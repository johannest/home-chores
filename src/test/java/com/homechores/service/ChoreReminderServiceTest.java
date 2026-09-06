package com.homechores.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.homechores.domain.ChoreReminderRepository;
import com.homechores.domain.ChoreTask;
import com.homechores.domain.Member;
import com.homechores.domain.MemberRepository;
import com.homechores.domain.PushSubscription;
import com.homechores.domain.PushSubscriptionRepository;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one-shot "remind me about this chore later" nudge. The sender is mocked — these tests cover
 * the trigger rule (fires once, then the row is gone), every path that must cancel a pending
 * reminder, and the retirement rule that stops an undeliverable row being reconsidered forever.
 *
 * <p>Modelled on {@link PushReminderServiceTest}, which does the same job for the daily reminder.
 */
@SpringBootTest
@Transactional
class ChoreReminderServiceTest {

    @Autowired ChoreService chores;
    @Autowired ChoreReminderService snoozes;
    @Autowired BackupService backup;
    @Autowired ChoreReminderRepository reminders;
    @Autowired MemberRepository members;
    @Autowired PushSubscriptionRepository subscriptions;

    @MockitoBean WebPushSender sender;

    private Member subscribedMember(String homeName, String name) {
        Member m = chores.createHome(homeName, name);
        subscriptions.save(new PushSubscription(m.getId(), m.getHomeCode(),
                "https://fcm.googleapis.com/fcm/send/" + m.getId(), "p256dh", "auth"));
        return m;
    }

    private ChoreTask anytimeChore(String code) {
        // The seeded board mixes anytime and interval chores; take one that is always due so the
        // sweep's "is this still worth sending" check is not what is under test.
        return chores.tasksOf(code).stream()
                .filter(t -> t.getIntervalDays() == 0)
                .findFirst().orElseThrow();
    }

    private void armed(Long memberId, Long taskId, Duration ago) {
        snoozes.schedule(memberId, taskId, Duration.ofHours(2), Locale.ENGLISH);
        var r = reminders.findByMemberIdAndTaskId(memberId, taskId).orElseThrow();
        r.setDueAt(Instant.now().minus(ago));
        reminders.save(r);
    }

    @Test
    void armedSnooze_firesOnceAfterItsTime_andIsThenGone() {
        when(sender.send(any(), anyString(), anyString())).thenReturn(true);
        Member alex = subscribedMember("Snooze", "Alex");
        ChoreTask task = anytimeChore(alex.getHomeCode());
        armed(alex.getId(), task.getId(), Duration.ofMinutes(1));

        assertEquals(1, snoozes.sendDue(Instant.now()));
        assertTrue(reminders.findByMemberIdAndTaskId(alex.getId(), task.getId()).isEmpty(),
                "a one-shot has no life after it fires");
        assertEquals(0, snoozes.sendDue(Instant.now()), "and it never fires twice");
    }

    @Test
    void beforeItsTime_nothingIsSentAndTheRowStays() {
        Member alex = subscribedMember("Snooze", "Alex");
        ChoreTask task = anytimeChore(alex.getHomeCode());
        snoozes.schedule(alex.getId(), task.getId(), Duration.ofHours(2), Locale.ENGLISH);

        assertEquals(0, snoozes.sendDue(Instant.now()));
        verify(sender, never()).send(any(), anyString(), anyString());
        assertTrue(reminders.findByMemberIdAndTaskId(alex.getId(), task.getId()).isPresent());
    }

    @Test
    void armingTheSameChoreTwice_movesTheReminderRatherThanAddingOne() {
        Member alex = subscribedMember("Snooze", "Alex");
        ChoreTask task = anytimeChore(alex.getHomeCode());

        Instant first = snoozes.schedule(alex.getId(), task.getId(), Duration.ofHours(1), null);
        Instant second = snoozes.schedule(alex.getId(), task.getId(), Duration.ofDays(1), null);

        assertEquals(1, snoozes.forMember(alex.getId()).size(), "one ⏰ on the card, one row");
        assertTrue(second.isAfter(first), "the later choice wins");
        assertEquals(second, reminders.findByMemberIdAndTaskId(alex.getId(), task.getId())
                .orElseThrow().getDueAt());
    }

    @Test
    void completingTheChore_cancelsTheMembersOwnSnooze_butNotAnotherMembers() {
        Member alex = subscribedMember("Snooze", "Alex");
        String code = alex.getHomeCode();
        Member sam = chores.joinHome(code, "Sam").orElseThrow();
        ChoreTask task = anytimeChore(code);
        snoozes.schedule(alex.getId(), task.getId(), Duration.ofHours(2), null);
        snoozes.schedule(sam.getId(), task.getId(), Duration.ofHours(2), null);

        chores.complete(task.getId(), alex.getId());

        assertTrue(reminders.findByMemberIdAndTaskId(alex.getId(), task.getId()).isEmpty(),
                "doing it settles it");
        assertTrue(reminders.findByMemberIdAndTaskId(sam.getId(), task.getId()).isPresent(),
                "a reminder belongs to the member who armed it");
    }

    @Test
    void anAdminLoggingItForSomeone_cancelsThatMembersSnooze() {
        Member alex = subscribedMember("Snooze", "Alex");
        String code = alex.getHomeCode();
        Member sam = chores.joinHome(code, "Sam").orElseThrow();
        ChoreTask task = anytimeChore(code);
        snoozes.schedule(sam.getId(), task.getId(), Duration.ofHours(2), null);

        chores.completeFor(task.getId(), sam.getId(), alex.getId());

        assertTrue(reminders.findByMemberIdAndTaskId(sam.getId(), task.getId()).isEmpty());
    }

    /**
     * An interval chore somebody else already did. Retired without sending, so completing a chore
     * never has to fan out a write across every other member's reminders.
     */
    @Test
    void aChoreSomeoneElseDid_isRetiredWithoutSending() {
        when(sender.send(any(), anyString(), anyString())).thenReturn(true);
        Member alex = subscribedMember("Snooze", "Alex");
        String code = alex.getHomeCode();
        Member sam = chores.joinHome(code, "Sam").orElseThrow();
        ChoreTask interval = chores.tasksOf(code).stream()
                .filter(t -> t.getIntervalDays() > 0).findFirst().orElseThrow();
        armed(alex.getId(), interval.getId(), Duration.ofMinutes(1));

        chores.complete(interval.getId(), sam.getId());

        assertEquals(0, snoozes.sendDue(Instant.now()), "the chore is not due — nothing useful to say");
        verify(sender, never()).send(any(), anyString(), anyString());
        assertTrue(reminders.findByMemberIdAndTaskId(alex.getId(), interval.getId()).isEmpty(),
                "and the row is retired rather than reconsidered every minute forever");
    }

    @Test
    void deletingTheChore_theMember_orTheHome_removesTheReminders() {
        Member alex = subscribedMember("Snooze", "Alex");
        String code = alex.getHomeCode();
        Member sam = chores.joinHome(code, "Sam").orElseThrow();
        List<ChoreTask> tasks = chores.tasksOf(code);
        snoozes.schedule(alex.getId(), tasks.get(0).getId(), Duration.ofHours(2), null);
        snoozes.schedule(sam.getId(), tasks.get(1).getId(), Duration.ofHours(2), null);

        chores.deleteTask(tasks.get(0).getId());
        assertTrue(snoozes.forMember(alex.getId()).isEmpty(), "the chore is gone");

        chores.removeMember(sam.getId());
        assertTrue(snoozes.forMember(sam.getId()).isEmpty(), "the member is gone");

        snoozes.schedule(alex.getId(), tasks.get(2).getId(), Duration.ofHours(2), null);
        chores.deleteHome(code);
        assertTrue(snoozes.forMember(alex.getId()).isEmpty(), "the home is gone");
    }

    @Test
    void restoringABackup_dropsPendingReminders() {
        Member alex = subscribedMember("Snooze", "Alex");
        String code = alex.getHomeCode();
        ChoreTask task = anytimeChore(code);
        snoozes.schedule(alex.getId(), task.getId(), Duration.ofHours(2), null);
        String json = backup.export(code);

        assertFalse(json.contains("dueAt"), "reminders are not family history — never exported");
        backup.restore(json.getBytes(StandardCharsets.UTF_8), code);

        // Scoped to this member, not findAll(): SnoozeUiTest is not transactional and leaves its
        // own rows behind, and this assertion is about THIS home's restore.
        assertTrue(snoozes.forMember(alex.getId()).isEmpty(),
                "restore remaps every member and task id, so a survivor would nudge the wrong "
                        + "person about the wrong chore");
    }

    @Test
    void aGoneSubscription_isPruned_andTheReminderIsStillRetired() {
        when(sender.send(any(), anyString(), anyString())).thenReturn(false); // 404/410
        Member alex = subscribedMember("Snooze", "Alex");
        ChoreTask task = anytimeChore(alex.getHomeCode());
        armed(alex.getId(), task.getId(), Duration.ofMinutes(1));

        assertEquals(0, snoozes.sendDue(Instant.now()));
        assertTrue(subscriptions.findByMemberId(alex.getId()).isEmpty(), "dead endpoint pruned");
        assertTrue(reminders.findByMemberIdAndTaskId(alex.getId(), task.getId()).isEmpty(),
                "retired regardless — an undeliverable row must not be retried forever");
    }

    @Test
    void aMemberWithNoDevices_stillHasTheReminderRetired() {
        Member alex = chores.createHome("Snooze", "Alex"); // no subscription at all
        ChoreTask task = anytimeChore(alex.getHomeCode());
        armed(alex.getId(), task.getId(), Duration.ofMinutes(1));

        assertEquals(0, snoozes.sendDue(Instant.now()));
        assertTrue(reminders.findByMemberIdAndTaskId(alex.getId(), task.getId()).isEmpty());
    }

    @Test
    void theSweepIsCapped_andTheRestFireNextTime() {
        when(sender.send(any(), anyString(), anyString())).thenReturn(true);
        Member alex = subscribedMember("Snooze", "Alex");
        String code = alex.getHomeCode();
        // Enough chores to exceed the cap, added rather than assumed: the seeded board's mix of
        // anytime and interval chores is not this test's business.
        for (int i = 0; i < ChoreReminderService.MAX_SENDS_PER_SWEEP + 2; i++) {
            ChoreTask t = chores.addTask(code, "Extra " + i, "✅");
            armed(alex.getId(), t.getId(), Duration.ofMinutes(1));
        }

        int first = snoozes.sendDue(Instant.now());
        assertEquals(ChoreReminderService.MAX_SENDS_PER_SWEEP, first, "capped");
        assertTrue(snoozes.sendDue(Instant.now()) > 0, "and the rest go out next minute");
    }

    @Test
    void anAbsurdOffsetIsClamped() {
        Member alex = subscribedMember("Snooze", "Alex");
        ChoreTask task = anytimeChore(alex.getHomeCode());

        Instant far = snoozes.schedule(alex.getId(), task.getId(), Duration.ofDays(9999), null);
        assertTrue(far.isBefore(Instant.now().plus(ChoreReminderService.MAX_OFFSET).plusSeconds(5)));

        Instant near = snoozes.schedule(alex.getId(), task.getId(), Duration.ZERO, null);
        assertTrue(near.isAfter(Instant.now()), "and never lands in the past");
    }

    /**
     * The sweep has no session to ask what language somebody reads, and {@code reminderLocale} is
     * otherwise written only by the daily reminder dialog — so a member who has only ever used a
     * snooze would get English on a Finnish phone.
     */
    @Test
    void theNotificationIsInTheMembersOwnLanguage_evenIfTheyNeverUsedTheDailyReminder() {
        when(sender.send(any(), anyString(), anyString())).thenReturn(true);
        Member alex = subscribedMember("Snooze", "Alex");
        ChoreTask task = anytimeChore(alex.getHomeCode());

        snoozes.schedule(alex.getId(), task.getId(), Duration.ofHours(2), Locale.of("fi"));
        assertEquals("fi", members.findById(alex.getId()).orElseThrow().getReminderLocale(),
                "arming stamps the language");

        var r = reminders.findByMemberIdAndTaskId(alex.getId(), task.getId()).orElseThrow();
        r.setDueAt(Instant.now().minusSeconds(60));
        reminders.save(r);
        snoozes.sendDue(Instant.now());

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(sender).send(any(), anyString(), body.capture());
        assertTrue(body.getValue().contains(task.getName()), "the notification names the chore");
        assertFalse(body.getValue().contains("you asked me"), "and is not the English string");
    }

    @Test
    void cancelling_removesTheRowWithoutSending() {
        Member alex = subscribedMember("Snooze", "Alex");
        ChoreTask task = anytimeChore(alex.getHomeCode());
        snoozes.schedule(alex.getId(), task.getId(), Duration.ofHours(2), null);

        snoozes.cancel(alex.getId(), task.getId());

        assertTrue(snoozes.forMember(alex.getId()).isEmpty());
        verify(sender, never()).send(any(), anyString(), anyString());
    }

    /**
     * The rule the group code already enforces ({@code aGroupFromAnotherHome_isRefused} in
     * {@code ChoreGroupTest}): a chore and a member from different homes never meet. The UI
     * only offers a member their own board, so this is the service holding the line rather than
     * every future caller having to remember to.
     */
    @Test
    void aChoreFromAnotherHome_isRefused() {
        Member alex = subscribedMember("Home A", "Alex");
        Member robin = subscribedMember("Home B", "Robin");
        ChoreTask alexsChore = anytimeChore(alex.getHomeCode());

        assertThrows(IllegalArgumentException.class, () -> snoozes.schedule(
                robin.getId(), alexsChore.getId(), Duration.ofHours(2), Locale.ENGLISH));

        assertTrue(reminders.findByMemberIdAndTaskId(robin.getId(), alexsChore.getId()).isEmpty(),
                "nothing was armed");
        assertTrue(snoozes.forMember(robin.getId()).isEmpty());
    }
}
