package com.homechores.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.EnumSet;
import java.util.Set;

/**
 * Something the family does on fixed weekdays (§4.4f) — "🏋️ Workout, Tuesdays 18:00", "Bins
 * out, every other Sunday". Not a chore: ticking it earns nothing, starts no streak and is not
 * part of the fairness rules, the way a list line is not.
 *
 * <p>Occurrences are computed, never stored: a routine occurs on a date when the date's weekday
 * is in {@link #getWeekdays()} and, for every other week, the date's week is an even number of
 * weeks from {@link #getAnchorMonday()}. Only ticks leave rows ({@link RoutineDone}), so a
 * routine nobody ticks costs nothing over time.
 *
 * <p>Brand-new table, so no {@code columnDefinition} defaults are needed — see the note on
 * {@link ChoreGroup#getSortOrder()}.
 */
@Entity
public class Routine {

    /** "Every other week" — the only rhythm besides weekly that the editor offers. */
    public static final int EVERY_OTHER_WEEK = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String homeCode;

    /** Length backstop for {@link InputLimits#TASK_NAME}; the service layer clips first. */
    @Column(length = InputLimits.TASK_NAME)
    private String name;

    @Column(length = InputLimits.EMOJI)
    private String emoji;

    /** One bit per weekday, Monday = bit 0 … Sunday = bit 6. Never 0 once saved. */
    private int weekdays;

    /** 1 = every week, {@link #EVERY_OTHER_WEEK} = every other week. */
    private int everyWeeks = 1;

    /** The Monday of a week the routine occurs in; what "every other week" counts from. */
    private LocalDate anchorMonday;

    /** Wall-clock time in the home's zone, or null for "some time that day" (and no reminder). */
    private LocalTime time;

    /** Whose routine it is; null for the whole home. Left as-is if that member is removed —
     *  the service deletes their routines with them. */
    private Long forMemberId;

    private Long createdByMemberId;

    /**
     * Seen only by {@link #forMemberId} (§4.4f): not on anyone else's Routines tab or strip, not
     * on the shared device, and not editable by an admin. Only a routine a member made for
     * themselves can be private, so it is never true with a null {@code forMemberId}.
     */
    private boolean privateToOwner;

    /**
     * When the reminder sweep next looks at this routine: the next occurrence's time as an
     * instant in the home's zone, or null without a {@link #time}. Recomputed on every save,
     * after every sweep and when the home's zone changes — so the sweep's query is a bounded
     * range scan rather than every routine of every home each minute.
     */
    private Instant nextFireAt;

    private Instant createdAt = Instant.now();

    protected Routine() {
    }

    public Routine(String homeCode, Long createdByMemberId) {
        this.homeCode = homeCode;
        this.createdByMemberId = createdByMemberId;
    }

    /** Whether this member — null for the shared device — may see the routine. */
    public boolean isVisibleTo(Long memberId) {
        return !privateToOwner || (memberId != null && memberId.equals(forMemberId));
    }

    // ---- Schedule ----------------------------------------------------------

    /** The bit mask for a set of weekdays. */
    public static int mask(Set<DayOfWeek> days) {
        int m = 0;
        for (DayOfWeek d : days) {
            m |= 1 << (d.getValue() - 1);
        }
        return m;
    }

    /** The weekdays this routine falls on, Monday first. */
    public Set<DayOfWeek> days() {
        Set<DayOfWeek> out = EnumSet.noneOf(DayOfWeek.class);
        for (DayOfWeek d : DayOfWeek.values()) {
            if ((weekdays & (1 << (d.getValue() - 1))) != 0) {
                out.add(d);
            }
        }
        return out;
    }

    /** The Monday of the week {@code date} falls in. */
    public static LocalDate mondayOf(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    public boolean occursOn(LocalDate date) {
        if ((weekdays & (1 << (date.getDayOfWeek().getValue() - 1))) == 0) {
            return false;
        }
        if (everyWeeks <= 1 || anchorMonday == null) {
            return true;
        }
        long weeks = ChronoUnit.WEEKS.between(anchorMonday, mondayOf(date));
        return Math.floorMod(weeks, everyWeeks) == 0;
    }

    /**
     * The first occurrence's time strictly after {@code after}, in {@code zone}; null without a
     * time. Looks two periods ahead at most, which always reaches one (the mask is never empty).
     * A time inside a spring-forward gap lands just after it, as {@code atZone} resolves it.
     */
    public Instant fireAfter(Instant after, ZoneId zone) {
        if (time == null || weekdays == 0) {
            return null;
        }
        LocalDate day = after.atZone(zone).toLocalDate();
        for (int i = 0; i <= 7 * Math.max(1, everyWeeks); i++) {
            LocalDate d = day.plusDays(i);
            if (occursOn(d)) {
                Instant at = d.atTime(time).atZone(zone).toInstant();
                if (at.isAfter(after)) {
                    return at;
                }
            }
        }
        return null;
    }

    // ---- Accessors ----------------------------------------------------------

    public Long getId() {
        return id;
    }

    public String getHomeCode() {
        return homeCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmoji() {
        return emoji;
    }

    public void setEmoji(String emoji) {
        this.emoji = emoji;
    }

    public int getWeekdays() {
        return weekdays;
    }

    public void setWeekdays(int weekdays) {
        this.weekdays = weekdays;
    }

    public int getEveryWeeks() {
        return everyWeeks;
    }

    public void setEveryWeeks(int everyWeeks) {
        this.everyWeeks = everyWeeks;
    }

    public LocalDate getAnchorMonday() {
        return anchorMonday;
    }

    public void setAnchorMonday(LocalDate anchorMonday) {
        this.anchorMonday = anchorMonday;
    }

    public LocalTime getTime() {
        return time;
    }

    public void setTime(LocalTime time) {
        this.time = time;
    }

    public Long getForMemberId() {
        return forMemberId;
    }

    public void setForMemberId(Long forMemberId) {
        this.forMemberId = forMemberId;
    }

    public Long getCreatedByMemberId() {
        return createdByMemberId;
    }

    public void setCreatedByMemberId(Long createdByMemberId) {
        this.createdByMemberId = createdByMemberId;
    }

    public boolean isPrivateToOwner() {
        return privateToOwner;
    }

    public void setPrivateToOwner(boolean privateToOwner) {
        this.privateToOwner = privateToOwner;
    }

    public Instant getNextFireAt() {
        return nextFireAt;
    }

    public void setNextFireAt(Instant nextFireAt) {
        this.nextFireAt = nextFireAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
