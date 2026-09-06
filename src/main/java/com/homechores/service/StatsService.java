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
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    // ---- Per-member ("My stats") -------------------------------------------

    /**
     * One member's numbers, from a single query for that member's own completions.
     *
     * <p>It used to fetch the whole home's history a second time just to work out one
     * person's feedback split — every other member's rows loaded and thrown away. The
     * member's own rows answer both questions: the APPROVED ones drive the counts, the
     * non-REJECTED ones the split.
     */
    public MyStats myStats(Long memberId, String homeCode) {
        Home home = homes.findById(homeCode).orElseThrow();
        List<Completion> mine = completions.findByMemberId(memberId);
        LocalDate today = LocalDate.now();

        // One pass: chore tallies, other help, per-day counts and the feedback split.
        // ("Other help" has no task, so it's counted separately and labelled by the caller
        // — the label is UI wording, not data like a chore name.)
        Map<Long, Long> byTask = new HashMap<>();
        Map<LocalDate, Long> perDay = new HashMap<>();
        long totalApproved = 0;
        long otherHelp = 0;
        long hate = 0;
        long ok = 0;
        long love = 0;
        for (Completion c : mine) {
            if (c.getStatus() == CompletionStatus.REJECTED) {
                continue; // excluded from every count, and from the feedback split
            }
            if (c.getFeedback() == Feedback.HATE) {
                hate++;
            } else if (c.getFeedback() == Feedback.OK) {
                ok++;
            } else if (c.getFeedback() == Feedback.LOVE) {
                love++;
            }
            if (c.getStatus() != CompletionStatus.APPROVED) {
                continue; // pending: it has feedback to show, but it counts for nothing yet
            }
            totalApproved++;
            if (c.isOtherHelp()) {
                otherHelp++;
            } else {
                byTask.merge(c.getTaskId(), 1L, Long::sum);
            }
            perDay.merge(dateOf(c), 1L, Long::sum);
        }

        // Chore order follows the home's chore list, so the bars match the board.
        List<CountBar> byChore = new ArrayList<>();
        for (ChoreTask t : tasks.findByHomeCodeOrderByCreatedAtAsc(homeCode)) {
            long n = byTask.getOrDefault(t.getId(), 0L);
            if (n > 0) {
                byChore.add(new CountBar(t.getEmoji() + " " + t.getName(), n));
            }
        }

        List<DayCount> last7 = daySeries(perDay, today, 7);
        long doneToday = perDay.getOrDefault(today, 0L);
        return new MyStats(totalApproved, byChore, new FeedbackSplit(hate, ok, love), last7,
                doneToday, home.getDailyTargetPerMember(), otherHelp);
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
    public HomeStats homeStats(String homeCode) {
        Home home = homes.findById(homeCode).orElseThrow();
        List<Member> memberList = members.findByHomeCodeOrderByJoinedAtAsc(homeCode);
        List<ChoreTask> taskList = tasks.findByHomeCodeOrderByCreatedAtAsc(homeCode);
        List<Completion> all = completions.findByHomeCode(homeCode);
        LocalDate today = LocalDate.now();

        Map<Long, Long> approvedByMember = new HashMap<>();
        Map<Long, Long> todayByMember = new HashMap<>();
        Map<Long, Long> approvedByTask = new HashMap<>();
        Map<Long, long[]> feedbackByTaskId = new HashMap<>(); // [hate, ok, love]
        Map<LocalDate, Long> perDay = new HashMap<>();
        long otherHelp = 0;
        long pending = 0;

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
            approvedByMember.merge(c.getMemberId(), 1L, Long::sum);
            LocalDate on = dateOf(c);
            perDay.merge(on, 1L, Long::sum);
            if (on.equals(today)) {
                todayByMember.merge(c.getMemberId(), 1L, Long::sum);
            }
            if (c.isOtherHelp()) {
                // Accepted other help belongs in the picture, but has no chore to hang off.
                otherHelp++;
            } else {
                approvedByTask.merge(c.getTaskId(), 1L, Long::sum);
            }
        }

        // Member and chore order are the home's own (join time, creation time), and every
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

        return new HomeStats(perMember, popularity, feedbackByChore,
                daySeries(perDay, today, 14), adherence, pending, otherHelp);
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

    /** {@code otherHelp} = approved help entries with no chore behind them; the view labels
     *  and appends them, since "Other help" is UI wording rather than a stored name. */
    public record MyStats(long totalApproved, List<CountBar> byChore, FeedbackSplit feedback,
                          List<DayCount> last7, long doneToday, int target, long otherHelp) {
    }

    public record HomeStats(List<CountBar> perMember, List<CountBar> chorePopularity,
                            List<ChoreFeedback> feedbackByChore, List<DayCount> trend14,
                            List<MemberDaily> adherence, long pending, long otherHelp) {
    }
}
