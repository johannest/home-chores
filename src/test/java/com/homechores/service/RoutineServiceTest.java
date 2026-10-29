package com.homechores.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.homechores.domain.InputLimits;
import com.homechores.domain.ListKind;
import com.homechores.domain.Member;
import com.homechores.domain.PushSubscription;
import com.homechores.domain.PushSubscriptionRepository;
import com.homechores.domain.Routine;
import com.homechores.domain.RoutineDoneRepository;
import com.homechores.domain.RoutineRepository;
import com.homechores.service.RoutineService.Spec;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * Routines (§4.4f) through the service: who may make and change which, isolation between homes,
 * today's list, ticking, the home's clock, the reminder sweep, and what deletion and backup do.
 * The push sender is mocked, as in {@link ListReminderServiceTest}.
 */
@SpringBootTest
@Transactional
class RoutineServiceTest {

    @Autowired ChoreService chores;
    @Autowired RoutineService routines;
    @Autowired ListItemService lists;
    @Autowired SharedDeviceService devices;
    @Autowired BackupService backup;
    @Autowired RoutineRepository repo;
    @Autowired RoutineDoneRepository ticks;
    @Autowired PushSubscriptionRepository subscriptions;

    @MockitoBean WebPushSender sender;

    private static final Set<DayOfWeek> EVERY_DAY = EnumSet.allOf(DayOfWeek.class);

    private static Spec spec(String name, Long forMemberId) {
        return new Spec(name, "🏋️", EVERY_DAY, 1, false, null, forMemberId);
    }

    private static Spec timed(String name, Long forMemberId, LocalTime time) {
        return new Spec(name, "🏋️", EVERY_DAY, 1, false, time, forMemberId);
    }

    private void subscribe(Member m) {
        subscriptions.save(new PushSubscription(m.getId(), m.getHomeCode(),
                "https://fcm.googleapis.com/fcm/send/" + m.getId(), "p256dh", "auth"));
    }

    /** Backdates a routine's next reminder so the next sweep finds it due. */
    private void dueNow(Routine r) {
        Routine fresh = repo.findById(r.getId()).orElseThrow();
        fresh.setNextFireAt(Instant.now().minusSeconds(30));
        repo.save(fresh);
    }

    // ---- Who may do what ------------------------------------------------------

    @Test
    void aMember_makesTheirOwn_butOnlyAnAdminMakesOnesForTheHomeOrSomebodyElse() {
        Member alex = chores.createHome("Nest", "Alex");
        String code = alex.getHomeCode();
        Member sam = chores.joinHome(code, "Sam").orElseThrow();

        assertTrue(routines.create(code, sam.getId(), spec("Workout", sam.getId())).isPresent());
        assertThrows(IllegalArgumentException.class,
                () -> routines.create(code, sam.getId(), spec("Bins out", null)));
        assertThrows(IllegalArgumentException.class,
                () -> routines.create(code, sam.getId(), spec("Alex's run", alex.getId())));

        assertTrue(routines.create(code, alex.getId(), spec("Bins out", null)).isPresent());
        assertTrue(routines.create(code, alex.getId(), spec("Piano", sam.getId())).isPresent());
        assertEquals(3, routines.routinesOf(code).size());
    }

    @Test
    void onlyItsOwnerOrAnAdmin_changesOrDeletesARoutine() {
        Member alex = chores.createHome("Nest", "Alex");
        String code = alex.getHomeCode();
        Member sam = chores.joinHome(code, "Sam").orElseThrow();
        Member kim = chores.joinHome(code, "Kim").orElseThrow();
        Routine samsRun = routines.create(code, sam.getId(), spec("Run", sam.getId())).orElseThrow();
        Routine bins = routines.create(code, alex.getId(), spec("Bins", null)).orElseThrow();

        assertThrows(IllegalArgumentException.class,
                () -> routines.update(code, kim.getId(), samsRun.getId(), spec("Mine now", kim.getId())));
        assertThrows(IllegalArgumentException.class,
                () -> routines.delete(code, sam.getId(), bins.getId()));
        assertThrows(IllegalArgumentException.class, () -> routines.update(code, sam.getId(),
                samsRun.getId(), spec("Run", null)), "nor hand it to the home");

        assertEquals("Long run", routines.update(code, sam.getId(), samsRun.getId(),
                spec("Long run", sam.getId())).orElseThrow().getName());
        assertTrue(routines.delete(code, alex.getId(), samsRun.getId()), "an admin may");
        assertFalse(routines.delete(code, alex.getId(), samsRun.getId()), "already gone");
    }

    @Test
    void anotherHomesRoutine_isNeitherChangedDeletedNorTicked_andItsMemberCannotBeAssigned() {
        Member alex = chores.createHome("Nest", "Alex");
        Member kim = chores.createHome("Elsewhere", "Kim");
        String code = alex.getHomeCode();
        Routine theirs = routines.create(kim.getHomeCode(), kim.getId(), spec("Theirs", null)).orElseThrow();

        assertTrue(routines.update(code, alex.getId(), theirs.getId(), spec("Hijacked", null)).isEmpty());
        assertFalse(routines.delete(code, alex.getId(), theirs.getId()));
        assertFalse(routines.setDone(code, theirs.getId(), Author.member(alex.getId()), true));
        assertThrows(IllegalArgumentException.class, () -> routines.setDone(kim.getHomeCode(),
                theirs.getId(), Author.member(alex.getId()), true), "nor as a member of the other home");
        assertEquals("Theirs", repo.findById(theirs.getId()).orElseThrow().getName());
        assertTrue(ticks.findAll().isEmpty());

        assertTrue(routines.create(code, alex.getId(), spec("For Kim", kim.getId())).isEmpty(),
                "a member of another home is nobody to assign a routine to");
    }

    @Test
    void aBlankName_noDays_andAFullHome_makeNothing() {
        Member alex = chores.createHome("Nest", "Alex");
        String code = alex.getHomeCode();
        assertTrue(routines.create(code, alex.getId(), spec("  ", null)).isEmpty());
        assertTrue(routines.create(code, alex.getId(),
                new Spec("No days", null, Set.of(), 1, false, null, null)).isEmpty());
        for (int i = 0; i < InputLimits.MAX_ROUTINES_PER_HOME; i++) {
            routines.create(code, alex.getId(), spec("R" + i, null)).orElseThrow();
        }
        assertTrue(routines.create(code, alex.getId(), spec("One too many", null)).isEmpty());
    }

    // ---- Today and ticking ----------------------------------------------------

    @Test
    void today_isTheHomesAndTheViewersOwn_andTheDeviceSeesEveryone() {
        Member alex = chores.createHome("Nest", "Alex");
        String code = alex.getHomeCode();
        Member sam = chores.joinHome(code, "Sam").orElseThrow();
        routines.create(code, alex.getId(), spec("Bins", null)).orElseThrow();
        routines.create(code, alex.getId(), spec("Alex run", alex.getId())).orElseThrow();
        routines.create(code, alex.getId(), spec("Sam piano", sam.getId())).orElseThrow();
        DayOfWeek notToday = routines.today(code).getDayOfWeek().plus(1);
        routines.create(code, alex.getId(),
                new Spec("Tomorrow only", null, Set.of(notToday), 1, false, null, null)).orElseThrow();

        assertEquals(List.of("Alex run", "Bins"), names(routines.today(code, alex.getId())));
        assertEquals(List.of("Bins", "Sam piano"), names(routines.today(code, sam.getId())));
        assertEquals(3, routines.today(code, null).size(), "the shared device: everyone's");
    }

    @Test
    void anyoneInTheHome_ticksAndUnticks_onlyToday_andOnlyOnce() {
        Member alex = chores.createHome("Nest", "Alex");
        String code = alex.getHomeCode();
        Member sam = chores.joinHome(code, "Sam").orElseThrow();
        Routine bins = routines.create(code, alex.getId(), spec("Bins", null)).orElseThrow();

        assertTrue(routines.setDone(code, bins.getId(), Author.member(sam.getId()), true));
        assertFalse(routines.setDone(code, bins.getId(), Author.member(alex.getId()), true), "once a day");
        assertEquals(sam.getId(), routines.today(code, alex.getId()).get(0).done().getDoneByMemberId());
        assertTrue(routines.setDone(code, bins.getId(), Author.member(alex.getId()), false));
        assertFalse(routines.today(code, alex.getId()).get(0).isDone());

        DayOfWeek notToday = routines.today(code).getDayOfWeek().plus(1);
        Routine tomorrow = routines.create(code, alex.getId(),
                new Spec("Tomorrow", null, Set.of(notToday), 1, false, null, null)).orElseThrow();
        assertFalse(routines.setDone(code, tomorrow.getId(), Author.member(alex.getId()), true),
                "a day it does not fall on cannot be ticked");
    }

    @Test
    void theSharedDevice_ticksWithNoName_andAnotherHomesDeviceCannot() {
        Member alex = chores.createHome("Nest", "Alex");
        String code = alex.getHomeCode();
        Member kim = chores.createHome("Elsewhere", "Kim");
        Long ours = devices.pair(code, chores.findHome(code).orElseThrow().getAdminPin(), "Tab", "Tab")
                .paired().device().getId();
        Long theirs = devices.pair(kim.getHomeCode(),
                chores.findHome(kim.getHomeCode()).orElseThrow().getAdminPin(), "Tab", "Tab")
                .paired().device().getId();
        Routine bins = routines.create(code, alex.getId(), spec("Bins", null)).orElseThrow();

        assertThrows(IllegalArgumentException.class,
                () -> routines.setDone(code, bins.getId(), Author.device(theirs), true));
        assertTrue(routines.setDone(code, bins.getId(), Author.device(ours), true));
        assertNull(routines.today(code, null).get(0).done().getDoneByMemberId());
    }

    @Test
    void switchingRoutinesOff_emptiesToday_andTheTabStoresNoLines() {
        Member alex = chores.createHome("Nest", "Alex");
        String code = alex.getHomeCode();
        routines.create(code, alex.getId(), spec("Bins", null)).orElseThrow();
        lists.setListEnabled(code, alex.getId(), ListKind.ROUTINES, false);
        assertTrue(routines.today(code, alex.getId()).isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> lists.add(code, ListKind.ROUTINES, Author.member(alex.getId()), "a line"));
    }

    // ---- The home's clock -----------------------------------------------------

    @Test
    void theFirstBoardSetsTheZone_afterThatOnlyAnAdminMovesIt_andRemindersFollow() {
        Member alex = chores.createHome("Nest", "Alex");
        String code = alex.getHomeCode();
        Member sam = chores.joinHome(code, "Sam").orElseThrow();
        routines.adoptZone(code, "Europe/Helsinki");
        routines.adoptZone(code, "America/New_York");
        assertEquals(ZoneId.of("Europe/Helsinki"), routines.zoneOf(code), "a traveller does not move it");
        routines.adoptZone(code, "Not/AZone");

        Routine run = routines.create(code, alex.getId(), timed("Run", null, LocalTime.of(18, 0))).orElseThrow();
        Instant inHelsinki = repo.findById(run.getId()).orElseThrow().getNextFireAt();
        assertThrows(IllegalArgumentException.class,
                () -> routines.setZone(code, sam.getId(), "America/New_York"));
        assertFalse(routines.setZone(code, alex.getId(), "Not/AZone"));
        assertTrue(routines.setZone(code, alex.getId(), "America/New_York"));
        Instant inNewYork = repo.findById(run.getId()).orElseThrow().getNextFireAt();
        assertEquals(LocalTime.of(18, 0), inNewYork.atZone(ZoneId.of("America/New_York")).toLocalTime());
        assertFalse(inHelsinki.equals(inNewYork), "the next reminder moved with the clock");
    }

    // ---- The sweep ------------------------------------------------------------

    @Test
    void aHomeRoutine_remindsEveryDevice_aPersonalOne_onlyItsMember_andBothMoveOn() {
        when(sender.isEnabled()).thenReturn(true);
        when(sender.send(any(), anyString(), anyString())).thenReturn(true);
        Member alex = chores.createHome("Nest", "Alex");
        String code = alex.getHomeCode();
        Member sam = chores.joinHome(code, "Sam").orElseThrow();
        subscribe(alex);
        subscribe(sam);
        Routine bins = routines.create(code, alex.getId(), timed("Bins", null, LocalTime.of(19, 0))).orElseThrow();
        Routine piano = routines.create(code, alex.getId(), timed("Piano", sam.getId(), LocalTime.of(16, 30))).orElseThrow();
        dueNow(bins);
        dueNow(piano);

        assertEquals(3, routines.sendDue(Instant.now()), "two devices for the bins, one for piano");
        verify(sender, times(2)).send(any(), eq("🏋️ Bins"), anyString());
        verify(sender, times(1)).send(any(), eq("🏋️ Piano"), anyString());
        assertTrue(repo.findById(bins.getId()).orElseThrow().getNextFireAt().isAfter(Instant.now()));
        assertEquals(0, routines.sendDue(Instant.now()), "never twice for one occurrence");
    }

    @Test
    void aRoutineTickedBeforeItsTime_isNotNagged_andAStaleOneIsSkipped() {
        when(sender.isEnabled()).thenReturn(true);
        when(sender.send(any(), anyString(), anyString())).thenReturn(true);
        Member alex = chores.createHome("Nest", "Alex");
        String code = alex.getHomeCode();
        subscribe(alex);
        Routine run = routines.create(code, alex.getId(), timed("Run", alex.getId(), LocalTime.of(18, 0))).orElseThrow();
        routines.setDone(code, run.getId(), Author.member(alex.getId()), true);
        dueNow(run);
        assertEquals(0, routines.sendDue(Instant.now()));

        Routine late = routines.create(code, alex.getId(), timed("Late", alex.getId(), LocalTime.of(7, 0))).orElseThrow();
        Routine stale = repo.findById(late.getId()).orElseThrow();
        stale.setNextFireAt(Instant.now().minus(RoutineService.STALE_AFTER).minusSeconds(60));
        repo.save(stale);
        assertEquals(0, routines.sendDue(Instant.now()));
        verify(sender, never()).send(any(), anyString(), anyString());
        assertTrue(repo.findById(late.getId()).orElseThrow().getNextFireAt().isAfter(Instant.now()),
                "skipped, but moved on rather than left for every sweep");
    }

    @Test
    void aRoutineWithoutATime_isNeverOnTheSweep() {
        Member alex = chores.createHome("Nest", "Alex");
        Routine r = routines.create(alex.getHomeCode(), alex.getId(), spec("Any time", null)).orElseThrow();
        assertNull(repo.findById(r.getId()).orElseThrow().getNextFireAt());
    }

    @Test
    void oldTicksArePurged_todaysStay() {
        Member alex = chores.createHome("Nest", "Alex");
        String code = alex.getHomeCode();
        Routine bins = routines.create(code, alex.getId(), spec("Bins", null)).orElseThrow();
        routines.setDone(code, bins.getId(), Author.member(alex.getId()), true);
        LocalDate today = routines.today(code);
        assertEquals(0, routines.purgeOldTicks(today));
        assertEquals(1, routines.purgeOldTicks(today.plusDays(RoutineService.DONE_RETENTION_DAYS + 1)));
    }

    // ---- Members, homes, backups ------------------------------------------------

    @Test
    void aRemovedMembersRoutinesGo_theHomesStay_andDeletingTheHomeTakesAll() {
        Member alex = chores.createHome("Nest", "Alex");
        String code = alex.getHomeCode();
        Member sam = chores.joinHome(code, "Sam").orElseThrow();
        Routine piano = routines.create(code, alex.getId(), spec("Piano", sam.getId())).orElseThrow();
        routines.create(code, sam.getId(), spec("Run", sam.getId())).orElseThrow();
        routines.create(code, sam.getId(), spec("Stretch", sam.getId())).orElseThrow();
        routines.create(code, alex.getId(), spec("Bins", null)).orElseThrow();
        routines.setDone(code, piano.getId(), Author.member(sam.getId()), true);

        assertTrue(chores.removeMember(sam.getId()));
        assertEquals(List.of("Bins"), routines.routinesOf(code).stream().map(Routine::getName).toList());
        assertTrue(ticks.findAll().isEmpty());

        assertTrue(chores.deleteHome(code));
        assertTrue(repo.findAll().stream().noneMatch(r -> r.getHomeCode().equals(code)));
    }

    @Test
    void routinesAndTheZone_roundTripThroughABackup_withMembersRemapped() {
        Member alex = chores.createHome("Nest", "Alex");
        String code = alex.getHomeCode();
        Member sam = chores.joinHome(code, "Sam").orElseThrow();
        routines.adoptZone(code, "Europe/Stockholm");
        routines.create(code, alex.getId(), new Spec("Piano", "🎹", EnumSet.of(DayOfWeek.THURSDAY),
                Routine.EVERY_OTHER_WEEK, true, LocalTime.of(16, 30), sam.getId())).orElseThrow();
        Routine bins = routines.create(code, alex.getId(), spec("Bins", null)).orElseThrow();
        routines.setDone(code, bins.getId(), Author.member(alex.getId()), true);
        LocalDate anchor = routines.routinesOf(code).stream().filter(r -> r.getName().equals("Piano"))
                .findFirst().orElseThrow().getAnchorMonday();
        String json = backup.export(code);

        backup.restore(json.getBytes(StandardCharsets.UTF_8), code, alex.getId());

        Member newSam = chores.membersOf(code).stream().filter(m -> m.getName().equals("Sam"))
                .findFirst().orElseThrow();
        Routine piano = routines.routinesOf(code).stream().filter(r -> r.getName().equals("Piano"))
                .findFirst().orElseThrow();
        assertEquals(newSam.getId(), piano.getForMemberId());
        assertEquals(Routine.EVERY_OTHER_WEEK, piano.getEveryWeeks());
        assertEquals(anchor, piano.getAnchorMonday(), "the same weeks as before");
        assertEquals(LocalTime.of(16, 30), piano.getTime());
        assertNotNull(piano.getNextFireAt());
        assertEquals(ZoneId.of("Europe/Stockholm"), routines.zoneOf(code));
        assertEquals(2, routines.routinesOf(code).size());
        assertTrue(routines.doneToday(code).isEmpty(), "ticks are not family history");
    }

    @Test
    void aHandEditedBackup_cannotPlantALineOfTheRoutinesKind_norAnOrphanRoutine() {
        Member alex = chores.createHome("Nest", "Alex");
        String code = alex.getHomeCode();
        String json = backup.export(code)
                .replace("\"listItems\" : [ ]", "\"listItems\" : [ { \"kind\" : \"ROUTINES\", \"text\" : \"x\" } ]")
                .replace("\"routines\" : [ ]", "\"routines\" : [ { \"name\" : \"Ghost\", \"weekdays\" : 1,"
                        + " \"everyWeeks\" : 1, \"forMemberId\" : 999999 } ]");
        assertTrue(json.contains("Ghost") && json.contains("\"ROUTINES\""), "the edit took");
        backup.restore(json.getBytes(StandardCharsets.UTF_8), code, alex.getId());
        assertTrue(lists.openItems(code, ListKind.TODO).isEmpty());
        assertTrue(routines.routinesOf(code).isEmpty());
    }

    private static List<String> names(List<RoutineService.Today> today) {
        return today.stream().map(t -> t.routine().getName()).sorted().toList();
    }
}
