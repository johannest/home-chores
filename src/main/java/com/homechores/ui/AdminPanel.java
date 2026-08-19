package com.homechores.ui;

import com.homechores.domain.ChoreTask;
import com.homechores.domain.Completion;
import com.homechores.domain.DivisionStyle;
import com.homechores.domain.Home;
import com.homechores.domain.Member;
import com.homechores.domain.RejoinRequest;
import com.homechores.domain.Season;
import com.homechores.domain.Seasons;
import com.homechores.domain.SpreeTier;
import com.homechores.domain.TimeWindows;
import com.homechores.service.BackupService;
import com.homechores.service.ChoreService;
import com.homechores.service.CreditService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.server.StreamResource;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Admin-only tools: approvals, settings, members, chores CRUD, backup/restore. */
class AdminPanel extends VerticalLayout {

    private final ChoreService service;
    private final CreditService creditService;
    private final BackupService backup;
    private final String homeCode;
    private final Long memberId;

    /** How far back the correction list reaches — enough for "that was this morning". */
    private static final int RECENT_LIMIT = 15;

    /** Stable identity for a card's open/closed preference — never the translated title, which
     *  changes with the language switcher and now embeds a count. */
    private enum Section {
        REJOINS, HELP, APPROVALS, LOG_FOR, RECENT,
        MEMBERS, CHORES, REWARDS, SETTINGS, BACKUP, DANGER
    }

    /**
     * Cards the admin has opened or closed by hand; absent means "use the smart default".
     *
     * <p>This is what makes collapsing viable at all. Every settings toggle writes through
     * immediately, which bumps HomeState and rebuilds all eleven sections from scratch — without a
     * remembered choice, a card would slam shut under the admin's finger on every tap.
     */
    private final EnumMap<Section, Boolean> openState = new EnumMap<>(Section.class);

    AdminPanel(ChoreService service, CreditService creditService, BackupService backup,
               String homeCode, Long memberId) {
        this.service = service;
        this.creditService = creditService;
        this.backup = backup;
        this.homeCode = homeCode;
        this.memberId = memberId;
        setPadding(false);
        setSpacing(false);
        setWidthFull();
    }

    void refresh() {
        removeAll();
        rejoinsSection().ifPresent(this::add);
        otherHelpSection().ifPresent(this::add);
        add(approvalsSection());
        add(logForSection());
        add(recentSection());
        // Daily work above, weekly work here, set-once configuration below it: settings alone is
        // ~950px of controls an admin touches at setup and then never again.
        add(membersSection());
        add(choresSection());
        add(rewardsSection());
        add(settingsSection());
        add(backupSection());
        add(dangerSection());
    }

    /**
     * One collapsible admin card. {@code defaultOpen} is the smart default; a tap on the summary
     * overrides it for the rest of the visit.
     */
    private Details section(Section id, String title, boolean defaultOpen) {
        Details s = new Details();
        s.addClassName("admin-section");
        s.setSummaryText(title);
        // Set the state BEFORE registering the listener. Details reports opened-change from an
        // element property listener, which fires for server-side writes too — so a programmatic
        // setOpened during a rebuild looks exactly like a tap.
        Boolean chosen = openState.get(id);
        s.setOpened(chosen != null ? chosen : defaultOpen);
        s.addOpenedChangeListener(e -> {
            if (e.isFromClient()) {
                openState.put(id, e.isOpened());
            }
        });
        return s;
    }

    // ---- Rejoin requests ----------------------------------------------------

    /**
     * Devices asking to sign back in as an existing member after clearing their browser
     * storage. Rendered only when something is waiting — most families never see it.
     */
    private Optional<Details> rejoinsSection() {
        var requests = service.pendingRejoins(homeCode);
        if (requests.isEmpty()) {
            return Optional.empty();
        }
        Details s = section(Section.REJOINS, T.tr("admin.rejoins", requests.size()), true);
        s.addClassName("admin-urgent");
        Span info = new Span(T.tr("admin.rejoins.info"));
        info.addClassName("sub");
        s.add(info);

        for (RejoinRequest r : requests) {
            String name = service.findMember(r.getMemberId()).map(Member::getName).orElse("?");

            Div box = new Div();
            Div line = new Div();
            line.setText(T.tr("admin.rejoins.row", name));
            line.getStyle().set("font-weight", "600");
            Span sub = new Span(ago(r.getRequestedAt()));
            sub.addClassName("sub");
            box.add(line, sub);
            box.addClassName("grow");

            Button approve = new Button(VaadinIcon.CHECK.create(), e -> {
                service.decideRejoin(r.getId(), memberId, true);
                toast(T.tr("admin.rejoins.approved", name));
                refresh();
            });
            approve.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS,
                    ButtonVariant.LUMO_SMALL);
            // Icon-only, so it needs a name of its own — as the Other help buttons already have.
            approve.setAriaLabel(T.tr("admin.approve"));
            Button reject = new Button(VaadinIcon.CLOSE.create(), e -> {
                service.decideRejoin(r.getId(), memberId, false);
                refresh();
            });
            reject.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
            reject.setAriaLabel(T.tr("admin.reject"));

            Div row = new Div(box, approve, reject);
            row.addClassName("list-row");
            s.add(row);
        }
        return Optional.of(s);
    }

    // ---- Other help ---------------------------------------------------------

    /**
     * Help a member did that no chore covers, in their own words. Kept apart from the
     * ordinary approval queue because the decision is a different one: there is no chore to
     * read a reward off, and a kind of help that keeps coming back probably deserves a card
     * of its own on the board.
     */
    private Optional<Details> otherHelpSection() {
        var waiting = service.pendingOtherHelp(homeCode);
        if (waiting.isEmpty()) {
            return Optional.empty();
        }
        Details s = section(Section.HELP, T.tr("admin.help", waiting.size()), true);
        s.addClassName("admin-urgent");
        Span info = new Span(T.tr("admin.help.info"));
        info.addClassName("sub");
        s.add(info);

        for (Completion c : waiting) {
            String member = service.findMember(c.getMemberId()).map(Member::getName).orElse("?");

            Div box = new Div();
            Div line = new Div();
            line.setText("🙋 " + c.getNote());
            line.getStyle().set("font-weight", "600");
            Span sub = new Span(member + "  ·  " + ago(c.getDoneAt()));
            sub.addClassName("sub");
            box.add(line, sub);
            box.addClassName("grow");

            Button accept = new Button(VaadinIcon.CHECK.create(), e -> acceptHelpDialog(c, member));
            accept.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS,
                    ButtonVariant.LUMO_SMALL);
            accept.setAriaLabel(T.tr("admin.help.accept"));
            Button decline = new Button(VaadinIcon.CLOSE.create(), e -> {
                service.reject(c.getId(), memberId);
                toast(T.tr("admin.help.declined"));
                refresh();
            });
            decline.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
            decline.setAriaLabel(T.tr("admin.help.decline"));

            Div row = new Div(box, accept, decline);
            row.addClassName("list-row");
            s.add(row);
        }
        return Optional.of(s);
    }

    /** Accepting: name the reward (there's no chore to carry one), then count it. */
    private void acceptHelpDialog(Completion help, String member) {
        Dialog d = new Dialog();
        d.setHeaderTitle(T.tr("admin.help.accept.title"));
        d.setWidth("min(90vw, 24em)");

        Span what = new Span("🙋 " + help.getNote());
        what.getStyle().set("font-weight", "600");
        Span who = new Span(T.tr("admin.help.accept.by", member));
        who.addClassName("sub");

        IntegerField credits = new IntegerField(T.tr("admin.help.accept.credits"));
        credits.setMin(0);
        credits.setValue(0);
        credits.setStepButtonsVisible(true);
        credits.setWidthFull();
        credits.setHelperText(T.tr("admin.help.accept.credits.helper"));

        Button ok = new Button(T.tr("admin.help.accept.confirm"), e -> {
            int amount = credits.getValue() == null ? 0 : Math.max(0, credits.getValue());
            service.approve(help.getId(), memberId, amount);
            d.close();
            toast(T.tr("admin.help.accepted", member));
            // The board is the point: help that happens often should become a card anyone
            // can tap, so the same thing never has to be typed out and reviewed again.
            promoteToChoreDialog(help.getNote(), amount);
        });
        ok.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);

        VerticalLayout body = new VerticalLayout(what, who, credits);
        body.setPadding(false);
        d.add(body);
        d.getFooter().add(new Button(T.tr("common.cancel"), e -> d.close()), ok);
        d.open();
    }

    /**
     * Offers to turn accepted help into a permanent chore. Prefilled from what the member
     * wrote, but editable — "took the neighbour's dog out because they were away" is a good
     * description of one evening and a poor name for a chore card.
     */
    private void promoteToChoreDialog(String description, int credits) {
        Dialog d = new Dialog();
        d.setHeaderTitle(T.tr("admin.help.promote.title"));
        d.setWidth("min(90vw, 24em)");

        Span question = new Span(T.tr("admin.help.promote.text"));

        TextField name = new TextField(T.tr("admin.chore.name"));
        name.setValue(shortName(description));
        name.setWidthFull();
        TextField emoji = new TextField(T.tr("admin.chore.emoji"));
        emoji.setWidthFull();
        emoji.setMaxLength(4);
        emoji.setPlaceholder("🙋");
        FrequencyField freq = new FrequencyField();
        IntegerField credit = new IntegerField(T.tr("admin.chore.credits"));
        credit.setMin(0);
        credit.setValue(Math.max(0, credits));
        credit.setStepButtonsVisible(true);
        credit.setWidthFull();
        credit.setHelperText(T.tr("admin.chore.credits.helper"));

        Button add = new Button(T.tr("admin.help.promote.add"), e -> {
            if (name.isEmpty()) {
                name.setInvalid(true);
                name.setErrorMessage(T.tr("admin.chore.nameRequired"));
                return;
            }
            // Neither hours nor seasons are asked for here: help that just happened is available
            // now by definition, and the admin can narrow it later from the chore editor.
            service.addTask(homeCode, name.getValue(), emoji.getValue(), freq.getIntervalDays(),
                    credit.getValue() == null ? 0 : Math.max(0, credit.getValue()), null);
            d.close();
            toast(T.tr("admin.help.promote.added", name.getValue().trim()));
            refresh();
        });
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button skip = new Button(T.tr("admin.help.promote.no"), e -> {
            d.close();
            refresh();
        });

        VerticalLayout body = new VerticalLayout(question, name, emoji, freq, credit);
        body.setPadding(false);
        d.add(body);
        d.getFooter().add(skip, add);
        d.addDialogCloseActionListener(e -> {
            d.close();
            refresh();
        });
        d.open();
    }

    /** A chore-card-sized name out of a free-text description: first line, first clause. */
    private static String shortName(String description) {
        if (description == null) {
            return "";
        }
        String s = description.trim();
        int cut = s.indexOf('\n');
        if (cut < 0) {
            cut = s.indexOf(',');
        }
        if (cut > 0) {
            s = s.substring(0, cut).trim();
        }
        return s.length() > 40 ? s.substring(0, 40).trim() : s;
    }

    // ---- Approvals ----------------------------------------------------------

    private Details approvalsSection() {
        var pending = service.pendingApprovals(homeCode);
        Details s = section(Section.APPROVALS, T.tr("admin.pending", pending.size()),
                !pending.isEmpty());
        if (!pending.isEmpty()) {
            s.addClassName("admin-urgent");
        }
        if (pending.isEmpty()) {
            Span none = new Span(T.tr("admin.pending.none"));
            none.addClassName("sub");
            s.add(none);
            return s;
        }
        for (Completion c : pending) {
            String member = service.findMember(c.getMemberId()).map(Member::getName).orElse("?");
            String chore = describe(c);

            Div info = new Div();
            Div line = new Div();
            line.setText(member + " — " + chore);
            line.getStyle().set("font-weight", "600");
            Span sub = new Span(ago(c.getDoneAt())
                    + (c.getFeedback() != null ? "  ·  " + c.getFeedback().getEmoji() : ""));
            sub.addClassName("sub");
            info.add(line, sub);
            info.addClassName("grow");

            Button approve = new Button(VaadinIcon.CHECK.create(), e -> {
                service.approve(c.getId(), memberId);
                refresh();
            });
            approve.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS,
                    ButtonVariant.LUMO_SMALL);
            // Icon-only, so it needs a name of its own — as the Other help buttons already have.
            approve.setAriaLabel(T.tr("admin.approve"));
            Button reject = new Button(VaadinIcon.CLOSE.create(), e -> {
                service.reject(c.getId(), memberId);
                refresh();
            });
            reject.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
            reject.setAriaLabel(T.tr("admin.reject"));

            Div row = new Div(info, approve, reject);
            row.addClassName("list-row");
            s.add(row);
        }
        return s;
    }

    // ---- Logging a chore for someone else -----------------------------------

    /**
     * Recording a chore on another member's behalf: the child with no phone, or the one who
     * did it and forgot to tap. The counterpart of the unmark list below — one puts a chore
     * into somebody's history, the other takes one out.
     */
    private Details logForSection() {
        Details s = section(Section.LOG_FOR, T.tr("admin.logFor"), false);
        List<Member> others = service.membersOf(homeCode).stream()
                .filter(m -> !m.getId().equals(memberId))
                .toList();
        List<ChoreTask> chores = service.tasksOf(homeCode);
        if (others.isEmpty() || chores.isEmpty()) {
            Span none = new Span(T.tr(others.isEmpty()
                    ? "admin.logFor.noOthers" : "admin.logFor.noChores"));
            none.addClassName("sub");
            s.add(none);
            return s;
        }
        Span info = new Span(T.tr("admin.logFor.info"));
        info.addClassName("sub");

        Select<Member> who = new Select<>();
        who.setLabel(T.tr("admin.logFor.member"));
        who.setWidthFull();
        who.setItems(others);
        who.setItemLabelGenerator(Member::getName);
        who.setValue(others.get(0));

        Select<ChoreTask> which = new Select<>();
        which.setLabel(T.tr("admin.logFor.chore"));
        which.setWidthFull();
        which.setItems(chores);
        which.setItemLabelGenerator(t -> t.getEmoji() + " " + t.getName());
        which.setValue(chores.get(0));

        Button log = new Button(T.tr("admin.logFor.action"), VaadinIcon.CHECK.create(), e -> {
            Member target = who.getValue();
            ChoreTask chore = which.getValue();
            if (target == null || chore == null) {
                return;
            }
            service.completeFor(chore.getId(), target.getId(), memberId);
            toast(T.tr("admin.logFor.done", chore.getName(), target.getName()));
            refresh();
        });
        log.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        VerticalLayout body = new VerticalLayout(info, who, which, log);
        body.setPadding(false);
        body.setSpacing(true);
        body.setWidthFull();
        s.add(body);
        return s;
    }

    // ---- Recent activity (corrections) --------------------------------------

    /**
     * The last few completions, so an admin can unmark a chore that was tapped by mistake
     * — including one already approved, and long after the member's own undo window shut.
     */
    private Details recentSection() {
        var recent = service.recentCompletions(homeCode, RECENT_LIMIT);
        Details s = section(Section.RECENT, T.tr("admin.recent", recent.size()), false);
        if (recent.isEmpty()) {
            Span none = new Span(T.tr("admin.recent.none"));
            none.addClassName("sub");
            s.add(none);
            return s;
        }
        Span info = new Span(T.tr("admin.recent.info"));
        info.addClassName("sub");
        s.add(info);

        for (Completion c : recent) {
            String member = service.findMember(c.getMemberId()).map(Member::getName).orElse("?");
            String chore = describe(c);

            Div info2 = new Div();
            Div line = new Div();
            line.setText(member + " — " + chore);
            line.getStyle().set("font-weight", "600");
            String status = switch (c.getStatus()) {
                case PENDING -> "  ·  " + T.tr("admin.recent.pending");
                case REJECTED -> "  ·  " + T.tr("admin.recent.rejected");
                default -> "";
            };
            Span sub = new Span(ago(c.getDoneAt()) + status
                    + (c.getFeedback() != null ? "  ·  " + c.getFeedback().getEmoji() : ""));
            sub.addClassName("sub");
            info2.add(line, sub);
            info2.addClassName("grow");

            Button unmark = new Button(T.tr("admin.recent.unmark"), VaadinIcon.ARROW_BACKWARD.create(),
                    e -> confirm(T.tr("admin.recent.unmark.title", chore),
                            T.tr("admin.recent.unmark.text", member), () -> {
                                service.deleteCompletion(c.getId());
                                toast(T.tr("admin.recent.unmarked", chore, member));
                                refresh();
                            }));
            unmark.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

            Div row = new Div(info2, unmark);
            row.addClassName("list-row");
            s.add(row);
        }
        return s;
    }

    // ---- Settings -----------------------------------------------------------

    private Details settingsSection() {
        Home home = service.findHome(homeCode).orElseThrow();
        Details s = section(Section.SETTINGS, T.tr("admin.settings"), false);

        Checkbox approval = new Checkbox(T.tr("admin.requireApproval"));
        approval.setValue(home.isRequireApproval());
        approval.addValueChangeListener(e -> {
            Home h = service.findHome(homeCode).orElseThrow();
            h.setRequireApproval(e.getValue());
            service.saveHome(h);
        });

        Checkbox confirmTaps = new Checkbox(T.tr("admin.confirmCompletion"));
        confirmTaps.setValue(home.isConfirmCompletion());
        confirmTaps.addValueChangeListener(e -> {
            Home h = service.findHome(homeCode).orElseThrow();
            h.setConfirmCompletion(e.getValue());
            service.saveHome(h);
        });
        Span confirmHint = new Span(T.tr("admin.confirmCompletion.helper"));
        confirmHint.addClassName("sub");

        Checkbox otherHelp = new Checkbox(T.tr("admin.allowOtherHelp"));
        otherHelp.setValue(home.isAllowOtherHelp());
        otherHelp.addValueChangeListener(e -> {
            Home h = service.findHome(homeCode).orElseThrow();
            h.setAllowOtherHelp(e.getValue());
            service.saveHome(h);
        });
        Span otherHelpHint = new Span(T.tr("admin.allowOtherHelp.helper"));
        otherHelpHint.addClassName("sub");

        Checkbox rejoinGate = new Checkbox(T.tr("admin.approveRejoin"));
        rejoinGate.setValue(home.isApproveRejoin());
        rejoinGate.addValueChangeListener(e -> {
            Home h = service.findHome(homeCode).orElseThrow();
            h.setApproveRejoin(e.getValue());
            service.saveHome(h);
        });
        Span rejoinHint = new Span(T.tr("admin.approveRejoin.helper"));
        rejoinHint.addClassName("sub");

        Select<Integer> target = new Select<>();
        target.setLabel(T.tr("admin.dailyTarget"));
        target.setWidthFull();
        target.setItems(1, 2, 3);
        target.setValue(home.getDailyTargetPerMember());
        target.addValueChangeListener(e -> {
            if (e.getValue() != null) {
                Home h = service.findHome(homeCode).orElseThrow();
                h.setDailyTargetPerMember(e.getValue());
                service.saveHome(h);
            }
        });

        Select<DivisionStyle> style = new Select<>();
        style.setLabel(T.tr("admin.divisionStyle"));
        // Short option labels with the explanation underneath: the full sentences used to
        // be the option text, which truncated mid-word in the closed select on a phone.
        style.setHelperText(T.tr("admin.divisionStyle.helper"));
        style.setWidthFull();
        style.setItems(DivisionStyle.DEFAULT, DivisionStyle.ROTATING);
        style.setItemLabelGenerator(ds -> ds == DivisionStyle.ROTATING
                ? T.tr("admin.division.rotating")
                : T.tr("admin.division.default"));
        style.setValue(home.getDivisionStyle());
        style.addValueChangeListener(e -> {
            if (e.getValue() != null) {
                Home h = service.findHome(homeCode).orElseThrow();
                h.setDivisionStyle(e.getValue());
                service.saveHome(h);
            }
        });

        Checkbox enforced = new Checkbox(T.tr("admin.rotationEnforced"));
        enforced.setValue(home.isRotationEnforced());
        enforced.addValueChangeListener(e -> {
            Home h = service.findHome(homeCode).orElseThrow();
            h.setRotationEnforced(e.getValue());
            service.saveHome(h);
        });

        Select<Integer> bookingHours = new Select<>();
        bookingHours.setLabel(T.tr("admin.bookingHold"));
        bookingHours.setWidthFull();
        bookingHours.setItems(1, 2, 3, 4, 6, 8, 12, 24);
        bookingHours.setItemLabelGenerator(h -> T.tr(h == 1 ? "admin.hour" : "admin.hours", h));
        bookingHours.setValue(home.getBookingTimeoutHours());
        bookingHours.addValueChangeListener(e -> {
            if (e.getValue() != null) {
                Home h = service.findHome(homeCode).orElseThrow();
                h.setBookingTimeoutHours(e.getValue());
                service.saveHome(h);
            }
        });

        TextField homeName = new TextField(T.tr("admin.homeName"));
        homeName.setValue(home.getName());
        Button rename = new Button(T.tr("admin.saveName"), e -> {
            if (!homeName.isEmpty()) {
                Home h = service.findHome(homeCode).orElseThrow();
                h.setName(homeName.getValue().trim());
                service.saveHome(h);
                toast(T.tr("admin.nameUpdated"));
            }
        });
        HorizontalLayout nameRow = new HorizontalLayout(homeName, rename);
        nameRow.setAlignItems(FlexComponent.Alignment.END);
        nameRow.setWidthFull();
        nameRow.setFlexGrow(1, homeName);

        // Admin PIN
        Div pinLabel = new Div();
        pinLabel.setText(T.tr("admin.pinLabel"));
        pinLabel.addClassName("sub");
        Span pin = new Span(home.getAdminPin());
        pin.addClassName("pin-box");
        Button changePin = new Button(T.tr("admin.changePin"), e -> changePinDialog());
        changePin.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        HorizontalLayout pinRow = new HorizontalLayout(pin, changePin);
        pinRow.setAlignItems(FlexComponent.Alignment.CENTER);

        VerticalLayout body = new VerticalLayout(confirmTaps, confirmHint, approval,
                otherHelp, otherHelpHint, style, enforced, bookingHours, target,
                rejoinGate, rejoinHint, nameRow, pinLabel, pinRow);
        body.setPadding(false);
        body.setSpacing(true);
        body.setWidthFull();
        s.add(body);
        return s;
    }

    private void changePinDialog() {
        Dialog d = new Dialog();
        d.setHeaderTitle(T.tr("admin.changePin.title"));
        TextField pin = new TextField(T.tr("admin.changePin.new"));
        pin.setMaxLength(4);
        pin.setPattern("\\d{4}");
        pin.setHelperText(T.tr("admin.changePin.numbers"));
        Button save = new Button(T.tr("common.save"), e -> {
            String v = pin.getValue();
            if (v == null || !v.matches("\\d{4}")) {
                pin.setInvalid(true);
                pin.setErrorMessage(T.tr("admin.changePin.error"));
                return;
            }
            Home h = service.findHome(homeCode).orElseThrow();
            h.setAdminPin(v);
            service.saveHome(h);
            d.close();
            toast(T.tr("admin.pinUpdated"));
            refresh();
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        d.add(pin);
        d.getFooter().add(new Button(T.tr("common.cancel"), e -> d.close()), save);
        d.open();
    }

    // ---- Members ------------------------------------------------------------

    private Details membersSection() {
        List<Member> members = service.membersOf(homeCode);
        Details s = section(Section.MEMBERS, T.tr("admin.members", members.size()), false);
        for (Member m : members) {
            Div dot = new Div();
            dot.addClassName("dot");
            dot.getStyle().set("background", m.getColor());
            dot.setText(m.getName().isEmpty() ? "?" : m.getName().substring(0, 1).toUpperCase());

            Div info = new Div();
            Div line = new Div();
            line.setText(m.getName() + (m.isAdmin() ? " 👑" : ""));
            line.getStyle().set("font-weight", "600");
            Span sub = new Span(T.tr(m.getId().equals(memberId) ? "admin.you" : "admin.member"));
            sub.addClassName("sub");
            info.add(line, sub);
            info.addClassName("grow");

            Button role = new Button(T.tr(m.isAdmin() ? "admin.demote" : "admin.makeAdmin"), e -> {
                if (!service.setMemberAdmin(m.getId(), !m.isAdmin())) {
                    toastError(T.tr("admin.lastAdmin"));
                }
                refresh();
            });
            role.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

            Button rename = new Button(VaadinIcon.EDIT.create(), e -> renameMemberDialog(m));
            rename.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

            Button remove = new Button(VaadinIcon.TRASH.create(), e -> confirmRemoveMember(m));
            remove.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY,
                    ButtonVariant.LUMO_SMALL);

            Div row = new Div(dot, info, role, rename, remove);
            row.addClassName("list-row");
            s.add(row);
        }
        return s;
    }

    private void renameMemberDialog(Member m) {
        Dialog d = new Dialog();
        d.setHeaderTitle(T.tr("admin.renameMember"));
        TextField name = new TextField(T.tr("admin.name"));
        name.setValue(m.getName());
        Button save = new Button(T.tr("common.save"), e -> {
            if (!name.isEmpty()) {
                service.renameMember(m.getId(), name.getValue());
                d.close();
                refresh();
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        d.add(name);
        d.getFooter().add(new Button(T.tr("common.cancel"), e -> d.close()), save);
        d.open();
    }

    private void confirmRemoveMember(Member m) {
        confirm(T.tr("admin.removeMember.title", m.getName()),
                T.tr("admin.removeMember.text"), () -> {
                    if (!service.removeMember(m.getId())) {
                        toastError(T.tr("admin.lastAdmin"));
                        return;
                    }
                    if (m.getId().equals(memberId)) {
                        DeviceIdentity.forget();
                        SessionContext.signOut();
                        getUI().ifPresent(ui -> ui.navigate(LandingView.class));
                    } else {
                        refresh();
                    }
                });
    }

    // ---- Chores -------------------------------------------------------------

    private Details choresSection() {
        List<ChoreTask> chores = service.tasksOf(homeCode);
        Details s = section(Section.CHORES, T.tr("admin.chores", chores.size()), false);
        Button add = new Button(T.tr("admin.addChore"), VaadinIcon.PLUS.create(), e -> choreDialog(null));
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
        s.add(add);

        for (ChoreTask t : chores) {
            Div emoji = new Div();
            emoji.setText(t.getEmoji());
            emoji.getStyle().set("font-size", "1.4rem");

            Div name = new Div();
            name.setText(t.getName());
            name.getStyle().set("font-weight", "600");
            name.addClassName("grow");

            Button edit = new Button(VaadinIcon.EDIT.create(), e -> choreDialog(t));
            edit.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            Button del = new Button(VaadinIcon.TRASH.create(), e ->
                    confirm(T.tr("admin.deleteChore.title", t.getName()),
                            T.tr("admin.deleteChore.text"), () -> {
                                service.deleteTask(t.getId());
                                refresh();
                            }));
            del.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY,
                    ButtonVariant.LUMO_SMALL);

            Div row = new Div(emoji, name, edit, del);
            row.addClassName("list-row");
            s.add(row);
        }
        return s;
    }

    private void choreDialog(ChoreTask existing) {
        Dialog d = new Dialog();
        d.setHeaderTitle(T.tr(existing == null ? "admin.chore.title.add" : "admin.chore.title.edit"));
        d.setWidth("min(90vw, 24em)");
        TextField name = new TextField(T.tr("admin.chore.name"));
        name.setWidthFull();
        TextField emoji = new TextField(T.tr("admin.chore.emoji"));
        emoji.setWidthFull();
        emoji.setMaxLength(4);
        FrequencyField freq = new FrequencyField();
        IntegerField credit = new IntegerField(T.tr("admin.chore.credits"));
        credit.setStepButtonsVisible(true);
        credit.setMin(0);
        credit.setValue(0);
        credit.setWidthFull();
        credit.setHelperText(T.tr("admin.chore.credits.helper"));
        TextField hours = new TextField(T.tr("admin.chore.hours"));
        hours.setWidthFull();
        hours.setPlaceholder("08:00-10:00, 18:00-22:00");
        hours.setHelperText(T.tr("admin.chore.hours.helper"));
        // Time of day above, time of year below: the two availability constraints sit together so
        // the difference between them is visible rather than explained.
        CheckboxGroup<Season> seasons = new CheckboxGroup<>(T.tr("admin.chore.seasons"));
        seasons.setItems(Season.values());
        seasons.setItemLabelGenerator(x -> T.tr("season." + x.name().toLowerCase(Locale.ROOT)));
        seasons.setHelperText(T.tr("admin.chore.seasons.helper"));
        seasons.setWidthFull();
        if (existing != null) {
            name.setValue(existing.getName());
            emoji.setValue(existing.getEmoji());
            freq.setIntervalDays(existing.getIntervalDays());
            credit.setValue(existing.getCreditValue());
            hours.setValue(existing.getAvailableWindows() == null
                    ? "" : existing.getAvailableWindows());
            seasons.setValue(Seasons.asSet(existing.getSeasons()));
        }
        Button save = new Button(T.tr("common.save"), e -> {
            if (name.isEmpty()) {
                name.setInvalid(true);
                name.setErrorMessage(T.tr("admin.chore.nameRequired"));
                return;
            }
            String windows;
            try {
                windows = TimeWindows.normalize(hours.getValue());
            } catch (IllegalArgumentException ex) {
                hours.setInvalid(true);
                hours.setErrorMessage(T.tr("admin.chore.hours.error"));
                return;
            }
            int days = freq.getIntervalDays();
            int credits = credit.getValue() == null ? 0 : Math.max(0, credit.getValue());
            // The Set overload cannot fail — the values come straight from the enum.
            String seasonValue = Seasons.normalize(seasons.getValue());
            if (existing == null) {
                service.addTask(homeCode, name.getValue(), emoji.getValue(), days, credits, windows,
                        seasonValue);
            } else {
                service.updateTask(existing.getId(), name.getValue(), emoji.getValue(), days,
                        credits, windows, seasonValue);
            }
            d.close();
            refresh();
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        VerticalLayout body = new VerticalLayout(name, emoji, freq, credit, hours, seasons);
        body.setPadding(false);
        d.add(body);
        d.getFooter().add(new Button(T.tr("common.cancel"), e -> d.close()), save);
        d.open();
    }

    // ---- Rewards (credits) --------------------------------------------------

    private Details rewardsSection() {
        Details s = section(Section.REWARDS, T.tr("admin.rewards"), false);

        Div tiersTitle = new Div();
        tiersTitle.setText(T.tr("admin.spree.title"));
        tiersTitle.addClassName("sub");
        s.add(tiersTitle);

        for (SpreeTier t : creditService.tiersOf(homeCode)) {
            Div label = new Div();
            label.setText(T.tr("admin.spree.tier", t.getDays(), t.getCredits()));
            label.addClassName("grow");
            Button del = new Button(VaadinIcon.TRASH.create(), e -> {
                creditService.deleteTier(t.getId());
                refresh();
            });
            del.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY,
                    ButtonVariant.LUMO_SMALL);
            Div row = new Div(label, del);
            row.addClassName("list-row");
            s.add(row);
        }

        IntegerField days = new IntegerField(T.tr("admin.spree.days"));
        days.setMin(1);
        days.setStepButtonsVisible(true);
        days.setWidth("7.5em");
        IntegerField cr = new IntegerField(T.tr("admin.spree.credits"));
        cr.setMin(1);
        cr.setStepButtonsVisible(true);
        cr.setWidth("7.5em");
        Button addTier = new Button(T.tr("admin.spree.add"), e -> {
            if (days.getValue() != null && cr.getValue() != null
                    && days.getValue() > 0 && cr.getValue() > 0) {
                creditService.addTier(homeCode, days.getValue(), cr.getValue());
                refresh();
            }
        });
        addTier.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
        HorizontalLayout addRow = new HorizontalLayout(days, cr, addTier);
        addRow.setAlignItems(FlexComponent.Alignment.END);
        s.add(addRow);

        Div balTitle = new Div();
        balTitle.setText(T.tr("admin.balances.title"));
        balTitle.addClassName("sub");
        balTitle.getStyle().set("margin-top", "var(--lumo-space-m)");
        s.add(balTitle);

        for (Member m : service.membersOf(homeCode)) {
            int bal = creditService.balance(m.getId());
            Div info = new Div();
            Div line = new Div();
            line.setText(m.getName());
            line.getStyle().set("font-weight", "600");
            Span sub = new Span(T.tr("admin.balance", bal));
            sub.addClassName("sub");
            info.add(line, sub);
            info.addClassName("grow");
            Button redeem = new Button(T.tr("admin.redeem"), e -> redeemDialog(m, bal));
            redeem.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            redeem.setEnabled(bal > 0);
            Div row = new Div(info, redeem);
            row.addClassName("list-row");
            s.add(row);
        }
        return s;
    }

    private void redeemDialog(Member m, int balance) {
        Dialog d = new Dialog();
        d.setHeaderTitle(T.tr("admin.redeem.title", m.getName()));
        IntegerField amount = new IntegerField(T.tr("admin.redeem.amount"));
        amount.setMin(1);
        amount.setMax(balance);
        amount.setValue(balance);
        amount.setStepButtonsVisible(true);
        TextField note = new TextField(T.tr("admin.redeem.note"));
        note.setWidthFull();
        Button ok = new Button(T.tr("admin.redeem"), e -> {
            Integer amt = amount.getValue();
            if (amt == null || amt <= 0) {
                amount.setInvalid(true);
                return;
            }
            if (!creditService.redeem(homeCode, m.getId(), amt, note.getValue(), memberId)) {
                toastError(T.tr("admin.redeem.tooMuch"));
                return;
            }
            d.close();
            toast(T.tr("admin.redeem.done", amt, m.getName()));
            refresh();
        });
        ok.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        VerticalLayout body = new VerticalLayout(amount, note);
        body.setPadding(false);
        d.add(body);
        d.getFooter().add(new Button(T.tr("common.cancel"), e -> d.close()), ok);
        d.open();
    }

    // ---- Backup / restore ---------------------------------------------------

    private Details backupSection() {
        Details s = section(Section.BACKUP, T.tr("admin.backup"), false);
        Span info = new Span(T.tr("admin.backup.info"));
        info.addClassName("sub");

        StreamResource res = new StreamResource("home-chores-" + homeCode + "-backup.json",
                () -> new ByteArrayInputStream(backup.export(homeCode).getBytes(StandardCharsets.UTF_8)));
        Anchor download = new Anchor(res, "");
        download.getElement().setAttribute("download", true);
        Button downloadBtn = new Button(T.tr("admin.backup.download"), VaadinIcon.DOWNLOAD.create());
        downloadBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        download.add(downloadBtn);

        MemoryBuffer buffer = new MemoryBuffer();
        Upload upload = new Upload(buffer);
        upload.setMaxFiles(1);
        upload.setAcceptedFileTypes("application/json", ".json");
        upload.setDropLabel(new Span(T.tr("admin.backup.restore")));
        upload.addSucceededListener(e -> {
            try {
                byte[] bytes = buffer.getInputStream().readAllBytes();
                confirmRestore(bytes);
            } catch (Exception ex) {
                toastError(T.tr("admin.restore.readError", ex.getMessage()));
            }
            upload.clearFileList();
        });

        VerticalLayout body = new VerticalLayout(info, download, upload);
        body.setPadding(false);
        body.setSpacing(true);
        s.add(body);
        return s;
    }

    private void confirmRestore(byte[] bytes) {
        confirm(T.tr("admin.restore.title"), T.tr("admin.restore.text"), () -> {
                    try {
                        var result = backup.restore(bytes);
                        // Restoring remaps every member id, so the stored identity is stale.
                        DeviceIdentity.forget();
                        SessionContext.signOut();
                        getUI().ifPresent(ui -> {
                            ui.navigate(LandingView.class);
                            Notification n = Notification.show(
                                    T.tr("admin.restore.done", result.homeCode(),
                                            result.members(), result.tasks()),
                                    5000, Notification.Position.TOP_CENTER);
                            n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                        });
                    } catch (IllegalArgumentException ex) {
                        toastError(ex.getMessage());
                    }
                });
    }

    // ---- Danger zone --------------------------------------------------------

    /** Deleting the whole home. Irreversible, so it sits apart from everything else. */
    private Details dangerSection() {
        Details s = section(Section.DANGER, T.tr("admin.danger"), false);
        s.addClassName("danger-section");
        Span info = new Span(T.tr("admin.deleteHome.info"));
        info.addClassName("sub");
        Button delete = new Button(T.tr("admin.deleteHome"), VaadinIcon.TRASH.create(),
                e -> deleteHomeDialog());
        delete.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_PRIMARY);

        VerticalLayout body = new VerticalLayout(info, delete);
        body.setPadding(false);
        body.setSpacing(true);
        s.add(body);
        return s;
    }

    /**
     * Confirms the wipe by making the admin type the home code — the one action here that
     * nothing can undo, so a plain "are you sure?" isn't enough.
     */
    private void deleteHomeDialog() {
        Home home = service.findHome(homeCode).orElseThrow();
        Dialog d = new Dialog();
        d.setHeaderTitle(T.tr("admin.deleteHome.title", home.getName()));
        d.setWidth("min(90vw, 26em)");

        Span warning = new Span(T.tr("admin.deleteHome.warning",
                service.membersOf(homeCode).size(), service.tasksOf(homeCode).size()));
        Span backupHint = new Span(T.tr("admin.deleteHome.backupHint"));
        backupHint.addClassName("sub");

        TextField confirm = new TextField(T.tr("admin.deleteHome.confirmLabel", homeCode));
        confirm.setWidthFull();
        confirm.setPlaceholder(homeCode);

        Button wipe = new Button(T.tr("admin.deleteHome.confirm"), e -> {
            if (!homeCode.equalsIgnoreCase(confirm.getValue() == null
                    ? "" : confirm.getValue().trim())) {
                confirm.setInvalid(true);
                confirm.setErrorMessage(T.tr("admin.deleteHome.mismatch", homeCode));
                return;
            }
            d.close();
            // Leave the board *before* wiping it. The revision bump lands once the delete
            // commits and shows every remaining device out with a generic "an admin deleted
            // this home"; stepping off first means this device is no longer listening, so
            // the admin who pressed the button gets their own confirmation instead.
            String name = home.getName();
            UI ui = UI.getCurrent();
            DeviceIdentity.forget();
            SessionContext.signOut();
            ui.navigate(LandingView.class);
            if (service.deleteHome(homeCode)) {
                Notification n = Notification.show(T.tr("admin.deleteHome.done", name),
                        5000, Notification.Position.TOP_CENTER);
                n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } else {
                toastError(T.tr("admin.deleteHome.failed"));
            }
        });
        wipe.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_PRIMARY);

        VerticalLayout body = new VerticalLayout(warning, backupHint, confirm);
        body.setPadding(false);
        d.add(body);
        d.getFooter().add(new Button(T.tr("common.cancel"), e -> d.close()), wipe);
        d.open();
    }

    // ---- Small helpers ------------------------------------------------------

    /** How a completion reads in the admin lists: the chore, or the helper's own words. */
    private String describe(Completion c) {
        if (c.isOtherHelp()) {
            return "🙋 " + c.getNote();
        }
        return service.tasksOf(homeCode).stream()
                .filter(t -> t.getId().equals(c.getTaskId()))
                .findFirst().map(t -> t.getEmoji() + " " + t.getName()).orElse("?");
    }

    private void confirm(String title, String text, Runnable onConfirm) {
        Dialog d = new Dialog();
        d.setHeaderTitle(title);
        d.add(new Span(text));
        Button yes = new Button(T.tr("common.confirm"), e -> {
            d.close();
            onConfirm.run();
        });
        yes.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        d.getFooter().add(new Button(T.tr("common.cancel"), e -> d.close()), yes);
        d.open();
    }

    private void toast(String msg) {
        Notification n = Notification.show(msg, 2500, Notification.Position.TOP_CENTER);
        n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    private void toastError(String msg) {
        Notification n = Notification.show(msg, 3500, Notification.Position.TOP_CENTER);
        n.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }

    private static String ago(Instant when) {
        Duration d = Duration.between(when, Instant.now());
        long mins = d.toMinutes();
        if (mins < 1) {
            return T.tr("admin.ago.justNow");
        }
        if (mins < 60) {
            return T.tr("admin.ago.min", mins);
        }
        long hours = d.toHours();
        if (hours < 24) {
            return T.tr("admin.ago.hours", hours);
        }
        return T.tr("admin.ago.days", d.toDays());
    }
}
