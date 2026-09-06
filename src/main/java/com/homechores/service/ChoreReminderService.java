package com.homechores.service;

import com.homechores.domain.ChoreReminder;
import com.homechores.domain.ChoreReminderRepository;
import com.homechores.domain.ChoreTask;
import com.homechores.domain.ChoreTaskRepository;
import com.homechores.domain.Member;
import com.homechores.domain.MemberRepository;
import com.homechores.domain.PushSubscription;
import com.homechores.domain.PushSubscriptionRepository;
import com.homechores.i18n.Translations;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * One-shot chore reminders: "remind me about the dishwasher in two hours". A member arms one from
 * a chore card, it fires a Web Push naming that chore, and it is then gone.
 *
 * <p>The sibling of {@link PushReminderService}, which owns the other kind of reminder — the
 * standing daily "you haven't logged anything today" nudge. They are independent by design:
 * different trigger rules, different rows, different notification text. What they share is the
 * transport ({@link WebPushSender}, {@link PushSubscription}) and, more importantly, the
 * transaction discipline documented there — <strong>no transaction may span a blocking push
 * send</strong>, because the connection pool is two connections wide and one unresponsive push
 * service would otherwise take half of it out for as long as its timeouts last. The three-phase
 * shape below (short read tx → sends holding nothing → short write tx) is a deliberate copy of
 * it, not a coincidence.
 *
 * <p>Two sweeps rather than one because their working sets could not be more different. The daily
 * sweep evaluates every member who has a reminder configured, every minute. This one asks for the
 * reminders that are already due — normally none at all, so it costs a single query returning an
 * empty list. That is what makes a second task on the shared single-thread scheduler affordable.
 */
@Service
public class ChoreReminderService {

    private static final Logger log = LoggerFactory.getLogger(ChoreReminderService.class);

    /** Blocking sends per sweep. Lower than the daily sweep's 20 because the two share one
     *  scheduler thread and their worst cases add up; a one-shot nudge is also naturally spread
     *  across the clock, so a family will never queue ten of them in one minute. */
    static final int MAX_SENDS_PER_SWEEP = 10;

    /** A nudge sooner than this is the member mistrusting the button; one further out than this
     *  is a row that sits in the table until the chore is deleted. The UI offers 1h–1w — this is
     *  the boundary check, not the menu. */
    static final Duration MIN_OFFSET = Duration.ofMinutes(1);
    static final Duration MAX_OFFSET = Duration.ofDays(30);

    private final ChoreReminderRepository reminders;
    private final ChoreTaskRepository tasks;
    private final MemberRepository members;
    private final PushSubscriptionRepository subscriptions;
    private final WebPushSender sender;
    private final ChoreService chores;
    private final Translations translations;
    private final TransactionTemplate transactions;

    public ChoreReminderService(ChoreReminderRepository reminders, ChoreTaskRepository tasks,
                                MemberRepository members, PushSubscriptionRepository subscriptions,
                                WebPushSender sender, ChoreService chores,
                                Translations translations,
                                org.springframework.transaction.PlatformTransactionManager txManager) {
        this.reminders = reminders;
        this.tasks = tasks;
        this.members = members;
        this.subscriptions = subscriptions;
        this.sender = sender;
        this.chores = chores;
        this.translations = translations;
        this.transactions = new TransactionTemplate(txManager);
    }

    @Scheduled(fixedDelayString = "${homechores.snooze.sweep-ms:60000}",
            initialDelayString = "${homechores.snooze.sweep-ms:60000}")
    public void scheduledSweep() {
        if (sender.isEnabled()) {
            sendDue(Instant.now());
        }
    }

    /**
     * One sweep at the given moment; callable directly from tests. Returns sends made.
     *
     * <p>Every reminder it looks at is retired, whatever happened — sent, undeliverable, or no
     * longer worth sending. That mirrors {@code PushReminderService}'s "stamped even when nothing
     * was sent": a row that can never produce a useful notification must not be reconsidered every
     * sixty seconds forever. It also bounds the crash window to one duplicate nudge (if the
     * process dies between an HTTP send and a one-statement delete) rather than an infinite retry.
     */
    public int sendDue(Instant now) {
        int sent = 0;
        for (Long id : dueReminderIds(now)) {
            Plan plan = planFor(id);
            List<Long> dead = new ArrayList<>();
            if (plan != null) {
                for (PushSubscription sub : plan.subscriptions()) {
                    if (sender.send(sub, plan.title(), plan.body())) {
                        sent++;
                    } else {
                        dead.add(sub.getId()); // gone at the push service — pruned below
                    }
                }
            }
            retire(id, dead);
            if (sent >= MAX_SENDS_PER_SWEEP) {
                break;
            }
        }
        return sent;
    }

    /** What this sweep should send about one reminder. Null = retire it without sending. */
    private record Plan(String title, String body, List<PushSubscription> subscriptions) {
    }

    private List<Long> dueReminderIds(Instant now) {
        return transactions.execute(status ->
                reminders.findTop50ByDueAtLessThanEqualOrderByDueAtAsc(now).stream()
                        .map(ChoreReminder::getId).toList());
    }

    /**
     * Decides about one reminder inside its own short transaction. The subscriptions travel with
     * the plan as detached rows — {@link WebPushSender} only reads their endpoint and keys.
     */
    private Plan planFor(Long reminderId) {
        return transactions.execute(status -> {
            ChoreReminder r = reminders.findById(reminderId).orElse(null);
            if (r == null) {
                return null; // cancelled since the working set was read
            }
            ChoreTask task = tasks.findById(r.getTaskId()).orElse(null);
            Member member = members.findById(r.getMemberId()).orElse(null);
            if (task == null || member == null) {
                return null; // the chore or the person is gone
            }
            // Somebody else did it in the meantime. For an anytime chore isDue is always true, so
            // this changes nothing in the common case; for an interval chore it is the difference
            // between a useful nudge and a notification telling a family to empty an empty
            // dishwasher. Deciding it here, on the few reminders actually firing, is why
            // completing a chore needs no fan-out write across every member's reminders.
            if (!chores.isDue(task)) {
                return null;
            }
            Locale locale = localeOf(member);
            String what = task.getEmoji() + " " + task.getName();
            return new Plan(
                    translations.getTranslation("snooze.notification.title", locale),
                    translations.getTranslation("snooze.notification.body", locale, what),
                    List.copyOf(subscriptions.findByMemberId(member.getId())));
        });
    }

    /** Deletes the fired reminder and prunes the subscriptions the push service reported gone. */
    private void retire(Long reminderId, List<Long> dead) {
        transactions.executeWithoutResult(status -> {
            reminders.deleteById(reminderId);
            dead.forEach(subscriptions::deleteById);
        });
    }

    // ---- Arming and cancelling, called from the board -------------------------

    /**
     * Arms (or moves) this member's nudge about one chore, returning when it will fire.
     *
     * <p>The language is stamped on the member here. The sweep runs with no session to ask what
     * language somebody reads, and {@code Member.reminderLocale} is otherwise written only by the
     * daily reminder dialog — so a member who has only ever used a snooze would get an English
     * notification on a Finnish phone.
     */
    @Transactional
    public Instant schedule(Long memberId, Long taskId, Duration offset, Locale locale) {
        ChoreTask task = tasks.findById(taskId).orElseThrow();
        Member member = members.findById(memberId).orElseThrow();
        // A chore and a member from different homes never meet — the rule the group code
        // already enforces (ChoreService.applyGroup) and completeFor states the same way.
        // The UI only ever offers a member their own board, so this is the service holding
        // the line rather than trusting that every future caller will.
        if (!task.getHomeCode().equals(member.getHomeCode())) {
            throw new IllegalArgumentException("Chore and member belong to different homes");
        }
        Duration clamped = offset.compareTo(MIN_OFFSET) < 0 ? MIN_OFFSET
                : offset.compareTo(MAX_OFFSET) > 0 ? MAX_OFFSET : offset;
        Instant dueAt = Instant.now().plus(clamped);

        ChoreReminder r = reminders.findByMemberIdAndTaskId(memberId, taskId)
                .orElseGet(() -> new ChoreReminder(memberId, task.getHomeCode(), taskId, dueAt));
        r.setDueAt(dueAt);
        reminders.save(r);

        if (locale != null) {
            member.setReminderLocale(locale.getLanguage());
            members.save(member);
        }
        log.debug("Chore reminder for member {} on task {} at {}", memberId, taskId, dueAt);
        return dueAt;
    }

    @Transactional
    public void cancel(Long memberId, Long taskId) {
        reminders.deleteByMemberIdAndTaskId(memberId, taskId);
    }

    /** Drops every pending nudge for a member — used when they turn notifications off, which
     *  destroys the browser subscription these would have been delivered to. */
    @Transactional
    public void cancelAll(Long memberId) {
        reminders.deleteByMemberId(memberId);
    }

    /** Everything this member has armed, for badging the board. One query, never one per card. */
    public List<ChoreReminder> forMember(Long memberId) {
        return reminders.findByMemberId(memberId);
    }

    public Optional<ChoreReminder> forMemberAndTask(Long memberId, Long taskId) {
        return reminders.findByMemberIdAndTaskId(memberId, taskId);
    }

    private static Locale localeOf(Member m) {
        return m.getReminderLocale() == null ? Locale.ENGLISH
                : Locale.forLanguageTag(m.getReminderLocale());
    }
}
