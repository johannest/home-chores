package com.homechores.service;

import com.homechores.domain.ChoreTask;
import com.homechores.domain.ChoreTaskRepository;
import com.homechores.domain.Completion;
import com.homechores.domain.CompletionRepository;
import com.homechores.domain.CompletionStatus;
import com.homechores.domain.Feedback;
import com.homechores.domain.Home;
import com.homechores.domain.HomeRepository;
import com.homechores.domain.Member;
import com.homechores.domain.MemberRepository;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.format.TextStyle;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Read-only aggregations for the statistics / chart views. */
@Service
public class StatsService {

    private final HomeRepository homes;
    private final MemberRepository members;
    private final ChoreTaskRepository tasks;
    private final CompletionRepository completions;

    public StatsService(HomeRepository homes, MemberRepository members,
                        ChoreTaskRepository tasks, CompletionRepository completions) {
        this.homes = homes;
        this.members = members;
        this.tasks = tasks;
        this.completions = completions;
    }

    /** How many columns a longer trend shows. Twelve is what fits a ~360px phone column at a
     *  readable width; beyond about fourteen the columns stop being distinguishable and the
     *  labels start colliding, and the page must never scroll sideways (SPEC §4.15.1). */
    static final int TREND_WEEKS = 12;
    static final int TREND_MONTHS = 12;

    private static ZoneId zone() {
        return ZoneId.systemDefault();
    }

    private LocalDate dateOf(Completion c) {
        return c.getDoneAt().atZone(zone()).toLocalDate();
    }

    // ---- Chore master of last week -------------------------------------------

    /** Last week's champion: who, and how many approved chores they did. */
    public record ChoreMaster(Member member, long count) {
    }

    /**
     * The member with the most APPROVED completions in the previous ISO week (Mon–Sun,
     * server zone — the same clock every other aggregate here uses). Ties go to the
     * earliest-joined member, so the badge is stable within a week. Empty for a solo home
     * (a competition of one is no competition) and for a week nobody did anything.
     */
    public Optional<ChoreMaster> lastWeekChoreMaster(String homeCode) {
        List<Member> memberList = members.findByHomeCodeOrderByJoinedAtAsc(homeCode);
        if (memberList.size() < 2) {
            return Optional.empty();
        }
        LocalDate thisMonday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Instant from = thisMonday.minusWeeks(1).atStartOfDay(zone()).toInstant();
        Instant to = thisMonday.atStartOfDay(zone()).toInstant();
        Map<Long, Long> byMember = new HashMap<>();
        completions
                .findByHomeCodeAndStatusInAndDoneAtGreaterThanEqualAndDoneAtLessThanOrderByDoneAtDesc(
                        homeCode, List.of(CompletionStatus.APPROVED), from, to)
                .forEach(c -> byMember.merge(c.getMemberId(), 1L, Long::sum));
        ChoreMaster best = null;
        for (Member m : memberList) { // join order + strictly-greater = stable tie-break
            long count = byMember.getOrDefault(m.getId(), 0L);
            if (count > 0 && (best == null || count > best.count())) {
                best = new ChoreMaster(m, count);
            }
        }
        return Optional.ofNullable(best);
    }

    // ---- Periods -------------------------------------------------------------

    /**
     * The lens a member picks on the Stats tab. A period narrows <em>which completions count</em>
     * — it never changes the counting rules themselves (REJECTED is still excluded everywhere,
     * PENDING still counts only for the feedback split).
     *
     * <p>Bucketing is by {@code doneAt}, so a completion approved days late still belongs to the
     * day it was <em>done</em> — approval never moves it. Weeks start Monday, matching
     * {@link #lastWeekChoreMaster}, so the app has exactly one notion of "week"; months are
     * calendar months. Both use the server zone like every other aggregate here (SPEC §5).
     */
    public enum Period {
        TODAY, WEEK, MONTH, ALL;

        /** The first day this period includes, or null for {@link #ALL}. */
        LocalDate startOn(LocalDate today) {
            return switch (this) {
                case TODAY -> today;
                case WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                case MONTH -> today.withDayOfMonth(1);
                case ALL -> null;
            };
        }

        boolean includes(LocalDate date, LocalDate today) {
            LocalDate from = startOn(today);
            return from == null || !date.isBefore(from);
        }
    }

    /** Headline totals for all four lenses at once — one pass fills them, so offering the
     *  member every period costs nothing beyond three comparisons per row. */
    public record PeriodCounts(long today, long thisWeek, long thisMonth, long allTime) {
        public long of(Period p) {
            return switch (p) {
                case TODAY -> today;
                case WEEK -> thisWeek;
                case MONTH -> thisMonth;
                case ALL -> allTime;
            };
        }
    }

    /**
     * One column of a week- or month-grained trend. Both strings are built here rather than in the
     * chart, because this is where the {@link Locale} is known.
     *
     * <p>Two of them, because a twelve-column trend on a ~360px phone gives each label about 25px
     * and the axis must not spill its track: {@code label} is what fits there, {@code caption} is
     * the readable form the tooltip uses. Finnish is what forces the split — "marrask." is half
     * again wider than its column, and `overflow-x: hidden` would clip it rather than reveal it.
     */
    public record PeriodBucket(LocalDate start, String label, String caption, long value) {
    }

    /**
     * The last {@code weeks} ISO weeks ending with the one containing {@code today}, zero-filled.
     * Derived from the same per-day map the day series uses, so a longer trend costs no extra
     * query and no second traversal of the completions.
     */
    private static List<PeriodBucket> weekSeries(Map<LocalDate, Long> perDay, LocalDate today,
                                                 int weeks, Locale locale) {
        LocalDate thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Map<LocalDate, Long> byWeek = new HashMap<>();
        perDay.forEach((day, n) -> byWeek.merge(
                day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), n, Long::sum));
        List<PeriodBucket> out = new ArrayList<>(weeks);
        // "15.6" — day and month, digits only, so it stays the same width in every language.
        DateTimeFormatter axis = DateTimeFormatter.ofPattern("d.M", locale);
        DateTimeFormatter full = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                .withLocale(locale);
        for (int i = weeks - 1; i >= 0; i--) {
            LocalDate start = thisMonday.minusWeeks(i);
            out.add(new PeriodBucket(start, start.format(axis), start.format(full),
                    byWeek.getOrDefault(start, 0L)));
        }
        return out;
    }

    /** The last {@code months} calendar months ending with {@code today}'s, zero-filled. */
    private static List<PeriodBucket> monthSeries(Map<LocalDate, Long> perDay, LocalDate today,
                                                  int months, Locale locale) {
        Map<LocalDate, Long> byMonth = new HashMap<>();
        perDay.forEach((day, n) -> byMonth.merge(day.withDayOfMonth(1), n, Long::sum));
        List<PeriodBucket> out = new ArrayList<>(months);
        LocalDate thisMonth = today.withDayOfMonth(1);
        for (int i = months - 1; i >= 0; i--) {
            LocalDate start = thisMonth.minusMonths(i);
            // The month NUMBER on the axis, not its name: a localized short name ("marrask.")
            // is wider than the 25px a twelve-column trend leaves it, and would spill the track
            // rather than wrap. The name goes in the tooltip, where there is room for it.
            out.add(new PeriodBucket(start, String.valueOf(start.getMonthValue()),
                    start.getMonth().getDisplayName(TextStyle.FULL, locale) + " "
                            + start.getYear(),
                    byMonth.getOrDefault(start, 0L)));
        }
        return out;
    }

    // ---- Per-member ("My stats") -------------------------------------------

    /**
     * One member's numbers, from a single query for that member's own completions.
     *
     * <p>It used to fetch the whole home's history a second time just to work out one
     * person's feedback split — every other member's rows loaded and thrown away. The
     * member's own rows answer both questions: the APPROVED ones drive the counts, the
     * non-REJECTED ones the split.
     */
    /** All-time, for callers that have no lens of their own (and for the existing tests). */
    public MyStats myStats(Long memberId, String homeCode) {
        return myStats(memberId, homeCode, Period.ALL, Locale.ENGLISH);
    }

    /**
     * One member's numbers under the chosen lens, from a single query for that member's own
     * completions.
     *
     * <p>It used to fetch the whole home's history a second time just to work out one person's
     * feedback split — every other member's rows loaded and thrown away. The member's own rows
     * answer both questions: the APPROVED ones drive the counts, the non-REJECTED ones the split.
     *
     * <p>The period is applied <strong>inside that same pass</strong>, and the four headline
     * totals are all filled at once. Bounding the query instead would mean one round trip per
     * lens, against a {@code doneAt} with no index behind it, to avoid a comparison per row that
     * is already in memory — strictly more work, and it would cost the "one query for the working
     * set" property {@code BoardRenderCostTest} pins. The trends are likewise derived from the
     * per-day map rather than re-walked.
     *
     * <p>{@code byChore} and the feedback split follow the lens; {@code counts} never does — the
     * whole point of the headline row is to compare the periods against each other.
     */
    public MyStats myStats(Long memberId, String homeCode, Period period, Locale locale) {
        Home home = homes.findById(homeCode).orElseThrow();
        List<Completion> mine = completions.findByMemberId(memberId);
        LocalDate today = LocalDate.now();

        // One pass: chore tallies, other help, per-day counts, the feedback split and every
        // period total. ("Other help" has no task, so it's counted separately and labelled by
        // the caller — the label is UI wording, not data like a chore name.)
        Map<Long, Long> byTask = new HashMap<>();
        Map<LocalDate, Long> perDay = new HashMap<>();
        long totalApproved = 0;
        long inPeriod = 0;
        long otherHelp = 0;
        long dayCount = 0;
        long weekCount = 0;
        long monthCount = 0;
        long hate = 0;
        long ok = 0;
        long love = 0;
        for (Completion c : mine) {
            if (c.getStatus() == CompletionStatus.REJECTED) {
                continue; // excluded from every count, and from the feedback split
            }
            boolean approved = c.getStatus() == CompletionStatus.APPROVED;
            LocalDate on = approved ? dateOf(c) : null;
            boolean counted = approved && period.includes(on, today);
            if (c.getFeedback() == Feedback.HATE && (period == Period.ALL || counted)) {
                hate++;
            } else if (c.getFeedback() == Feedback.OK && (period == Period.ALL || counted)) {
                ok++;
            } else if (c.getFeedback() == Feedback.LOVE && (period == Period.ALL || counted)) {
                love++;
            }
            if (!approved) {
                continue; // pending: it has feedback to show, but it counts for nothing yet
            }
            totalApproved++;
            perDay.merge(on, 1L, Long::sum);
            if (Period.TODAY.includes(on, today)) {
                dayCount++;
            }
            if (Period.WEEK.includes(on, today)) {
                weekCount++;
            }
            if (Period.MONTH.includes(on, today)) {
                monthCount++;
            }
            if (!counted) {
                continue; // outside the lens: it still counts toward the totals above
            }
            inPeriod++;
            if (c.isOtherHelp()) {
                otherHelp++;
            } else {
                byTask.merge(c.getTaskId(), 1L, Long::sum);
            }
        }

        // Chore order follows the home's chore list, so the bars match the board — group by
        // group, in the order the admin arranged them. Reading the repository's creation order
        // here instead would silently stop matching the moment anyone reordered anything.
        List<CountBar> byChore = new ArrayList<>();
        for (ChoreTask t : tasks.findByHomeCodeOrderBySortOrderAscCreatedAtAscIdAsc(homeCode)) {
            long n = byTask.getOrDefault(t.getId(), 0L);
            if (n > 0) {
                byChore.add(new CountBar(t.getEmoji() + " " + t.getName(), n));
            }
        }

        Locale loc = locale == null ? Locale.ENGLISH : locale;
        return new MyStats(totalApproved, byChore, new FeedbackSplit(hate, ok, love),
                daySeries(perDay, today, 7), dayCount, home.getDailyTargetPerMember(), otherHelp,
                new PeriodCounts(dayCount, weekCount, monthCount, totalApproved), inPeriod,
                weekSeries(perDay, today, TREND_WEEKS, loc),
                monthSeries(perDay, today, TREND_MONTHS, loc));
    }

    /** The last {@code days} days ending today, zero-filled — a chart needs every column. */
    private static List<DayCount> daySeries(Map<LocalDate, Long> perDay, LocalDate today,
                                            int days) {
        List<DayCount> series = new ArrayList<>(days);
        for (int i = days - 1; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            series.add(new DayCount(d, perDay.getOrDefault(d, 0L)));
        }
        return series;
    }

    // ---- Home-wide ("Admin stats") -----------------------------------------

    /**
     * The whole home's numbers, in one pass over its completions.
     *
     * <p>This is the most expensive read in the app and it re-runs for every connected
     * admin on every {@link HomeState} bump, so how it is written matters. It used to walk
     * the home's completion list once per member, twice per chore, and once more per
     * member for today's adherence — cost proportional to
     * (members + 2·chores + members) × history, which for a family with a couple of years
     * of taps is hundreds of thousands of predicate evaluations to draw one screen. Every
     * one of those tallies is a group-by over the same rows, so they are gathered in a
     * single traversal into maps and read off by key.
     */
    /** All-time, for callers that have no lens of their own (and for the existing tests). */
    public HomeStats homeStats(String homeCode) {
        return homeStats(homeCode, Period.ALL, Locale.ENGLISH);
    }

    public HomeStats homeStats(String homeCode, Period period, Locale locale) {
        Home home = homes.findById(homeCode).orElseThrow();
        List<Member> memberList = members.findByHomeCodeOrderByJoinedAtAsc(homeCode);
        List<ChoreTask> taskList = tasks.findByHomeCodeOrderBySortOrderAscCreatedAtAscIdAsc(homeCode);
        List<Completion> all = completions.findByHomeCode(homeCode);
        LocalDate today = LocalDate.now();

        Map<Long, Long> approvedByMember = new HashMap<>();
        Map<Long, Long> todayByMember = new HashMap<>();
        Map<Long, Long> approvedByTask = new HashMap<>();
        Map<Long, long[]> feedbackByTaskId = new HashMap<>(); // [hate, ok, love]
        Map<LocalDate, Long> perDay = new HashMap<>();
        long otherHelp = 0;
        long pending = 0;
        long dayCount = 0;
        long weekCount = 0;
        long monthCount = 0;
        long allTime = 0;

        for (Completion c : all) {
            CompletionStatus status = c.getStatus();
            if (status == CompletionStatus.PENDING) {
                pending++;
            }
            if (status != CompletionStatus.REJECTED && c.getTaskId() != null
                    && c.getFeedback() != null) {
                long[] split = feedbackByTaskId.computeIfAbsent(c.getTaskId(), k -> new long[3]);
                switch (c.getFeedback()) {
                    case HATE -> split[0]++;
                    case OK -> split[1]++;
                    case LOVE -> split[2]++;
                    default -> { }
                }
            }
            if (status != CompletionStatus.APPROVED) {
                continue;
            }
            LocalDate on = dateOf(c);
            perDay.merge(on, 1L, Long::sum);
            if (on.equals(today)) {
                todayByMember.merge(c.getMemberId(), 1L, Long::sum);
            }
            if (Period.TODAY.includes(on, today)) {
                dayCount++;
            }
            if (Period.WEEK.includes(on, today)) {
                weekCount++;
            }
            if (Period.MONTH.includes(on, today)) {
                monthCount++;
            }
            allTime++;
            if (!period.includes(on, today)) {
                continue; // outside the lens: still counted in the headline totals above
            }
            approvedByMember.merge(c.getMemberId(), 1L, Long::sum);
            if (c.isOtherHelp()) {
                // Accepted other help belongs in the picture, but has no chore to hang off.
                otherHelp++;
            } else {
                approvedByTask.merge(c.getTaskId(), 1L, Long::sum);
            }
        }

        // Member and chore order are the home's own (join time, board order), and every
        // row is emitted even at zero — the charts' categories must not shift about.
        List<CountBar> perMember = new ArrayList<>(memberList.size());
        List<MemberDaily> adherence = new ArrayList<>(memberList.size());
        for (Member m : memberList) {
            perMember.add(new CountBar(m.getName(), approvedByMember.getOrDefault(m.getId(), 0L)));
            adherence.add(new MemberDaily(m, todayByMember.getOrDefault(m.getId(), 0L),
                    home.getDailyTargetPerMember()));
        }

        List<CountBar> popularity = new ArrayList<>(taskList.size());
        List<ChoreFeedback> feedbackByChore = new ArrayList<>();
        for (ChoreTask t : taskList) {
            popularity.add(new CountBar(t.getEmoji() + " " + t.getName(),
                    approvedByTask.getOrDefault(t.getId(), 0L)));
            long[] split = feedbackByTaskId.get(t.getId());
            if (split != null) { // only chores anyone actually reacted to get a row
                feedbackByChore.add(new ChoreFeedback(t,
                        new FeedbackSplit(split[0], split[1], split[2])));
            }
        }

        Locale loc = locale == null ? Locale.ENGLISH : locale;
        return new HomeStats(perMember, popularity, feedbackByChore,
                daySeries(perDay, today, 14), adherence, pending, otherHelp,
                new PeriodCounts(dayCount, weekCount, monthCount, allTime),
                weekSeries(perDay, today, TREND_WEEKS, loc),
                monthSeries(perDay, today, TREND_MONTHS, loc));
    }

    // ---- DTOs ---------------------------------------------------------------

    public record CountBar(String label, long value) {
    }

    public record DayCount(LocalDate date, long value) {
    }

    public record FeedbackSplit(long hate, long ok, long love) {
        public long total() {
            return hate + ok + love;
        }
    }

    public record ChoreFeedback(ChoreTask task, FeedbackSplit split) {
    }

    public record MemberDaily(Member member, long doneToday, int target) {
    }

    /**
     * {@code otherHelp} = approved help entries with no chore behind them; the view labels and
     * appends them, since "Other help" is UI wording rather than a stored name.
     *
     * <p>{@code totalApproved}, {@code counts}, {@code doneToday} and both trends are always
     * all-time facts, whatever lens is selected — a period narrows the bars, not the picture of
     * how the member is doing over time. {@code byChore}, {@code feedback}, {@code otherHelp} and
     * {@code inPeriod} follow the lens.
     */
    public record MyStats(long totalApproved, List<CountBar> byChore, FeedbackSplit feedback,
                          List<DayCount> last7, long doneToday, int target, long otherHelp,
                          PeriodCounts counts, long inPeriod, List<PeriodBucket> byWeek,
                          List<PeriodBucket> byMonth) {
    }

    /**
     * {@code perMember}, {@code chorePopularity} and {@code otherHelp} follow the selected lens;
     * {@code counts}, {@code trend14}, both longer trends and {@code adherence} (which is about
     * today by definition) are always all-time facts. {@code feedbackByChore} likewise stays
     * all-time: it answers "which chore does this family hate", and a week's worth of reactions
     * is too thin to answer it.
     */
    public record HomeStats(List<CountBar> perMember, List<CountBar> chorePopularity,
                            List<ChoreFeedback> feedbackByChore, List<DayCount> trend14,
                            List<MemberDaily> adherence, long pending, long otherHelp,
                            PeriodCounts counts, List<PeriodBucket> byWeek,
                            List<PeriodBucket> byMonth) {
    }
}
