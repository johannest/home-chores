package com.homechores.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.homechores.domain.ChoreTask;
import com.homechores.domain.ChoreTaskRepository;
import com.homechores.domain.Completion;
import com.homechores.domain.CompletionRepository;
import com.homechores.domain.DivisionStyle;
import com.homechores.domain.Home;
import com.homechores.domain.Member;
import com.homechores.domain.Season;
import com.homechores.domain.Seasons;
import com.homechores.domain.TimeWindows;
import com.homechores.service.ChoreService.CompleteOutcome;
import com.homechores.service.ChoreService.LockReason;
import com.homechores.service.ChoreService.TaskView;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ChoreFeaturesTest {

    @Autowired ChoreService service;
    @Autowired ChoreTaskRepository taskRepo;
    @Autowired CompletionRepository completionRepo;

    private List<ChoreTask> tasks(String code) {
        return service.tasksOf(code);
    }

    /** Removes availability windows from the seeded chores so tests that complete an
     *  arbitrary (e.g. rotation-assigned) chore don't depend on the wall-clock time. */
    private void clearWindows(String code) {
        taskRepo.findByHomeCodeOrderByCreatedAtAsc(code).forEach(t -> {
            t.setAvailableWindows(null);
            taskRepo.save(t);
        });
    }

    /** A fixed-offset zone whose current wall-clock hour is {@code hour}. */
    private static ZoneId zoneWhereLocalHourIs(int hour) {
        int utcHour = Instant.now().atOffset(ZoneOffset.UTC).getHour();
        int diff = hour - utcHour;
        if (diff > 14) {
            diff -= 24;
        }
        if (diff < -14) {
            diff += 24;
        }
        return ZoneOffset.ofHours(diff);
    }

    // ---- Booking ------------------------------------------------------------

    @Test
    void booking_blocksOthers_thenClearsOnCompletion() {
        Member alex = service.createHome("Nest", "Alex");
        Member sam = service.joinHome(alex.getHomeCode(), "Sam").orElseThrow();
        Long dish = tasks(alex.getHomeCode()).get(0).getId();

        assertTrue(service.bookChore(dish, alex.getId()));
        assertFalse(service.bookChore(dish, sam.getId()), "already booked by Alex");

        CompleteOutcome samTry = service.complete(dish, sam.getId());
        assertFalse(samTry.allowed());
        assertEquals(LockReason.BOOKED, samTry.blockReason());

        assertTrue(service.complete(dish, alex.getId()).allowed(), "booker can complete");
        // Booking cleared on completion, so Sam can now book it.
        assertTrue(service.bookChore(dish, sam.getId()));
    }

    @Test
    void booking_expires_afterTimeout() {
        Member alex = service.createHome("Nest", "Alex");
        Member sam = service.joinHome(alex.getHomeCode(), "Sam").orElseThrow();
        Home home = service.findHome(alex.getHomeCode()).orElseThrow();
        home.setBookingTimeoutHours(2);
        service.saveHome(home);
        ChoreTask dish = tasks(alex.getHomeCode()).get(0);

        assertTrue(service.bookChore(dish.getId(), alex.getId()));
        // Fast-forward the booking into the past so it's expired.
        ChoreTask booked = taskRepo.findById(dish.getId()).orElseThrow();
        booked.setBookedAt(Instant.now().minus(Duration.ofHours(3)));
        taskRepo.save(booked);

        assertTrue(service.complete(dish.getId(), sam.getId()).allowed(),
                "expired booking no longer blocks others");
    }

    @Test
    void cancelBooking_freesItUp() {
        Member alex = service.createHome("Nest", "Alex");
        Member sam = service.joinHome(alex.getHomeCode(), "Sam").orElseThrow();
        Long dish = tasks(alex.getHomeCode()).get(0).getId();

        assertTrue(service.bookChore(dish, alex.getId()));
        service.cancelBooking(dish, alex.getId());
        assertTrue(service.bookChore(dish, sam.getId()), "freed after cancel");
    }

    // ---- Interval (every-N-days) -------------------------------------------

    @Test
    void intervalChore_notDueUntilIntervalPasses() {
        Member alex = service.createHome("Nest", "Alex");
        ChoreTask plants = service.addTask(alex.getHomeCode(), "Water plants", "🪴", 5);

        assertTrue(service.isDue(plants), "due the first time");
        assertTrue(service.complete(plants.getId(), alex.getId()).allowed());

        assertFalse(service.isDue(plants), "not due right after doing it");
        assertEquals(LocalDate.now().plusDays(5), service.nextDueDate(plants));
        CompleteOutcome early = service.complete(plants.getId(), alex.getId());
        assertFalse(early.allowed());
        assertEquals(LockReason.NOT_DUE, early.blockReason());

        // Backdate the completion so the interval has elapsed.
        Completion c = completionRepo.findByHomeCode(alex.getHomeCode()).get(0);
        c.setDoneAt(Instant.now().minus(Duration.ofDays(6)));
        completionRepo.save(c);

        assertTrue(service.isDue(plants), "due again after 6 days");
        assertTrue(service.complete(plants.getId(), alex.getId()).allowed());
    }

    /**
     * The board computes due dates from the completions it already loaded, via a different code
     * path from the public nextDueDate. Pin that path: it is what draws the "🕒 in Nd" badge.
     */
    @Test
    void boardReportsTheSameDueDate_asTheServiceApi() {
        Member alex = service.createHome("Nest", "Alex");
        ChoreTask plants = service.addTask(alex.getHomeCode(), "Water plants", "🪴", 5);
        assertTrue(service.complete(plants.getId(), alex.getId()).allowed());

        TaskView view = service.taskViews(alex.getHomeCode(), alex.getId()).stream()
                .filter(v -> v.task().getId().equals(plants.getId()))
                .findFirst().orElseThrow();

        assertEquals(LocalDate.now().plusDays(5), view.nextDueDate(), "board's own due date");
        assertEquals(service.nextDueDate(plants), view.nextDueDate(), "and it agrees with the API");
        assertFalse(view.due());
        assertEquals(LockReason.NOT_DUE, view.lockReason());
    }

    // ---- Seasonal chores ---------------------------------------------------

    /** The season it is on the server right now — the gate uses LocalDate.now(). */
    private static Season currentSeason() {
        return Season.of(LocalDate.now().getMonth());
    }

    /** The season half a year away, so it is never the current one. */
    private static Season oppositeSeason() {
        Season[] all = Season.values();
        return all[(currentSeason().ordinal() + 2) % all.length];
    }

    private ChoreTask seasonalChore(String homeCode, Season season) {
        return service.addTask(homeCode, "Seasonal", "🍂", 0, 0, null, season.name());
    }

    @Test
    void untaggedChore_isDoableWhateverTheSeason() {
        Member alex = service.createHome("Nest", "Alex");
        ChoreTask any = service.addTask(alex.getHomeCode(), "Anytime", "🧹", 0);

        assertNull(any.getSeasons(), "no tag means all year round");
        assertTrue(service.complete(any.getId(), alex.getId()).allowed());
    }

    @Test
    void inSeasonChore_isDoable() {
        Member alex = service.createHome("Nest", "Alex");
        ChoreTask chore = seasonalChore(alex.getHomeCode(), currentSeason());

        assertTrue(service.complete(chore.getId(), alex.getId()).allowed(),
                "tagged with the season we are actually in");
    }

    @Test
    void outOfSeasonChore_isBlocked() {
        Member alex = service.createHome("Nest", "Alex");
        ChoreTask chore = seasonalChore(alex.getHomeCode(), oppositeSeason());

        CompleteOutcome outcome = service.complete(chore.getId(), alex.getId());
        assertFalse(outcome.allowed());
        assertEquals(LockReason.OUT_OF_SEASON, outcome.blockReason());
    }

    @Test
    void outOfSeasonChore_readsAsLockedOnTheBoard() {
        Member alex = service.createHome("Nest", "Alex");
        ChoreTask chore = seasonalChore(alex.getHomeCode(), oppositeSeason());

        TaskView view = service.taskViews(alex.getHomeCode(), alex.getId()).stream()
                .filter(v -> v.task().getId().equals(chore.getId()))
                .findFirst().orElseThrow();
        assertTrue(view.lockedForMe());
        assertEquals(LockReason.OUT_OF_SEASON, view.lockReason());
    }

    /**
     * Season is reported ahead of the interval. Otherwise a winter chore on a yearly interval
     * badges "in 200d" — true, and no help at all in working out why it can't be tapped.
     */
    @Test
    void outOfSeasonBeatsNotDue_asTheReportedReason() {
        Member alex = service.createHome("Nest", "Alex");
        ChoreTask chore = service.addTask(alex.getHomeCode(), "Rake leaves", "🍂", 365, 0, null,
                oppositeSeason().name());
        // Do it once so the yearly interval also has it locked.
        service.completeFor(chore.getId(), alex.getId(), alex.getId());

        CompleteOutcome outcome = service.complete(chore.getId(), alex.getId());
        assertFalse(outcome.allowed());
        assertEquals(LockReason.OUT_OF_SEASON, outcome.blockReason(),
                "season is the useful reason, not the interval");
    }

    @Test
    void allFourSeasonsTagged_behavesLikeUntagged() {
        Member alex = service.createHome("Nest", "Alex");
        ChoreTask chore = service.addTask(alex.getHomeCode(), "Every season", "🔁", 0, 0, null,
                Seasons.normalize("SPRING,SUMMER,AUTUMN,WINTER"));

        assertNull(chore.getSeasons(), "every season is the same as no restriction");
        assertTrue(service.complete(chore.getId(), alex.getId()).allowed());
    }

    /** A mangled value (hand-edited backup) must fail open rather than orphan the chore. */
    @Test
    void garbledSeasonValue_failsOpen() {
        Member alex = service.createHome("Nest", "Alex");
        ChoreTask chore = service.addTask(alex.getHomeCode(), "Broken", "❓", 0);
        ChoreTask stored = taskRepo.findById(chore.getId()).orElseThrow();
        stored.setSeasons("NOT_A_SEASON");
        taskRepo.save(stored);

        assertTrue(service.complete(chore.getId(), alex.getId()).allowed());
    }

    // ---- Rotating division --------------------------------------------------

    @Test
    void rotation_assignsDistinctChores_andEnforcesWhenConfigured() {
        Member alex = service.createHome("Nest", "Alex");
        clearWindows(alex.getHomeCode());
        Member sam = service.joinHome(alex.getHomeCode(), "Sam").orElseThrow();
        Home home = service.findHome(alex.getHomeCode()).orElseThrow();
        home.setDivisionStyle(DivisionStyle.ROTATING);
        home.setRotationEnforced(true);
        service.saveHome(home);

        LocalDate today = LocalDate.now();
        Long alexChore = service.rotationAssignedChoreId(home, alex.getId(), today);
        Long samChore = service.rotationAssignedChoreId(home, sam.getId(), today);
        assertNotEquals(alexChore, samChore, "two members get different chores");

        // Enforced: Alex can do his assigned chore but not Sam's.
        CompleteOutcome wrong = service.complete(samChore, alex.getId());
        assertFalse(wrong.allowed());
        assertEquals(LockReason.NOT_ASSIGNED, wrong.blockReason());
        assertTrue(service.complete(alexChore, alex.getId()).allowed());

        // Suggested: anyone can do any chore.
        home = service.findHome(alex.getHomeCode()).orElseThrow();
        home.setRotationEnforced(false);
        service.saveHome(home);
        assertTrue(service.complete(samChore, alex.getId()).allowed(),
                "suggested mode lets Alex help with Sam's chore");
    }

    @Test
    void rotation_advancesByDay() {
        Member alex = service.createHome("Nest", "Alex");
        service.joinHome(alex.getHomeCode(), "Sam");
        Home home = service.findHome(alex.getHomeCode()).orElseThrow();
        home.setDivisionStyle(DivisionStyle.ROTATING);
        service.saveHome(home);

        Long todayChore = service.rotationAssignedChoreId(home, alex.getId(), LocalDate.now());
        Long tomorrowChore = service.rotationAssignedChoreId(home, alex.getId(), LocalDate.now().plusDays(1));
        assertNotEquals(todayChore, tomorrowChore, "assignment rotates day to day");
    }

    // ---- Availability windows (time of day) ----------------------------------

    @Test
    void timeWindows_normalize_parse_andCheck() {
        assertEquals("08:00-10:00,18:00-22:00", TimeWindows.normalize("8-10, 18:00-22"));
        assertNull(TimeWindows.normalize("   "), "blank = no windows");
        assertThrows(IllegalArgumentException.class, () -> TimeWindows.normalize("10-8"));
        assertThrows(IllegalArgumentException.class, () -> TimeWindows.normalize("abc"));

        String dog = "08:00-10:00,18:00-22:00";
        assertTrue(TimeWindows.isWithinAny(dog, LocalTime.of(9, 0)));
        assertTrue(TimeWindows.isWithinAny(dog, LocalTime.of(21, 59)));
        assertFalse(TimeWindows.isWithinAny(dog, LocalTime.of(12, 0)));
        assertFalse(TimeWindows.isWithinAny(dog, LocalTime.of(22, 0)), "end is exclusive");
        assertTrue(TimeWindows.isWithinAny(null, LocalTime.NOON), "no windows = always available");
        assertTrue(TimeWindows.isWithinAny("garbage", LocalTime.NOON), "bad data fails open");

        assertEquals("8–10, 18–22", TimeWindows.displayCompact(dog));
    }

    @Test
    void windowedChore_blockedOutsideHours_allowedInside() {
        Member alex = service.createHome("Nest", "Alex");
        ChoreTask dog = service.addTask(alex.getHomeCode(), "Walk the dog", "🐕", 0, 0,
                "08:00-10:00");

        CompleteOutcome outside = service.complete(dog.getId(), alex.getId(),
                zoneWhereLocalHourIs(12));
        assertFalse(outside.allowed());
        assertEquals(LockReason.OUTSIDE_HOURS, outside.blockReason());

        assertTrue(service.complete(dog.getId(), alex.getId(), zoneWhereLocalHourIs(9)).allowed(),
                "inside the window the chore can be completed");
    }

    // ---- Localized default chores --------------------------------------------

    @Test
    void seededDefaults_areLocalized_withIntervalAndWindows() {
        Member alex = service.createHome("Pesä", "Aleksi", Locale.of("fi"));
        List<ChoreTask> seeded = tasks(alex.getHomeCode());

        assertEquals(11, seeded.size());
        assertTrue(seeded.stream().anyMatch(t ->
                        t.getName().equals("Kastele kukat") && t.getIntervalDays() == 7),
                "water plants seeded in Finnish with a 7-day interval");
        assertTrue(seeded.stream().anyMatch(t -> t.getName().equals("Ulkoiluta koira")
                        && "08:00-10:00,18:00-22:00".equals(t.getAvailableWindows())),
                "dog walk seeded in Finnish with morning + evening windows");
    }
}
