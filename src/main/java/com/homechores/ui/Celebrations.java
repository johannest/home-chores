package com.homechores.ui;

import com.homechores.domain.Feedback;
import com.homechores.domain.Seasons;
import com.homechores.domain.Trophy;
import com.homechores.service.ChoreService;
import com.homechores.service.ChoreService.CompleteOutcome;
import com.homechores.service.CreditService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Fires confetti, sound and vibration, and shows congratulation dialogs (with quick feedback
 *  capture). */
final class Celebrations {

    private Celebrations() {
    }

    static void fireConfetti(String intensity) {
        UI ui = UI.getCurrent();
        if (ui != null) {
            ui.getPage().executeJs("window.fireConfetti && window.fireConfetti($0)", intensity);
        }
    }

    /**
     * Sound and vibration for one moment: {@code tick}, {@code small}, {@code medium},
     * {@code fire}, {@code big} or {@code blocked}. {@code effects.js} plays whichever of the two
     * this device has switched on, so the server sends the same call to every device.
     */
    static void effect(String kind) {
        UI ui = UI.getCurrent();
        if (ui != null) {
            ui.getPage().executeJs("window.playEffect && window.playEffect($0)", kind);
        }
    }

    /** A trophy this close (or closer) is announced in the ordinary celebration dialog. */
    static final int TROPHY_NEAR = 3;

    /**
     * Handles the full post-completion experience for the acting member.
     *
     * @param onUndo taking the chore back, offered right here because this dialog is the
     *               moment a mis-tap is noticed
     * @param onShowTrophies opens the member's trophy shelf; null where there is none to open
     *                       (the shared device)
     */
    static void afterComplete(ChoreService service, CreditService credits, CompleteOutcome o,
                              Runnable onUndo, Runnable onShowTrophies) {
        // Other help has no chore behind it (task == null). Until now nothing celebrated such
        // an outcome — accepting a member's help just toasts — but an admin logging their own
        // help counts at once and deserves the same moment a chore gets.
        String chore = o.task() != null ? o.task().getName() : T.tr("board.otherHelp");
        if (o.pending()) {
            effect("tick");
            showDialog(service, o, onUndo, "⏳", T.tr("celebrate.pending.title"),
                    T.tr("celebrate.pending.text", chore), null, null);
            return;
        }
        Trophy trophy = o.trophy();
        String burst;
        if (trophy != null) {
            burst = "big";
            int reward = o.award().trophyCredits();
            Paragraph rewardEl = null;
            if (reward > 0) {
                rewardEl = new Paragraph(T.tr("celebrate.trophy.reward", reward));
                rewardEl.addClassName("trophy-reward");
            }
            showDialog(service, o, onUndo, trophy.emoji(),
                    T.tr("celebrate.trophy.title", T.tr(trophy.nameKey())),
                    T.tr("celebrate.trophy.text", trophy.rung(), o.member().getName()),
                    rewardEl, onShowTrophies);
        } else if (o.newChoreForMember()) {
            burst = "medium";
            showDialog(service, o, onUndo, "🌟", T.tr("celebrate.newChore.title"),
                    T.tr("celebrate.newChore.text", chore, o.member().getName()),
                    nearTrophy(credits, o), null);
        } else if (o.doneTodayAfter() >= 3) {
            // Third-or-more chore of the day: the praise (and the confetti) escalates.
            burst = "fire";
            showDialog(service, o, onUndo, "🔥", T.tr("celebrate.third.title"),
                    T.tr("celebrate.third.text", chore, o.member().getName(), o.doneTodayAfter()),
                    nearTrophy(credits, o), null);
        } else if (o.doneTodayAfter() == 2) {
            burst = "medium";
            showDialog(service, o, onUndo, "💪", T.tr("celebrate.second.title"),
                    T.tr("celebrate.second.text", chore, o.member().getName()),
                    nearTrophy(credits, o), null);
        } else {
            burst = "small";
            showDialog(service, o, onUndo, o.task() != null ? o.task().getEmoji() : "🙋",
                    T.tr("celebrate.nice.title"),
                    T.tr("celebrate.nice.text", chore, o.member().getName()),
                    nearTrophy(credits, o), null);
        }

        fireConfetti(burst);

        // Credit rewards earned by this completion. A trophy's own 💎 is in its dialog.
        CreditService.Award award = o.award();
        // One sound per completion: a spree outranks whatever the chore itself earned.
        effect(award.spreeDays() != null ? "big" : burst);
        if (award.spreeDays() != null) {
            fireConfetti("big");
            Notification n = Toasts.show(
                    T.tr("celebrate.spree", award.spreeDays(), award.spreeCredits()),
                    4500);
            n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        } else if (award.choreCredits() > 0) {
            Notification n = Toasts.show(
                    T.tr("celebrate.credits", award.choreCredits()),
                    2500);
            n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        }
    }

    /**
     * "Just 2 more to 🥈 Silver (+20 💎)" when the next trophy is {@link #TROPHY_NEAR} or fewer
     * chores away; null otherwise. Anticipation lives in the dialog the member is already
     * looking at, rather than as one more element on the board.
     */
    private static Paragraph nearTrophy(CreditService credits, CompleteOutcome o) {
        Trophy next = Trophy.next(o.memberTotal()).orElse(null);
        if (next == null || next.rung() - o.memberTotal() > TROPHY_NEAR) {
            return null;
        }
        long toGo = next.rung() - o.memberTotal();
        int reward = credits.trophyRewards(o.member().getHomeCode()).getOrDefault(next, 0);
        String name = T.tr(next.nameKey());
        Paragraph p = new Paragraph(reward > 0
                ? T.tr("celebrate.trophy.nearReward", toGo, next.emoji(), name, reward)
                : T.tr("celebrate.trophy.near", toGo, next.emoji(), name));
        p.addClassName("trophy-near");
        return p;
    }

    /**
     * Trophies the member owns but was not shown when they were earned — an admin approved or
     * logged the chore, or they predate trophies. One dialog however many there are: a single
     * trophy reads like its own unlock, several are listed by name.
     */
    static void showUnseenTrophies(List<Trophy> trophies, String memberName,
                                   Runnable onShowTrophies) {
        Trophy best = trophies.getLast();
        Dialog dialog = new Dialog();
        dialog.setModal(true);
        dialog.addClassName("celebrate-dialog");

        Div emojiEl = new Div();
        emojiEl.setText(trophies.stream().map(Trophy::emoji).collect(Collectors.joining()));
        emojiEl.addClassNames("celebrate-emoji", "trophy-shine");
        if (trophies.size() > 1) {
            emojiEl.addClassName("several");
        }
        Div titleEl = new Div();
        titleEl.setText(trophies.size() == 1
                ? T.tr("celebrate.trophy.title", T.tr(best.nameKey()))
                : T.tr("celebrate.trophies.title", trophies.size()));
        titleEl.addClassName("celebrate-title");
        Paragraph textEl = new Paragraph(trophies.size() == 1
                ? T.tr("celebrate.trophy.text", best.rung(), memberName)
                : T.tr("celebrate.trophies.text", memberName, trophies.stream()
                        .map(t -> T.tr(t.nameKey())).collect(Collectors.joining(", "))));
        textEl.addClassName("celebrate-text");

        Button see = new Button(T.tr("trophy.see"), e -> {
            dialog.close();
            onShowTrophies.run();
        });
        see.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
        Button ok = new Button(T.tr("common.done"), e -> dialog.close());
        ok.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        VerticalLayout layout = new VerticalLayout(emojiEl, titleEl, textEl, see, ok);
        layout.setAlignItems(FlexComponent.Alignment.CENTER);
        layout.setPadding(false);
        layout.setSpacing(true);
        dialog.add(layout);
        fireConfetti("big");
        effect("big");
        dialog.open();
    }

    /**
     * @param named true on the shared device, where "you" is nobody: the two messages that say
     *              "you" name the member the chore was tapped for instead
     */
    static void showBlocked(CompleteOutcome o, boolean named) {
        String chore = o.task().getName();
        String who = o.member().getName();
        String msg = switch (o.blockReason()) {
            case STREAK -> named ? T.tr("blocked.named.streak", chore, o.newStreak(), who)
                    : T.tr("blocked.streak", chore, o.newStreak());
            case BOOKED -> T.tr("blocked.booked", chore);
            case NOT_DUE -> T.tr("blocked.notDue", chore);
            case NOT_ASSIGNED -> named ? T.tr("blocked.named.notAssigned", chore, who)
                    : T.tr("blocked.notAssigned", chore);
            case OUTSIDE_HOURS -> T.tr("blocked.outsideHours", chore,
                    com.homechores.domain.TimeWindows.displayCompact(o.task().getAvailableWindows()));
            case OUT_OF_SEASON -> T.tr("blocked.outOfSeason", chore,
                    seasonWords(o.task().getSeasons()));
            default -> T.tr("blocked.generic", chore);
        };
        Notification n = Notification.show(msg, 4000, Notification.Position.MIDDLE);
        n.addThemeVariants(NotificationVariant.LUMO_CONTRAST);
        effect("blocked");
    }

    /** Localized season names for a sentence. The card badge uses emoji; prose uses words. */
    private static String seasonWords(String seasons) {
        return Seasons.toList(seasons).stream()
                .map(s -> T.tr("season." + s.name().toLowerCase(Locale.ROOT)))
                .collect(Collectors.joining(", "));
    }

    /**
     * @param extra a line under the text (a trophy's reward, or how near the next one is), or null
     * @param onShowTrophies when set, the dialog offers the trophy shelf (a trophy's unlock only)
     */
    private static void showDialog(ChoreService service, CompleteOutcome o, Runnable onUndo,
                                   String emoji, String title, String text, Component extra,
                                   Runnable onShowTrophies) {
        Dialog dialog = new Dialog();
        dialog.setModal(true);
        dialog.addClassName("celebrate-dialog");

        Div emojiEl = new Div();
        emojiEl.setText(emoji);
        emojiEl.addClassName("celebrate-emoji");
        if (o.trophy() != null) {
            emojiEl.addClassName("trophy-shine");
        }

        Div titleEl = new Div();
        titleEl.setText(title);
        titleEl.addClassName("celebrate-title");

        Paragraph textEl = new Paragraph(text);
        textEl.addClassName("celebrate-text");

        Paragraph hint = new Paragraph(T.tr("celebrate.howWasIt"));
        hint.addClassName("feedback-hint");

        Div feedbackRow = new Div();
        feedbackRow.addClassName("feedback-row");
        for (Feedback fb : Feedback.values()) {
            Div btn = new Div();
            btn.setText(fb.getEmoji());
            btn.addClassName("feedback-btn");
            btn.getElement().setAttribute("title", T.tr(feedbackKey(fb)));
            btn.addClickListener(e -> {
                if (o.completionId() != null) {
                    service.setFeedback(o.completionId(), fb);
                }
                feedbackRow.getChildren().forEach(c -> c.getElement().getClassList().remove("selected"));
                btn.addClassName("selected");
            });
            feedbackRow.add(btn);
        }

        Button ok = new Button(T.tr("common.done"), e -> dialog.close());
        ok.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);

        // Quietly styled: undoing is the rare case, and it shouldn't compete with the
        // celebration — but it has to be right here, where the mistake is noticed.
        Button undo = new Button(T.tr("undo.wasntMe"), e -> {
            dialog.close();
            onUndo.run();
        });
        // Tertiary, not small: quiet in colour, but still a finger-sized target (see .undo-btn).
        undo.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        undo.addClassName("undo-btn");

        VerticalLayout layout = new VerticalLayout(emojiEl, titleEl, textEl);
        if (extra != null) {
            layout.add(extra);
        }
        layout.add(hint, feedbackRow, ok);
        if (onShowTrophies != null) {
            Button see = new Button(T.tr("trophy.see"), e -> {
                dialog.close();
                onShowTrophies.run();
            });
            see.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            layout.add(see);
        }
        layout.add(undo);
        layout.setAlignItems(FlexComponent.Alignment.CENTER);
        layout.setPadding(false);
        layout.setSpacing(true);
        dialog.add(layout);
        dialog.open();
    }

    static String feedbackKey(Feedback fb) {
        return switch (fb) {
            case HATE -> "feedback.hate";
            case OK -> "feedback.ok";
            case LOVE -> "feedback.love";
        };
    }
}
