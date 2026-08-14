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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    // ---- Per-member ("My stats") -------------------------------------------

    public MyStats myStats(Long memberId, String homeCode) {
        Home home = homes.findById(homeCode).orElseThrow();
        List<Completion> approved = completions.findByMemberIdAndStatus(memberId, CompletionStatus.APPROVED);

        // by chore
        Map<Long, Long> byTask = new HashMap<>();
        for (Completion c : approved) {
            byTask.merge(c.getTaskId(), 1L, Long::sum);
        }
        List<CountBar> byChore = new ArrayList<>();
        for (ChoreTask t : tasks.findByHomeCodeOrderByCreatedAtAsc(homeCode)) {
            long n = byTask.getOrDefault(t.getId(), 0L);
            if (n > 0) {
                byChore.add(new CountBar(t.getEmoji() + " " + t.getName(), n));
            }
        }

        // feedback split (across this member's non-rejected completions)
        FeedbackSplit fb = feedbackSplit(
                completions.findByHomeCode(homeCode).stream()
                        .filter(c -> c.getMemberId().equals(memberId))
                        .filter(c -> c.getStatus() != CompletionStatus.REJECTED)
                        .toList());

        // last 7 days adherence
        List<DayCount> last7 = new ArrayList<>();
        LocalDate today = LocalDate.now();
        Map<LocalDate, Long> perDay = new HashMap<>();
        for (Completion c : approved) {
            perDay.merge(dateOf(c), 1L, Long::sum);
        }
        for (int i = 6; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            last7.add(new DayCount(d, perDay.getOrDefault(d, 0L)));
        }

        long doneToday = perDay.getOrDefault(today, 0L);
        return new MyStats(approved.size(), byChore, fb, last7, doneToday,
                home.getDailyTargetPerMember());
    }

    // ---- Home-wide ("Admin stats") -----------------------------------------

    public HomeStats homeStats(String homeCode) {
        Home home = homes.findById(homeCode).orElseThrow();
        List<Member> memberList = members.findByHomeCodeOrderByJoinedAtAsc(homeCode);
        List<ChoreTask> taskList = tasks.findByHomeCodeOrderByCreatedAtAsc(homeCode);
        List<Completion> all = completions.findByHomeCode(homeCode);
        LocalDate today = LocalDate.now();

        // approved completions per member
        List<CountBar> perMember = new ArrayList<>();
        for (Member m : memberList) {
            long n = all.stream()
                    .filter(c -> c.getMemberId().equals(m.getId()))
                    .filter(c -> c.getStatus() == CompletionStatus.APPROVED)
                    .count();
            perMember.add(new CountBar(m.getName(), n));
        }

        // chore popularity (approved)
        List<CountBar> popularity = new ArrayList<>();
        List<ChoreFeedback> feedbackByChore = new ArrayList<>();
        for (ChoreTask t : taskList) {
            long n = all.stream()
                    .filter(c -> c.getTaskId().equals(t.getId()))
                    .filter(c -> c.getStatus() == CompletionStatus.APPROVED)
                    .count();
            popularity.add(new CountBar(t.getEmoji() + " " + t.getName(), n));

            FeedbackSplit fb = feedbackSplit(all.stream()
                    .filter(c -> c.getTaskId().equals(t.getId()))
                    .filter(c -> c.getStatus() != CompletionStatus.REJECTED)
                    .toList());
            if (fb.total() > 0) {
                feedbackByChore.add(new ChoreFeedback(t, fb));
            }
        }

        // 14-day activity trend (approved, home-wide)
        Map<LocalDate, Long> perDay = new HashMap<>();
        for (Completion c : all) {
            if (c.getStatus() == CompletionStatus.APPROVED) {
                perDay.merge(dateOf(c), 1L, Long::sum);
            }
        }
        List<DayCount> trend14 = new ArrayList<>();
        for (int i = 13; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            trend14.add(new DayCount(d, perDay.getOrDefault(d, 0L)));
        }

        // today's adherence per member
        List<MemberDaily> adherence = new ArrayList<>();
        for (Member m : memberList) {
            long doneToday = all.stream()
                    .filter(c -> c.getMemberId().equals(m.getId()))
                    .filter(c -> c.getStatus() == CompletionStatus.APPROVED)
                    .filter(c -> dateOf(c).equals(today))
                    .count();
            adherence.add(new MemberDaily(m, doneToday, home.getDailyTargetPerMember()));
        }

        long pending = all.stream().filter(c -> c.getStatus() == CompletionStatus.PENDING).count();
        return new HomeStats(perMember, popularity, feedbackByChore, trend14, adherence, pending);
    }

    private FeedbackSplit feedbackSplit(List<Completion> list) {
        long hate = list.stream().filter(c -> c.getFeedback() == Feedback.HATE).count();
        long ok = list.stream().filter(c -> c.getFeedback() == Feedback.OK).count();
        long love = list.stream().filter(c -> c.getFeedback() == Feedback.LOVE).count();
        return new FeedbackSplit(hate, ok, love);
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

    public record MyStats(long totalApproved, List<CountBar> byChore, FeedbackSplit feedback,
                          List<DayCount> last7, long doneToday, int target) {
    }

    public record HomeStats(List<CountBar> perMember, List<CountBar> chorePopularity,
                            List<ChoreFeedback> feedbackByChore, List<DayCount> trend14,
                            List<MemberDaily> adherence, long pending) {
    }
}
