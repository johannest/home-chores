package com.homechores.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The trophy ladder: a member owns every rung their all-time approved count has reached.
 *
 * <p>Fixed, not admin-editable — the admin's lever is the 💎 reward per rung
 * ({@link TrophyReward}). A trophy is never stored: it follows the count, so undoing the chore
 * that reached a rung takes the trophy back, the way it takes back that chore's credits.
 *
 * <p>The emoji avoid the glyphs that already mean something on the board: 👑 admin, 🥇 chore
 * master, 💎 credits, 🔥 / 💪 the daily tiers, 🌟 a new chore, ⏳ pending, 🙋 other help.
 */
public enum Trophy {
    SPROUT(5, "🌱"),
    HELPER(10, "🧹"),
    BRONZE(25, "🥉"),
    SILVER(50, "🥈"),
    GOLD(100, "🏆"),
    MEDAL(250, "🏅"),
    DIPLOMA(500, "🎓"),
    LEGEND(1000, "🦄");

    private final int rung;
    private final String emoji;

    Trophy(int rung, String emoji) {
        this.rung = rung;
        this.emoji = emoji;
    }

    /** The approved-chore count that earns this trophy. */
    public int rung() {
        return rung;
    }

    public String emoji() {
        return emoji;
    }

    /** Message key of the trophy's name ({@code trophy.silver}). */
    public String nameKey() {
        return "trophy." + name().toLowerCase(Locale.ROOT);
    }

    /** The trophy whose rung is exactly {@code count} — the moment it is earned. */
    public static Optional<Trophy> reachedAt(long count) {
        return Arrays.stream(values()).filter(t -> t.rung == count).findFirst();
    }

    /** Every trophy {@code count} has reached, lowest first. */
    public static List<Trophy> earned(long count) {
        return Arrays.stream(values()).filter(t -> t.rung <= count).toList();
    }

    /** The highest trophy {@code count} has reached, if any. */
    public static Optional<Trophy> best(long count) {
        List<Trophy> earned = earned(count);
        return earned.isEmpty() ? Optional.empty() : Optional.of(earned.getLast());
    }

    /** The next trophy to aim for, empty once the whole ladder is climbed. */
    public static Optional<Trophy> next(long count) {
        return Arrays.stream(values()).filter(t -> t.rung > count).findFirst();
    }
}
