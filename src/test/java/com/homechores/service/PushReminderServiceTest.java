package com.homechores.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.homechores.domain.Member;
import com.homechores.domain.MemberRepository;
import com.homechores.domain.PushSubscriptionRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * The daily "no chores logged yet" reminder sweep. The sender is mocked — these tests
 * cover the trigger rule (member-local time, once per local day, suppressed by any
 * submission), the idempotency stamp, and pruning of dead subscriptions.
 */
@SpringBootTest
@Transactional
class PushReminderServiceTest {

    @Autowired ChoreService service;
    @Autowired PushReminderService reminders;
    @Autowired MemberRepository members;
    @Autowired PushSubscriptionRepository subscriptions;

    @MockitoBean WebPushSender sender;

    private static final ZoneId HELSINKI = ZoneId.of("Europe/Helsinki");

    /** A member with a reminder at 19:00 Helsinki time and one subscribed device. */
    private Member reminderMember() {
        Member m = service.createHome("Reminders", "Alex");
        m = members.findById(m.getId()).orElseThrow();
        m.setZoneId(HELSINKI.getId());
        m.setReminderTime("19:00");
        m.setReminderLocale("en");
        members.save(m);
        reminders.storeSubscription(m.getId(), m.getHomeCode(),
                "https://push.example/" + m.getId(), "p256dh-key", "auth-secret");
        return m;
    }

    /** An instant that is the given local time today in the member's zone. */
    private static Instant at(LocalTime time) {
        return ZonedDateTime.of(LocalDate.now(HELSINKI), time, HELSINKI).toInstant();
    }

    @Test
    void dueMemberWithNoSubmissionToday_getsOneReminderAndIsStamped() {
        when(sender.send(any(), anyString(), anyString())).thenReturn(true);
        Member m = reminderMember();

        int sent = reminders.sendDueReminders(at(LocalTime.of(19, 5)));

        assertEquals(1, sent);
        verify(sender, times(1)).send(any(), anyString(), anyString());
        assertEquals(LocalDate.now(HELSINKI),
                members.findById(m.getId()).orElseThrow().getLastRemindedOn());
    }

    @Test
    void beforeTheReminderTime_nothingHappens() {
        Member m = reminderMember();

        assertEquals(0, reminders.sendDueReminders(at(LocalTime.of(18, 55))));

        verify(sender, never()).send(any(), anyString(), anyString());
        assertNull(members.findById(m.getId()).orElseThrow().getLastRemindedOn());
    }

    @Test
    void secondSweepTheSameLocalDay_doesNotResend() {
        when(sender.send(any(), anyString(), anyString())).thenReturn(true);
        reminderMember();

        assertEquals(1, reminders.sendDueReminders(at(LocalTime.of(19, 5))));
        assertEquals(0, reminders.sendDueReminders(at(LocalTime.of(19, 6))));
        assertEquals(0, reminders.sendDueReminders(at(LocalTime.of(23, 59))));

        verify(sender, times(1)).send(any(), anyString(), anyString());
    }

    @Test
    void anySubmissionToday_suppressesTheReminder_butStillStamps() {
        Member m = reminderMember();
        // A completion right now counts whatever its status — PENDING included.
        service.complete(service.tasksOf(m.getHomeCode()).get(0).getId(), m.getId());

        assertEquals(0, reminders.sendDueReminders(at(LocalTime.of(19, 5))));

        verify(sender, never()).send(any(), anyString(), anyString());
        assertEquals(LocalDate.now(HELSINKI),
                members.findById(m.getId()).orElseThrow().getLastRemindedOn(),
                "stamped so the sweep stops re-evaluating this member today");
    }

    @Test
    void memberLocalMidnight_isRespected_notTheServerZone() {
        when(sender.send(any(), anyString(), anyString())).thenReturn(true);
        Member m = reminderMember();
        m = members.findById(m.getId()).orElseThrow();
        m.setZoneId("Pacific/Auckland");
        members.save(m);

        // 19:05 in Auckland — due there regardless of what the server clock says.
        Instant aucklandEvening = ZonedDateTime.of(LocalDate.now(ZoneId.of("Pacific/Auckland")),
                LocalTime.of(19, 5), ZoneId.of("Pacific/Auckland")).toInstant();
        assertEquals(1, reminders.sendDueReminders(aucklandEvening));
        assertEquals(LocalDate.now(ZoneId.of("Pacific/Auckland")),
                members.findById(m.getId()).orElseThrow().getLastRemindedOn());
    }

    @Test
    void goneSubscription_isPruned() {
        when(sender.send(any(), anyString(), anyString())).thenReturn(false); // 404/410
        Member m = reminderMember();

        assertEquals(0, reminders.sendDueReminders(at(LocalTime.of(19, 5))));

        assertTrue(subscriptions.findByMemberId(m.getId()).isEmpty(),
                "a subscription the push service reports gone is deleted");
    }

    @Test
    void savingATimeAlreadyPastToday_preStampsSoNothingFiresImmediately() {
        Member m = reminderMember();
        ZonedDateTime now = Instant.now().atZone(HELSINKI);
        LocalTime past = now.toLocalTime().minusHours(1).truncatedTo(java.time.temporal.ChronoUnit.MINUTES);

        reminders.saveReminder(m.getId(), past, Locale.ENGLISH);

        Member saved = members.findById(m.getId()).orElseThrow();
        assertEquals(now.toLocalDate(), saved.getLastRemindedOn(),
                "opting in after the chosen time must not fire a reminder for today");
        assertEquals(0, reminders.sendDueReminders(Instant.now()));
        verify(sender, never()).send(any(), anyString(), anyString());
    }

    @Test
    void clearingTheReminder_turnsTheMemberOff() {
        Member m = reminderMember();
        reminders.saveReminder(m.getId(), null, null);

        assertNull(members.findById(m.getId()).orElseThrow().getReminderTime());
        assertEquals(0, reminders.sendDueReminders(at(LocalTime.of(19, 5))));
    }

    @Test
    void deletingTheHome_removesItsSubscriptions() {
        Member m = reminderMember();
        assertFalse(subscriptions.findByMemberId(m.getId()).isEmpty());

        service.deleteHome(m.getHomeCode());

        assertTrue(subscriptions.findByMemberId(m.getId()).isEmpty());
    }

    @Test
    void removingAMember_removesTheirSubscriptions() {
        Member admin = reminderMember();
        Member other = service.joinHome(admin.getHomeCode(), "Sam").orElseThrow();
        reminders.storeSubscription(other.getId(), other.getHomeCode(),
                "https://push.example/other", "k", "a");

        assertTrue(service.removeMember(other.getId()));

        assertTrue(subscriptions.findByMemberId(other.getId()).isEmpty());
        assertFalse(subscriptions.findByMemberId(admin.getId()).isEmpty(),
                "the admin's own subscription is untouched");
    }

    @Test
    void invalidZoneOrGarbageInput_isIgnoredSafely() {
        Member m = reminderMember();

        reminders.updateZone(m.getId(), "Not/AZone");
        assertEquals(HELSINKI.getId(),
                members.findById(m.getId()).orElseThrow().getZoneId());

        reminders.updateZone(m.getId(), "Pacific/Auckland");
        assertEquals("Pacific/Auckland",
                members.findById(m.getId()).orElseThrow().getZoneId());

        // A subscription with missing keys or an oversized endpoint is refused quietly.
        reminders.storeSubscription(m.getId(), m.getHomeCode(), "x".repeat(2000), "k", "a");
        reminders.storeSubscription(m.getId(), m.getHomeCode(), "https://ok", null, "a");
        assertEquals(1, subscriptions.findByMemberId(m.getId()).size());
    }

    @Test
    void newMembers_carryATermsAcceptanceStamp() {
        Member m = service.createHome("Consent", "Alex");
        assertNotNull(members.findById(m.getId()).orElseThrow().getTermsAcceptedAt(),
                "member creation is checkbox-gated, so creation implies consent");
    }
}
