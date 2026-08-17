package com.homechores.service;

import com.homechores.domain.ChoreTask;
import com.homechores.domain.Completion;
import com.homechores.domain.CompletionRepository;
import com.homechores.domain.CompletionStatus;
import com.homechores.domain.CreditEntry;
import com.homechores.domain.CreditEntryRepository;
import com.homechores.domain.CreditType;
import com.homechores.domain.SpreeTier;
import com.homechores.domain.SpreeTierRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Credit rewards: per-chore credits for challenging tasks, admin-configured spree
 * bonuses for consecutive-day streaks, balances, and admin redemptions.
 */
@Service
public class CreditService {

    private final CreditEntryRepository credits;
    private final SpreeTierRepository tiers;
    private final CompletionRepository completions;
    private final HomeState homeState;

    public CreditService(CreditEntryRepository credits, SpreeTierRepository tiers,
                        CompletionRepository completions, HomeState homeState) {
        this.credits = credits;
        this.tiers = tiers;
        this.completions = completions;
        this.homeState = homeState;
    }

    private static ZoneId zone() {
        return ZoneId.systemDefault();
    }

    /**
     * Awards any credits due now that {@code memberId} has an APPROVED completion of
     * {@code task}: the chore's own credit value, plus any spree tier just reached.
     */
    @Transactional
    public Award onApprovedCompletion(ChoreTask task, Long memberId, String homeCode,
                                      Long completionId) {
        int choreCredits = 0;
        if (task.getCreditValue() > 0) {
            credits.save(new CreditEntry(homeCode, memberId, task.getCreditValue(),
                    CreditType.EARNED, task.getName(), 0, completionId));
            choreCredits = task.getCreditValue();
        }

        Integer spreeDays = null;
        int spreeCredits = 0;
        Set<LocalDate> days = approvedDays(memberId);
        if (!days.isEmpty()) {
            LocalDate end = days.stream().max(LocalDate::compareTo).orElseThrow();
            int streak = 0;
            LocalDate d = end;
            while (days.contains(d)) {
                streak++;
                d = d.minusDays(1);
            }
            LocalDate start = end.minusDays(streak - 1L);
            for (SpreeTier t : tiers.findByHomeCodeOrderByDaysAsc(homeCode)) {
                if (t.getDays() == streak && t.getCredits() > 0
                        && !alreadyAwardedSpree(memberId, t.getDays(), start)) {
                    // Tagged with the same completion: the spree only landed because of
                    // this chore, so undoing it takes the bonus back too.
                    credits.save(new CreditEntry(homeCode, memberId, t.getCredits(),
                            CreditType.EARNED, "spree", t.getDays(), completionId));
                    spreeDays = t.getDays();
                    spreeCredits = t.getCredits();
                }
            }
        }

        if (choreCredits > 0 || spreeCredits > 0) {
            homeState.bump(homeCode);
        }
        return new Award(choreCredits, spreeDays, spreeCredits);
    }

    private Set<LocalDate> approvedDays(Long memberId) {
        return completions.findByMemberIdAndStatus(memberId, CompletionStatus.APPROVED).stream()
                .map(c -> c.getDoneAt().atZone(zone()).toLocalDate())
                .collect(Collectors.toSet());
    }

    private boolean alreadyAwardedSpree(Long memberId, int tierDays, LocalDate streakStart) {
        return credits.findByMemberId(memberId).stream()
                .filter(e -> e.getType() == CreditType.EARNED && e.getSpreeTierDays() == tierDays)
                .anyMatch(e -> !e.getCreatedAt().atZone(zone()).toLocalDate().isBefore(streakStart));
    }

    // ---- Balances & redemption ---------------------------------------------

    public int balance(Long memberId) {
        int earned = 0;
        int redeemed = 0;
        for (CreditEntry e : credits.findByMemberId(memberId)) {
            if (e.getType() == CreditType.EARNED) {
                earned += e.getAmount();
            } else {
                redeemed += e.getAmount();
            }
        }
        return earned - redeemed;
    }

    /** Logs a redemption (credits spent). Fails if the amount exceeds the balance. */
    @Transactional
    public boolean redeem(String homeCode, Long memberId, int amount, String note, Long adminId) {
        if (amount <= 0 || amount > balance(memberId)) {
            return false;
        }
        String reason = note == null || note.isBlank() ? "Redeemed" : note.trim();
        credits.save(new CreditEntry(homeCode, memberId, amount, CreditType.REDEEMED, reason, 0));
        homeState.bump(homeCode);
        return true;
    }

    public List<CreditEntry> ledger(String homeCode) {
        return credits.findByHomeCodeOrderByCreatedAtDesc(homeCode);
    }

    @Transactional
    public void deleteForMember(Long memberId) {
        credits.deleteByMemberId(memberId);
    }

    /** Takes back whatever a completion earned, when it is undone or deleted. */
    @Transactional
    public void deleteForCompletion(Long completionId) {
        if (completionId != null) {
            credits.deleteByCompletionId(completionId);
        }
    }

    /** Wipes every credit entry and spree tier of a home (used when the home is deleted). */
    @Transactional
    public void deleteForHome(String homeCode) {
        credits.deleteByHomeCode(homeCode);
        tiers.deleteByHomeCode(homeCode);
    }

    // ---- Spree tier admin ---------------------------------------------------

    public List<SpreeTier> tiersOf(String homeCode) {
        return tiers.findByHomeCodeOrderByDaysAsc(homeCode);
    }

    @Transactional
    public void addTier(String homeCode, int days, int credits) {
        if (days <= 0 || credits <= 0) {
            return;
        }
        tiers.save(new SpreeTier(homeCode, days, credits));
        homeState.bump(homeCode);
    }

    @Transactional
    public void deleteTier(Long tierId) {
        tiers.findById(tierId).ifPresent(t -> {
            String homeCode = t.getHomeCode();
            tiers.delete(t);
            homeState.bump(homeCode);
        });
    }

    /** Result of awarding credits for a completion, for celebration purposes. */
    public record Award(int choreCredits, Integer spreeDays, int spreeCredits) {
        public int total() {
            return choreCredits + spreeCredits;
        }
    }
}
