package com.homechores.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.homechores.domain.ChoreTask;
import com.homechores.domain.ChoreTaskRepository;
import com.homechores.domain.Home;
import com.homechores.domain.HomeRepository;
import com.homechores.domain.Member;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * The scheduled sweep that frees lapsed "I'll do it" holds.
 *
 * <p>Deliberately NOT {@code @Transactional}: {@link HomeState#bump} defers to
 * {@code afterCommit}, so inside a rolled-back test transaction the revision would never
 * move and the bump assertion would test nothing. The test database is in-memory and
 * throwaway, so committing is harmless.
 */
@SpringBootTest
class BookingExpiryTest {

    @Autowired ChoreService service;
    @Autowired ChoreTaskRepository taskRepo;
    @Autowired HomeRepository homeRepo;
    @Autowired HomeState homeState;

    /**
     * Drains bookings left expired by other tests, so the counts below can be exact.
     * The sweep is global by nature — it has no home to scope itself to.
     */
    @BeforeEach
    void drain() {
        service.releaseExpiredBookings();
    }

    /** A home with a 2-hour hold, one member, and its first chore booked by that member. */
    private ChoreTask homeWithBooking() {
        Member alex = service.createHome("Nest", "Alex");
        Home home = service.findHome(alex.getHomeCode()).orElseThrow();
        home.setBookingTimeoutHours(2);
        service.saveHome(home);

        ChoreTask chore = service.tasksOf(alex.getHomeCode()).get(0);
        assertTrue(service.bookChore(chore.getId(), alex.getId()));
        return taskRepo.findById(chore.getId()).orElseThrow();
    }

    /** Fast-forwards a booking into the past so the hold has lapsed. */
    private void backdate(ChoreTask task, Duration age) {
        task.setBookedAt(Instant.now().minus(age));
        taskRepo.save(task);
    }

    @Test
    void expiredBooking_isCleared() {
        ChoreTask booked = homeWithBooking();
        backdate(booked, Duration.ofHours(3));

        assertEquals(1, service.releaseExpiredBookings());

        ChoreTask after = taskRepo.findById(booked.getId()).orElseThrow();
        assertNull(after.getBookedByMemberId(), "booker cleared");
        assertNull(after.getBookedAt(), "booking timestamp cleared");
    }

    @Test
    void liveBooking_isUntouched() {
        ChoreTask booked = homeWithBooking();

        assertEquals(0, service.releaseExpiredBookings());

        ChoreTask after = taskRepo.findById(booked.getId()).orElseThrow();
        assertEquals(booked.getBookedByMemberId(), after.getBookedByMemberId(), "still held");
        assertNotNull(after.getBookedAt());
    }

    @Test
    void sweep_bumpsHomeRevision_onlyWhenSomethingWasFreed() {
        ChoreTask booked = homeWithBooking();
        String code = booked.getHomeCode();

        double quiet = homeState.revision(code).peek();
        service.releaseExpiredBookings();
        assertEquals(quiet, homeState.revision(code).peek(),
                "a live booking must not wake the boards");

        backdate(booked, Duration.ofHours(3));
        service.releaseExpiredBookings();
        assertTrue(homeState.revision(code).peek() > quiet,
                "freeing the hold must bump the home so open boards re-render");
    }

    @Test
    void orphanedBooking_isCleared() {
        ChoreTask booked = homeWithBooking();
        // Home row gone but the task survives: nobody could ever un-book it by hand.
        homeRepo.deleteById(booked.getHomeCode());

        assertEquals(1, service.releaseExpiredBookings());
        assertNull(taskRepo.findById(booked.getId()).orElseThrow().getBookedByMemberId());
    }
}
