package com.homechores.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.homechores.domain.ListItem;
import com.homechores.domain.ListKind;
import com.homechores.domain.ListReminder;
import com.homechores.domain.ListReminderRepository;
import com.homechores.domain.Member;
import com.homechores.domain.MemberRepository;
import com.homechores.domain.PushSubscription;
import com.homechores.domain.PushSubscriptionRepository;
import com.homechores.service.ListReminderService.QuickOption;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * The home-wide "remind us about this line" nudge. The sender is mocked — these cover the trigger
 * rule (fires once to every device in the home, then waits for an answer rather than vanishing),
 * every path that must retire a reminder, the snooze round trip, and the quick-option clock math.
 *
 * <p>Modelled on {@link ChoreReminderServiceTest}, which does the same for the per-chore snooze.
 */
@SpringBootTest
@Transactional
class ListReminderServiceTest {

    @Autowired ChoreService chores;
    @Autowired ListItemService lists;
    @Autowired ListReminderService reminders;
    @Autowired BackupService backup;
    @Autowired ListReminderRepository repo;
    @Autowired MemberRepository members;
    @Autowired PushSubscriptionRepository subscriptions;

    @MockitoBean WebPushSender sender;

    private void subscribe(Member m) {
        subscriptions.save(new PushSubscription(m.getId(), m.getHomeCode(),
                "https://fcm.googleapis.com/fcm/send/" + m.getId(), "p256dh", "auth"));
    }

    private Member subscribedHome(String name) {
        Member m = chores.createHome("Listy", name);
        subscribe(m);
        return m;
    }

    private ListItem todo(String code, Long memberId, String text) {
        return lists.add(code, ListKind.TODO, memberId, text).orElseThrow();
    }

    private Instant inTwoHours() {
        return Instant.now().plus(Duration.ofHours(2));
    }

    /** Arms and then backdates, so the next sweep sees it as due. */
    private ListReminder armedAndDue(Long itemId, Long memberId) {
        reminders.schedule(itemId, memberId, inTwoHours(), Locale.ENGLISH);
        ListReminder r = repo.findByItemId(itemId).orElseThrow();
        r.setDueAt(Instant.now().minusSeconds(60));
        return repo.save(r);
    }

    @Test
    void firesOnceToEveryDeviceInTheHome_thenWaitsForAnAnswerInsteadOfVanishing() {
        when(sender.send(any(), anyString(), anyString())).thenReturn(true);
        Member alex = subscribedHome("Alex");
        String code = alex.getHomeCode();
        Member sam = chores.joinHome(code, "Sam").orElseThrow();
        subscribe(sam);
        Member stranger = subscribedHome("Robin"); // another home entirely
        ListItem plumber = todo(code, alex.getId(), "Call the plumber");
        armedAndDue(plumber.getId(), alex.getId());

        assertEquals(2, reminders.sendDue(Instant.now()), "one push per device in the home");
        verify(sender, times(2)).send(any(), anyString(), anyString());
        assertTrue(reminders.forHome(stranger.getHomeCode()).isEmpty(), "and none to the neighbours");

        ListReminder after = repo.findByItemId(plumber.getId()).orElseThrow();
        assertTrue(after.isFired(), "kept, marked fired — the board will offer a snooze");
        assertEquals(1, reminders.firedForHome(code).size());
        assertEquals(0, reminders.sendDue(Instant.now()), "and never sent twice");
    }

    @Test
    void beforeItsTime_nothingIsSentAndTheRowStays() {
        Member alex = subscribedHome("Alex");
        ListItem item = todo(alex.getHomeCode(), alex.getId(), "Milk");
        reminders.schedule(item.getId(), alex.getId(), inTwoHours(), Locale.ENGLISH);

        assertEquals(0, reminders.sendDue(Instant.now()));
        verify(sender, never()).send(any(), anyString(), anyString());
        assertFalse(repo.findByItemId(item.getId()).orElseThrow().isFired());
    }

    @Test
    void schedulingTwice_movesTheOneReminder_andAnyoneInTheHomeMayMoveIt() {
        Member alex = subscribedHome("Alex");
        String code = alex.getHomeCode();
        Member sam = chores.joinHome(code, "Sam").orElseThrow();
        ListItem item = todo(code, alex.getId(), "Milk");

        Instant first = reminders.schedule(item.getId(), alex.getId(), inTwoHours(), null);
        Instant second = reminders.schedule(item.getId(), sam.getId(),
                Instant.now().plus(Duration.ofDays(1)), null);

        assertEquals(1, reminders.forHome(code).size(), "one line, one reminder");
        assertTrue(second.isAfter(first), "the later choice wins");
        ListReminder r = repo.findByItemId(item.getId()).orElseThrow();
        assertEquals(second, r.getDueAt());
        assertEquals(sam.getId(), r.getSetByMemberId(), "and the badge names who moved it");
    }

    @Test
    void snoozingAFiredReminder_putsItBackToPending_withTheNewTime() {
        when(sender.send(any(), anyString(), anyString())).thenReturn(true);
        Member alex = subscribedHome("Alex");
        ListItem item = todo(alex.getHomeCode(), alex.getId(), "Milk");
        armedAndDue(item.getId(), alex.getId());
        reminders.sendDue(Instant.now());
        assertTrue(repo.findByItemId(item.getId()).orElseThrow().isFired());

        Instant later = reminders.schedule(item.getId(), alex.getId(),
                Instant.now().plus(Duration.ofDays(7)), Locale.ENGLISH);

        ListReminder r = repo.findByItemId(item.getId()).orElseThrow();
        assertNull(r.getFiredAt(), "pending again");
        assertEquals(later, r.getDueAt());
        assertTrue(reminders.firedForHome(alex.getHomeCode()).isEmpty(), "nothing left to answer");
        assertEquals(0, reminders.sendDue(Instant.now()), "and it does not fire before its new time");
    }

    @Test
    void tickingTheLineOff_byAnyone_orDeletingIt_removesTheReminder() {
        Member alex = subscribedHome("Alex");
        String code = alex.getHomeCode();
        Member sam = chores.joinHome(code, "Sam").orElseThrow();
        ListItem milk = todo(code, alex.getId(), "Milk");
        ListItem bread = todo(code, alex.getId(), "Bread");
        reminders.schedule(milk.getId(), alex.getId(), inTwoHours(), null);
        reminders.schedule(bread.getId(), alex.getId(), inTwoHours(), null);

        lists.setDone(milk.getId(), sam.getId(), true);
        assertTrue(repo.findByItemId(milk.getId()).isEmpty(), "doing it settles it, whoever does it");

        lists.setDone(milk.getId(), sam.getId(), false);
        assertTrue(repo.findByItemId(milk.getId()).isEmpty(), "unticking does not bring it back");

        lists.delete(bread.getId(), alex.getId());
        assertTrue(repo.findByItemId(bread.getId()).isEmpty(), "the line is gone");
    }

    @Test
    void aLineAlreadyDoneOrGoneAtFireTime_isRetiredWithoutSending() {
        when(sender.send(any(), anyString(), anyString())).thenReturn(true);
        Member alex = subscribedHome("Alex");
        ListItem item = todo(alex.getHomeCode(), alex.getId(), "Milk");
        ListReminder r = armedAndDue(item.getId(), alex.getId());
        // Tick it behind the service's back so the row is still there when the sweep looks.
        item.setDoneAt(Instant.now());
        lists.find(item.getId()); // no-op read; the entity is managed in this transaction

        assertEquals(0, reminders.sendDue(Instant.now()));
        verify(sender, never()).send(any(), anyString(), anyString());
        assertTrue(repo.findById(r.getId()).isEmpty(),
                "retired rather than reconsidered every minute forever");
    }

    @Test
    void aGoneSubscription_isPruned_andTheReminderStillFires() {
        when(sender.send(any(), anyString(), anyString())).thenReturn(false); // 404/410
        Member alex = subscribedHome("Alex");
        ListItem item = todo(alex.getHomeCode(), alex.getId(), "Milk");
        armedAndDue(item.getId(), alex.getId());

        assertEquals(0, reminders.sendDue(Instant.now()));
        assertTrue(subscriptions.findByMemberId(alex.getId()).isEmpty(), "dead endpoint pruned");
        assertTrue(repo.findByItemId(item.getId()).orElseThrow().isFired(),
                "the notification was attempted — the dialog still gets its turn");
    }

    @Test
    void aHomeWithNoDevices_hasTheReminderDroppedQuietly() {
        Member alex = chores.createHome("Listy", "Alex"); // nobody subscribed
        ListItem item = todo(alex.getHomeCode(), alex.getId(), "Milk");
        armedAndDue(item.getId(), alex.getId());

        assertEquals(0, reminders.sendDue(Instant.now()));
        assertTrue(repo.findByItemId(item.getId()).isEmpty(),
                "nothing was sent, so there is nothing to answer");
    }

    @Test
    void deletingTheHome_orRestoringABackup_dropsTheReminders_andTheExportNeverHasThem() {
        Member alex = subscribedHome("Alex");
        String code = alex.getHomeCode();
        ListItem item = todo(code, alex.getId(), "Milk");
        reminders.schedule(item.getId(), alex.getId(), inTwoHours(), null);

        String json = backup.export(code);
        assertFalse(json.contains("dueAt"), "a pending notification is not family history");
        backup.restore(json.getBytes(StandardCharsets.UTF_8), code);
        assertTrue(reminders.forHome(code).isEmpty(), "line ids were reissued — a survivor would point nowhere");

        ListItem again = todo(code, chores.membersOf(code).get(0).getId(), "Bread");
        reminders.schedule(again.getId(), chores.membersOf(code).get(0).getId(), inTwoHours(), null);
        chores.deleteHome(code);
        assertTrue(reminders.forHome(code).isEmpty(), "the home is gone");
    }

    @Test
    void removingAMember_leavesTheHomesReminderInPlace() {
        Member alex = subscribedHome("Alex");
        String code = alex.getHomeCode();
        Member sam = chores.joinHome(code, "Sam").orElseThrow();
        ListItem item = todo(code, sam.getId(), "Milk");
        reminders.schedule(item.getId(), sam.getId(), inTwoHours(), null);

        chores.removeMember(sam.getId());

        assertEquals(1, reminders.forHome(code).size(), "it belongs to the line, not to Sam");
    }

    @Test
    void aStrangerIsRefused_andCancelWorksForAnyoneInTheHome() {
        Member alex = subscribedHome("Alex");
        Member robin = subscribedHome("Robin");
        String code = alex.getHomeCode();
        Member sam = chores.joinHome(code, "Sam").orElseThrow();
        ListItem item = todo(code, alex.getId(), "Milk");

        assertThrows(IllegalArgumentException.class,
                () -> reminders.schedule(item.getId(), robin.getId(), inTwoHours(), null));
        assertTrue(repo.findByItemId(item.getId()).isEmpty(), "nothing was armed");

        reminders.schedule(item.getId(), alex.getId(), inTwoHours(), null);
        assertThrows(IllegalArgumentException.class, () -> reminders.cancel(item.getId(), robin.getId()));
        assertTrue(reminders.cancel(item.getId(), sam.getId()), "a housemate may");
        assertTrue(reminders.forHome(code).isEmpty());
        assertFalse(reminders.cancel(item.getId(), sam.getId()), "and twice is a no-op");
    }

    @Test
    void absurdTimesAreClamped() {
        Member alex = subscribedHome("Alex");
        ListItem item = todo(alex.getHomeCode(), alex.getId(), "Milk");

        Instant far = reminders.schedule(item.getId(), alex.getId(),
                Instant.now().plus(Duration.ofDays(9999)), null);
        assertTrue(far.isBefore(Instant.now().plus(ListReminderService.MAX_OFFSET).plusSeconds(5)));

        Instant past = reminders.schedule(item.getId(), alex.getId(),
                Instant.now().minus(Duration.ofDays(1)), null);
        assertTrue(past.isAfter(Instant.now()), "never lands in the past");

        Instant none = reminders.schedule(item.getId(), alex.getId(), null, null);
        assertTrue(none.isAfter(Instant.now()));
    }

    @Test
    void unansweredForAWeek_isPurged_butAFreshOneIsNot() {
        when(sender.send(any(), anyString(), anyString())).thenReturn(true);
        Member alex = subscribedHome("Alex");
        String code = alex.getHomeCode();
        ListItem old = todo(code, alex.getId(), "Old");
        ListItem fresh = todo(code, alex.getId(), "Fresh");
        armedAndDue(old.getId(), alex.getId());
        armedAndDue(fresh.getId(), alex.getId());
        reminders.sendDue(Instant.now());
        ListReminder stale = repo.findByItemId(old.getId()).orElseThrow();
        stale.setFiredAt(Instant.now().minus(ListReminderService.FIRED_RETENTION).minusSeconds(60));
        repo.save(stale);

        assertEquals(1, reminders.purgeStaleFired(Instant.now()));
        assertTrue(repo.findByItemId(old.getId()).isEmpty());
        assertTrue(repo.findByItemId(fresh.getId()).orElseThrow().isFired(), "still waiting for an answer");
    }

    /** A Finnish phone and an English tablet in one home each read the nudge in their own words. */
    @Test
    void eachDeviceIsAddressedInItsOwnMembersLanguage() {
        when(sender.send(any(), anyString(), anyString())).thenReturn(true);
        Member alex = subscribedHome("Alex");
        String code = alex.getHomeCode();
        Member sam = chores.joinHome(code, "Sam").orElseThrow();
        subscribe(sam);
        ListItem item = todo(code, alex.getId(), "Putkimies");
        // Sam arms it in Finnish: that stamps Sam's language; Alex has never said, so English.
        reminders.schedule(item.getId(), sam.getId(), inTwoHours(), Locale.of("fi"));
        assertEquals("fi", members.findById(sam.getId()).orElseThrow().getReminderLocale());
        ListReminder r = repo.findByItemId(item.getId()).orElseThrow();
        r.setDueAt(Instant.now().minusSeconds(60));
        repo.save(r);

        reminders.sendDue(Instant.now());

        ArgumentCaptor<String> bodies = ArgumentCaptor.forClass(String.class);
        verify(sender, times(2)).send(any(), anyString(), bodies.capture());
        List<String> sent = bodies.getAllValues();
        assertTrue(sent.stream().allMatch(b -> b.contains("Putkimies")), "both name the line");
        assertEquals(1, sent.stream().filter(b -> b.contains("asked to be reminded")).count(), "Alex, in English");
        assertEquals(1, sent.stream().filter(b -> b.contains("pyysi")).count(), "Sam, in Finnish");
    }

    // ---- The quick options' clock math ---------------------------------------

    private static final ZoneId HELSINKI = ZoneId.of("Europe/Helsinki");

    private static ZonedDateTime at(String isoLocal) {
        return LocalDateTime.parse(isoLocal).atZone(HELSINKI);
    }

    @Test
    void quickOptions_landOnNineInTheMorning_inTheMembersOwnZone() {
        ZonedDateTime lateEvening = at("2026-09-18T23:40:00");

        assertEquals(at("2026-09-19T00:40:00").toInstant(), QuickOption.IN_1H.at(lateEvening),
                "an hour is an hour, even across midnight");
        assertEquals(at("2026-09-19T09:00:00").toInstant(), QuickOption.TOMORROW.at(lateEvening),
                "tomorrow morning, not 23:40 tomorrow");
        assertEquals(at("2026-09-25T09:00:00").toInstant(), QuickOption.NEXT_WEEK.at(lateEvening));
        assertEquals(at("2026-10-18T09:00:00").toInstant(), QuickOption.NEXT_MONTH.at(lateEvening));
    }

    @Test
    void nextMonthFromTheThirtyFirst_isTheLastDayOfAShorterMonth() {
        assertEquals(at("2027-02-28T09:00:00").toInstant(),
                QuickOption.NEXT_MONTH.at(at("2027-01-31T10:00:00")));
    }

    /** Helsinki leaves summer time on 25 Oct 2026: "next week 9:00" is still 9:00 on the wall. */
    @Test
    void wallClockOptions_surviveADstChange() {
        ZonedDateTime beforeChange = at("2026-10-20T09:00:00");
        Instant nextWeek = QuickOption.NEXT_WEEK.at(beforeChange);
        assertEquals(LocalDateTime.parse("2026-10-27T09:00:00"),
                nextWeek.atZone(HELSINKI).toLocalDateTime());
        assertEquals(Duration.ofDays(7).plusHours(1), Duration.between(beforeChange.toInstant(), nextWeek),
                "which is an hour more than seven times 24 h");
    }
}
