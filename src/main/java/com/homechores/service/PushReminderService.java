package com.homechores.service;

import com.homechores.domain.CompletionRepository;
import com.homechores.domain.Member;
import com.homechores.domain.MemberRepository;
import com.homechores.domain.PushSubscription;
import com.homechores.domain.PushSubscriptionRepository;
import com.homechores.i18n.Translations;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
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
 * The opt-in "you haven't logged a chore today" reminder: each member may pick a
 * wall-clock time (their own timezone), and a Web Push notification goes to their
 * subscribed devices when that time passes with nothing submitted that day.
 *
 * <p>Runs on the shared single-thread scheduler, so the sweep must stay cheap and
 * non-blocking: one derived query for the working set (members with a reminder at all),
 * an exists-check per due member, and a bounded number of blocking HTTP sends per sweep.
 *
 * <p>The trigger rule is deliberately "&ge; time, once per local day" rather than
 * "== minute": {@code lastRemindedOn} makes it idempotent across restarts, missed
 * minutes and DST gaps (a reminder set for a nonexistent 02:30 simply fires at 03:00).
 */
@Service
public class PushReminderService {

    private static final Logger log = LoggerFactory.getLogger(PushReminderService.class);

    /** Upper bound on blocking HTTP sends per sweep, protecting the 1-thread scheduler.
     *  The rest are picked up next minute — a reminder a minute late is still a reminder. */
    static final int MAX_SENDS_PER_SWEEP = 20;

    private final MemberRepository members;
    private final CompletionRepository completions;
    private final PushSubscriptionRepository subscriptions;
    private final WebPushSender sender;
    private final Translations translations;

    /** Short, explicit transactions around the sweep's reads and writes — the sends must
     *  sit between them, holding nothing (see {@link #sendDueReminders}). */
    private final TransactionTemplate transactions;

    public PushReminderService(MemberRepository members, CompletionRepository completions,
                              PushSubscriptionRepository subscriptions, WebPushSender sender,
                              Translations translations,
                              org.springframework.transaction.PlatformTransactionManager txManager) {
        this.members = members;
        this.completions = completions;
        this.subscriptions = subscriptions;
        this.sender = sender;
        this.translations = translations;
        this.transactions = new TransactionTemplate(txManager);
    }

    @Scheduled(fixedDelayString = "${homechores.reminder.sweep-ms:60000}")
    public void scheduledSweep() {
        if (sender.isEnabled()) {
            sendDueReminders(Instant.now());
        }
    }

    /**
     * One sweep at the given moment; callable directly from tests. Returns sends made.
     *
     * <p><strong>No transaction spans the sends.</strong> A push is a blocking HTTP request
     * to a third party, and the connection pool is two connections wide
     * ({@code spring.datasource.hikari.maximum-pool-size}, sized for a family-scale board).
     * Holding a connection across up to {@link #MAX_SENDS_PER_SWEEP} such requests would
     * let one unresponsive push service — or one endpoint chosen to hang — take half the
     * pool out for as long as its timeouts last, which is the whole application, not just
     * reminders. So the work is split: a short read transaction decides about one member, the
     * sends happen with nothing held, and a short write transaction records the outcome.
     *
     * <p>Deciding per member rather than up front keeps the "did they log anything today"
     * check as late as it was when the whole sweep ran inside one transaction: a chore
     * tapped while the sweep is working its way down the list still suppresses that
     * member's reminder.
     */
    public int sendDueReminders(Instant now) {
        int sent = 0;
        for (Long memberId : candidateMemberIds()) {
            Plan plan = planFor(memberId, now);
            if (plan == null) {
                continue; // not due, already considered today, or unparseable
            }
            List<Long> dead = new ArrayList<>();
            if (plan.send()) {
                for (PushSubscription sub : plan.subscriptions()) {
                    if (sender.send(sub, plan.title(), plan.body())) {
                        sent++;
                    } else {
                        dead.add(sub.getId()); // gone at the push service — prune below
                    }
                }
            }
            // Stamped even when nothing was sent (already submitted, or no devices):
            // "considered today" is what stops the sweep re-evaluating every minute.
            recordSweepOutcome(memberId, plan.localDate(), dead);
            if (sent >= MAX_SENDS_PER_SWEEP) {
                break;
            }
        }
        return sent;
    }

    /** What this sweep should do about one member. {@code send} false = stamp only. */
    private record Plan(boolean send, LocalDate localDate, String title, String body,
                        List<PushSubscription> subscriptions) {
    }

    /** The working set: everyone who has a reminder configured at all. */
    private List<Long> candidateMemberIds() {
        return transactions.execute(status ->
                members.findByReminderTimeNotNull().stream().map(Member::getId).toList());
    }

    /**
     * Decides about one member inside its own short transaction, returning null when this
     * sweep should leave them alone. The subscriptions travel with the plan as detached
     * rows — {@link WebPushSender} only reads their endpoint and keys.
     */
    private Plan planFor(Long memberId, Instant now) {
        return transactions.execute(status -> {
            Member m = members.findById(memberId).orElse(null);
            if (m == null) {
                return null; // removed since the working set was read
            }
            LocalTime due = parseTime(m.getReminderTime());
            if (due == null) {
                return null; // unparseable — leave the row alone rather than guessing
            }
            ZoneId zone = zoneOf(m);
            ZonedDateTime local = now.atZone(zone);
            if (local.toLocalTime().isBefore(due)
                    || local.toLocalDate().equals(m.getLastRemindedOn())) {
                return null;
            }
            Instant midnight = local.toLocalDate().atStartOfDay(zone).toInstant();
            boolean send = !completions.existsByMemberIdAndDoneAtAfter(m.getId(), midnight);
            if (!send) {
                return new Plan(false, local.toLocalDate(), null, null, List.of());
            }
            Locale locale = localeOf(m);
            return new Plan(true, local.toLocalDate(),
                    translations.getTranslation("reminder.notification.title", locale),
                    translations.getTranslation("reminder.notification.body", locale, m.getName()),
                    List.copyOf(subscriptions.findByMemberId(m.getId())));
        });
    }

    /**
     * Records that this member has been considered today, and prunes the subscriptions the
     * push service reported gone. Re-reads the member and touches one field, rather than
     * merging the copy read before the sends — otherwise a rename or a timezone update
     * made while the sweep was blocked on HTTP would be written back out of existence.
     */
    private void recordSweepOutcome(Long memberId, LocalDate consideredOn, List<Long> dead) {
        transactions.executeWithoutResult(status -> {
            members.findById(memberId).ifPresent(m -> {
                m.setLastRemindedOn(consideredOn);
                members.save(m);
            });
            dead.forEach(subscriptions::deleteById);
        });
    }

    // ---- Settings, called from the reminder dialog / board -------------------

    /**
     * Saves (or, with a null time, clears) a member's reminder. When the chosen time is
     * already past today in the member's zone, today is pre-stamped so the very next
     * sweep doesn't fire a reminder for a day the member has effectively opted out of.
     */
    @Transactional
    public void saveReminder(Long memberId, LocalTime time, Locale locale) {
        Member m = members.findById(memberId).orElseThrow();
        if (time == null) {
            m.setReminderTime(null);
        } else {
            m.setReminderTime(time.toString().substring(0, 5));
            m.setReminderLocale(locale == null ? "en" : locale.getLanguage());
            ZonedDateTime local = Instant.now().atZone(zoneOf(m));
            if (!local.toLocalTime().isBefore(time)) {
                m.setLastRemindedOn(local.toLocalDate());
            }
        }
        members.save(m);
    }

    /** The member's saved reminder time, if reminders are on. */
    public Optional<LocalTime> reminderTime(Long memberId) {
        return members.findById(memberId)
                .map(Member::getReminderTime)
                .map(PushReminderService::parseTime);
    }

    /** Persists the member's browser timezone (no-op when unchanged). */
    @Transactional
    public void updateZone(Long memberId, String zoneId) {
        if (zoneId == null || zoneId.isBlank()) {
            return;
        }
        try {
            ZoneId.of(zoneId);
        } catch (Exception e) {
            return; // not a real zone — keep whatever we have
        }
        members.findById(memberId).ifPresent(m -> {
            if (!zoneId.equals(m.getZoneId())) {
                m.setZoneId(zoneId);
                members.save(m);
            }
        });
    }

    /**
     * Upserts a device subscription (keyed by its unique endpoint).
     *
     * <p>The endpoint arrives from the browser and is the one URL this server is later
     * told to make requests to, so it is checked before it is ever stored — see
     * {@link PushEndpoints} for why that matters and what the check is. A value that
     * fails is dropped silently: there is nothing the member could do about it, and the
     * only realistic way to produce one is to have tampered with the subscribe script.
     */
    @Transactional
    public void storeSubscription(Long memberId, String homeCode, String endpoint,
                                  String p256dh, String auth) {
        if (!PushEndpoints.isWellFormed(endpoint) || p256dh == null || auth == null) {
            return;
        }
        PushSubscription sub = subscriptions.findByEndpoint(endpoint)
                .orElseGet(() -> new PushSubscription(memberId, homeCode, endpoint,
                        p256dh, auth));
        sub.setP256dh(p256dh);
        sub.setAuth(auth);
        subscriptions.save(sub);
        log.debug("Stored push subscription for member {}", memberId);
    }

    @Transactional
    public void removeSubscription(String endpoint) {
        if (endpoint != null && !endpoint.isBlank()) {
            subscriptions.deleteByEndpoint(endpoint);
        }
    }

    // ---- Helpers --------------------------------------------------------------

    private static LocalTime parseTime(String hhmm) {
        try {
            return LocalTime.parse(hhmm);
        } catch (DateTimeParseException | NullPointerException e) {
            return null;
        }
    }

    private static ZoneId zoneOf(Member m) {
        if (m.getZoneId() != null) {
            try {
                return ZoneId.of(m.getZoneId());
            } catch (Exception ignored) {
                // fall through to the server zone
            }
        }
        return ZoneId.systemDefault();
    }

    private static Locale localeOf(Member m) {
        return m.getReminderLocale() == null ? Locale.ENGLISH
                : Locale.forLanguageTag(m.getReminderLocale());
    }
}
