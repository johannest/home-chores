package com.homechores.ui;

import com.homechores.domain.ChoreTask;
import com.homechores.domain.Completion;
import com.homechores.domain.DivisionStyle;
import com.homechores.domain.Home;
import com.homechores.domain.Member;
import com.homechores.domain.SpreeTier;
import com.homechores.domain.TimeWindows;
import com.homechores.service.BackupService;
import com.homechores.service.ChoreService;
import com.homechores.service.CreditService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
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

/** Admin-only tools: approvals, settings, members, chores CRUD, backup/restore. */
class AdminPanel extends VerticalLayout {

    private final ChoreService service;
    private final CreditService creditService;
    private final BackupService backup;
    private final String homeCode;
    private final Long memberId;

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
        add(approvalsSection());
        add(settingsSection());
        add(membersSection());
        add(choresSection());
        add(rewardsSection());
        add(backupSection());
    }

    private Div section(String title) {
        Div s = new Div();
        s.addClassName("admin-section");
        s.add(new com.vaadin.flow.component.html.H3(title));
        return s;
    }

    // ---- Approvals ----------------------------------------------------------

    private Div approvalsSection() {
        var pending = service.pendingApprovals(homeCode);
        Div s = section(T.tr("admin.pending", pending.size()));
        if (pending.isEmpty()) {
            Span none = new Span(T.tr("admin.pending.none"));
            none.addClassName("sub");
            s.add(none);
            return s;
        }
        for (Completion c : pending) {
            String member = service.findMember(c.getMemberId()).map(Member::getName).orElse("?");
            String chore = service.tasksOf(homeCode).stream()
                    .filter(t -> t.getId().equals(c.getTaskId()))
                    .findFirst().map(t -> t.getEmoji() + " " + t.getName()).orElse("?");

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
            Button reject = new Button(VaadinIcon.CLOSE.create(), e -> {
                service.reject(c.getId(), memberId);
                refresh();
            });
            reject.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);

            Div row = new Div(info, approve, reject);
            row.addClassName("list-row");
            s.add(row);
        }
        return s;
    }

    // ---- Settings -----------------------------------------------------------

    private Div settingsSection() {
        Home home = service.findHome(homeCode).orElseThrow();
        Div s = section(T.tr("admin.settings"));

        Checkbox approval = new Checkbox(T.tr("admin.requireApproval"));
        approval.setValue(home.isRequireApproval());
        approval.addValueChangeListener(e -> {
            Home h = service.findHome(homeCode).orElseThrow();
            h.setRequireApproval(e.getValue());
            service.saveHome(h);
        });

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

        VerticalLayout body = new VerticalLayout(approval, style, enforced, bookingHours, target,
                nameRow, pinLabel, pinRow);
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

    private Div membersSection() {
        Div s = section(T.tr("admin.members"));
        for (Member m : service.membersOf(homeCode)) {
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
                        SessionContext.signOut();
                        getUI().ifPresent(ui -> ui.navigate(LandingView.class));
                    } else {
                        refresh();
                    }
                });
    }

    // ---- Chores -------------------------------------------------------------

    private Div choresSection() {
        Div s = section(T.tr("admin.chores"));
        Button add = new Button(T.tr("admin.addChore"), VaadinIcon.PLUS.create(), e -> choreDialog(null));
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
        s.add(add);

        for (ChoreTask t : service.tasksOf(homeCode)) {
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
        IntegerField interval = new IntegerField(T.tr("admin.chore.interval"));
        interval.setStepButtonsVisible(true);
        interval.setMin(0);
        interval.setValue(0);
        interval.setWidthFull();
        interval.setHelperText(T.tr("admin.chore.interval.helper"));
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
        if (existing != null) {
            name.setValue(existing.getName());
            emoji.setValue(existing.getEmoji());
            interval.setValue(existing.getIntervalDays());
            credit.setValue(existing.getCreditValue());
            hours.setValue(existing.getAvailableWindows() == null
                    ? "" : existing.getAvailableWindows());
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
            int days = interval.getValue() == null ? 0 : Math.max(0, interval.getValue());
            int credits = credit.getValue() == null ? 0 : Math.max(0, credit.getValue());
            if (existing == null) {
                service.addTask(homeCode, name.getValue(), emoji.getValue(), days, credits, windows);
            } else {
                service.updateTask(existing.getId(), name.getValue(), emoji.getValue(), days,
                        credits, windows);
            }
            d.close();
            refresh();
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        VerticalLayout body = new VerticalLayout(name, emoji, interval, credit, hours);
        body.setPadding(false);
        d.add(body);
        d.getFooter().add(new Button(T.tr("common.cancel"), e -> d.close()), save);
        d.open();
    }

    // ---- Rewards (credits) --------------------------------------------------

    private Div rewardsSection() {
        Div s = section(T.tr("admin.rewards"));

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

    private Div backupSection() {
        Div s = section(T.tr("admin.backup"));
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

    // ---- Small helpers ------------------------------------------------------

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
