package com.homechores.ui;

import com.homechores.domain.Feedback;
import com.homechores.service.ChoreService;
import com.homechores.service.ChoreService.CompleteOutcome;
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

/** Fires confetti and shows congratulation dialogs (with quick feedback capture). */
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
     * Handles the full post-completion experience for the acting member.
     *
     * @param onUndo taking the chore back, offered right here because this dialog is the
     *               moment a mis-tap is noticed
     */
    static void afterComplete(ChoreService service, CompleteOutcome o, Runnable onUndo) {
        String chore = o.task().getName();
        if (o.pending()) {
            showDialog(service, o, onUndo, "⏳", T.tr("celebrate.pending.title"),
                    T.tr("celebrate.pending.text", chore));
            return;
        }
        if (o.milestone() != null) {
            fireConfetti("big");
            showDialog(service, o, onUndo, "🏆", T.tr("celebrate.milestone.title", o.milestone()),
                    T.tr("celebrate.milestone.text", o.member().getName()));
        } else if (o.newChoreForMember()) {
            fireConfetti("medium");
            showDialog(service, o, onUndo, "🌟", T.tr("celebrate.newChore.title"),
                    T.tr("celebrate.newChore.text", chore, o.member().getName()));
        } else {
            fireConfetti("small");
            showDialog(service, o, onUndo, o.task().getEmoji(), T.tr("celebrate.nice.title"),
                    T.tr("celebrate.nice.text", chore, o.member().getName()));
        }

        // Credit rewards earned by this completion.
        if (o.spreeDays() != null) {
            fireConfetti("big");
            Notification n = Notification.show(
                    T.tr("celebrate.spree", o.spreeDays(), o.spreeCredits()),
                    4500, Notification.Position.TOP_CENTER);
            n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        } else if (o.creditsAwarded() > 0) {
            Notification n = Notification.show(
                    T.tr("celebrate.credits", o.creditsAwarded()),
                    2500, Notification.Position.TOP_CENTER);
            n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        }
    }

    static void showBlocked(CompleteOutcome o) {
        String chore = o.task().getName();
        String msg = switch (o.blockReason()) {
            case STREAK -> T.tr("blocked.streak", chore, ChoreService.MAX_IN_A_ROW);
            case BOOKED -> T.tr("blocked.booked", chore);
            case NOT_DUE -> T.tr("blocked.notDue", chore);
            case NOT_ASSIGNED -> T.tr("blocked.notAssigned", chore);
            case OUTSIDE_HOURS -> T.tr("blocked.outsideHours", chore,
                    com.homechores.domain.TimeWindows.displayCompact(o.task().getAvailableWindows()));
            default -> T.tr("blocked.generic", chore);
        };
        Notification n = Notification.show(msg, 4000, Notification.Position.MIDDLE);
        n.addThemeVariants(NotificationVariant.LUMO_CONTRAST);
    }

    private static void showDialog(ChoreService service, CompleteOutcome o, Runnable onUndo,
                                   String emoji, String title, String text) {
        Dialog dialog = new Dialog();
        dialog.setModal(true);
        dialog.addClassName("celebrate-dialog");

        Div emojiEl = new Div();
        emojiEl.setText(emoji);
        emojiEl.addClassName("celebrate-emoji");

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
        undo.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

        VerticalLayout layout =
                new VerticalLayout(emojiEl, titleEl, textEl, hint, feedbackRow, ok, undo);
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
