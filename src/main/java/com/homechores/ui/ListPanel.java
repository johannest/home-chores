package com.homechores.ui;

import com.homechores.domain.CustomList;
import com.homechores.domain.InputLimits;
import com.homechores.domain.ListItem;
import com.homechores.domain.ListKind;
import com.homechores.domain.ListReminder;
import com.homechores.domain.Member;
import com.homechores.service.ChoreService;
import com.homechores.service.ListItemService;
import com.homechores.service.ListItemService.ListSlot;
import com.homechores.service.ListReminderService;
import com.homechores.service.PushReminderService;
import com.homechores.service.WebPushSender;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.menubar.MenuBarVariant;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The shared family lists: groceries, to-dos, the week's dinners and any lists the home named
 * itself (one tab each, after the built-in three, then a "+" tab). Anyone adds, anyone ticks,
 * anyone plans, anyone makes or deletes a list. Nothing here is a chore — ticking a line records no completion and earns nothing
 * (see {@link ListItemService}).
 */
class ListPanel extends VerticalLayout {

    private final ListItemService lists;
    private final ChoreService service;
    private final ListReminderService reminders;
    private final PushReminderService push;
    private final WebPushSender pushSender;
    private final String homeCode;
    private final Long memberId;

    private final Div content = new Div();
    private final Div body = new Div();

    /**
     * Which list is showing, and what is half-typed in the add box. Plain fields, the same trick
     * {@code StatsPanel} uses for its sub-tab: {@code HomeView} holds this panel across every
     * {@code HomeState} rebuild, so another phone adding "milk" mid-word does not lose this one's
     * "brea". Neither is the family's business, so neither ever bumps {@code HomeState}.
     */
    /** Null until the first render, which opens the first list in the admin's order. */
    private ListKind kind;
    /** The custom list showing, or null for the built-in {@link #kind}. Its lines are to-dos. */
    private Long listId;
    private String draft = "";
    /** Half-typed dinner slots, by day — the same survival trick as {@link #draft}. */
    private final Map<LocalDate, String> dinnerDrafts = new HashMap<>();

    /** Dinner slots shown: today plus a full week, so a Sunday plan reaches next Sunday. */
    static final int WINDOW_DAYS = 8;

    /** Whether the panel has drawn once — a list vanishing is only news after that. */
    private boolean rendered;

    /** Focus the add box the first time the panel shows — not on every live update, which would
     *  yank the keyboard up on a phone that was only reading the list. */
    private boolean focusOnRender = true;

    ListPanel(ListItemService lists, ChoreService service, ListReminderService reminders,
              PushReminderService push, WebPushSender pushSender, String homeCode, Long memberId) {
        this.lists = lists;
        this.service = service;
        this.reminders = reminders;
        this.push = push;
        this.pushSender = pushSender;
        this.homeCode = homeCode;
        this.memberId = memberId;
        setPadding(false);
        setSpacing(false);
        setWidthFull();
        content.setWidthFull();
        body.setWidthFull();
        add(content);
    }

    void refresh() {
        content.removeAll();
        boolean hadCustom = listId != null;
        // The admin's order, without the built-ins switched off (see ListItemService.listSlots).
        List<ListSlot> slots = lists.visibleSlots(homeCode);
        int selected = indexOfSelection(slots);
        if (selected < 0 && !slots.isEmpty()) {
            // What this phone was showing is gone: another phone deleted the list, or an admin
            // switched it off. Land on To-do (or the first list, if To-do is off), and say why — unless this is the
            // very first render, where there was nothing to lose.
            if (rendered) {
                toast(T.tr(hadCustom ? "list.custom.gone" : "list.hidden"), false);
            }
            ListSlot to = rendered ? fallback(slots) : slots.get(0);
            select(to);
            draft = "";
            selected = slots.indexOf(to);
        }
        rendered = true;

        List<Tab> all = new ArrayList<>();
        for (ListSlot slot : slots) {
            Tab t = new Tab(slotLabel(slot));
            if (!slot.isBuiltIn()) {
                t.addClassName("custom-list-tab");
            }
            all.add(t);
        }
        Tab plus = new Tab(VaadinIcon.PLUS.create());
        plus.addClassName("list-new-tab");
        plus.setAriaLabel(T.tr("list.custom.newAria"));
        plus.setTooltipText(T.tr("list.custom.new"));
        all.add(plus);

        Tabs tabs = new Tabs(all.toArray(Tab[]::new));
        tabs.addClassName("list-kind-tabs");
        tabs.setWidthFull();
        int initial = selected;
        if (initial >= 0) {
            tabs.setSelectedIndex(initial);
        } else {
            tabs.setSelectedTab(null); // nothing to show; "+" is not a place to stay on either
        }
        tabs.addSelectedChangeListener(e -> {
            int i = tabs.getSelectedIndex();
            if (i == all.size() - 1) {
                // "+" is an action, not a place: go back to where we were and ask for a name.
                if (e.getPreviousTab() != null) {
                    tabs.setSelectedTab(e.getPreviousTab());
                } else if (initial >= 0) {
                    tabs.setSelectedIndex(initial);
                } else {
                    tabs.setSelectedTab(null);
                }
                newListDialog();
                return;
            }
            if (i >= 0) {
                select(slots.get(i));
            }
            draft = "";
            dinnerDrafts.clear();
            renderBody();
        });
        content.add(tabs, body);
        if (slots.isEmpty()) {
            body.removeAll();
            Paragraph none = new Paragraph(T.tr("list.noneShown"));
            none.addClassName("feedback-hint");
            body.add(none);
            return;
        }
        renderBody();
    }

    /** A built-in list's tab label, or a custom list's own name. Shared with the admin card. */
    static String slotLabel(ListSlot slot) {
        if (!slot.isBuiltIn()) {
            return slot.custom().getName();
        }
        return T.tr(switch (slot.kind()) {
            case GROCERY -> "list.tab.groceries";
            case TODO -> "list.tab.todo";
            case DINNER -> "list.tab.dinner";
        });
    }

    private int indexOfSelection(List<ListSlot> slots) {
        for (int i = 0; i < slots.size(); i++) {
            ListSlot slot = slots.get(i);
            if (listId == null ? slot.isBuiltIn() && kind != null && slot.kind() == kind
                    : listId.equals(slot.listId())) {
                return i;
            }
        }
        return -1;
    }

    /** Where to land when the shown list goes: To-do — custom lists are to-do lists too — or,
     *  if an admin switched To-do off, the first list there is. {@code slots} is non-empty. */
    private static ListSlot fallback(List<ListSlot> slots) {
        return slots.stream().filter(sl -> sl.isBuiltIn() && sl.kind() == ListKind.TODO)
                .findFirst().orElse(slots.get(0));
    }

    private void select(ListSlot slot) {
        kind = slot.kind();
        listId = slot.listId();
    }

    /** Selects the given custom list (null: built-in {@code kind}) and redraws. For tests too. */
    void show(ListKind kind, Long listId) {
        this.kind = listId == null ? kind : ListKind.TODO;
        this.listId = listId;
        draft = "";
        refresh();
    }

    private void newListDialog() {
        if (lists.customLists(homeCode).size() >= InputLimits.CUSTOM_LISTS) {
            toast(T.tr("list.custom.limit", InputLimits.CUSTOM_LISTS), true);
            return;
        }
        new TextPromptDialog(T.tr("list.custom.new"), T.tr("list.custom.name"), "",
                InputLimits.LIST_NAME, T.tr("list.custom.create"), name -> {
                    var made = lists.createList(homeCode, memberId, name);
                    made.ifPresent(l -> {
                        focusOnRender = true;
                        show(ListKind.TODO, l.getId());
                    });
                    return made.isPresent();
                }).open();
    }

    /** The custom list's own strip: its name and a ⋯ with Rename and Delete. */
    private Div customListHead(CustomList list) {
        Span name = new Span(list.getName());
        name.addClassName("custom-list-name");

        MenuBar menu = new MenuBar();
        menu.addClassName("custom-list-menu");
        menu.addThemeVariants(MenuBarVariant.LUMO_SMALL, MenuBarVariant.LUMO_TERTIARY_INLINE);
        MenuItem root = menu.addItem(VaadinIcon.ELLIPSIS_DOTS_V.create());
        root.setAriaLabel(T.tr("list.custom.menu"));
        root.getSubMenu().addItem(T.tr("list.custom.rename"), e ->
                new TextPromptDialog(T.tr("list.custom.rename"), T.tr("list.custom.name"),
                        list.getName(), InputLimits.LIST_NAME, T.tr("common.save"), text -> {
                            boolean ok = lists.renameList(homeCode, list.getId(), memberId, text);
                            if (ok) {
                                refresh();
                            }
                            return ok;
                        }).open());
        root.getSubMenu().addItem(T.tr("list.custom.delete"), e -> confirmDeleteList(list))
                .addClassName("danger-item");

        Div head = new Div(name, menu);
        head.addClassName("custom-list-head");
        return head;
    }

    /** No undo for a whole list, so this one does ask first — unlike deleting a single line. */
    private void confirmDeleteList(CustomList list) {
        Dialog d = new Dialog();
        d.setHeaderTitle(T.tr("list.custom.delete"));
        d.setWidth("min(90vw, 24em)");
        d.addClassName("delete-list-dialog");
        d.add(new Paragraph(T.tr("list.custom.deleteConfirm", list.getName())));
        Button delete = new Button(T.tr("common.delete"), e -> {
            lists.deleteList(homeCode, list.getId(), memberId);
            d.close();
            List<ListSlot> left = lists.visibleSlots(homeCode);
            ListSlot to = left.isEmpty() ? ListSlot.builtIn(ListKind.TODO) : fallback(left);
            show(to.kind(), to.listId());
        });
        delete.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        d.getFooter().add(new Button(T.tr("common.cancel"), e -> d.close()), delete);
        d.open();
    }

    private static void toast(String text, boolean warn) {
        Notification n = Notification.show(text, 3500, Notification.Position.TOP_CENTER);
        if (warn) {
            n.addThemeVariants(NotificationVariant.LUMO_WARNING);
        }
    }

    private void renderBody() {
        body.removeAll();
        if (kind == ListKind.DINNER) {
            renderDinnerWeek();
            return;
        }
        if (listId != null) {
            lists.findList(homeCode, listId).ifPresent(l -> body.add(customListHead(l)));
        }
        body.add(addRow());

        Map<Long, String> names = memberNames();
        List<ListItem> open = lists.openItems(homeCode, kind, listId);
        List<ListItem> done = lists.doneItems(homeCode, kind, listId);
        // One query for the home's reminders, never one per line — and none at all when push is
        // not configured, in which case the ⏰ is not offered either (see ChoresPanel for why).
        Map<Long, ListReminder> armed = pushSender.isEnabled() ? reminders.forHome(homeCode) : Map.of();

        if (open.isEmpty() && done.isEmpty()) {
            Paragraph empty = new Paragraph(
                    T.tr(listId != null ? "list.empty.custom"
                            : "list.empty." + kind.name().toLowerCase(Locale.ROOT)));
            empty.addClassName("feedback-hint");
            body.add(empty);
            return;
        }

        Div openList = new Div();
        openList.addClassName("shared-list");
        for (ListItem item : open) {
            openList.add(itemRow(item, names, armed.get(item.getId())));
        }
        body.add(openList);

        if (!done.isEmpty()) {
            Span title = new Span(T.tr("list.done", done.size()));
            title.addClassName("list-done-title");
            Button clear = new Button(T.tr("list.clearDone"), e -> {
                lists.clearDone(homeCode, kind, listId);
                refresh();
            });
            clear.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            Div head = new Div(title, clear);
            head.addClassName("list-done-head");
            body.add(head);

            Div doneList = new Div();
            doneList.addClassName("shared-list");
            doneList.addClassName("done");
            for (ListItem item : done) {
                doneList.add(itemRow(item, names, null));
            }
            body.add(doneList);

            Span hint = new Span(T.tr("list.doneHint"));
            hint.addClassName("sub");
            body.add(hint);
        }
    }

    private Map<Long, String> memberNames() {
        Map<Long, String> names = new HashMap<>();
        for (Member m : service.membersOf(homeCode)) {
            names.put(m.getId(), m.getName());
        }
        return names;
    }

    // ---- Dinner week ----------------------------------------------------------

    /**
     * Eight rows from today — today plus a full week — in the member's own zone: the window
     * slides by itself, so every day reveals a fresh empty slot. Eight rather than seven so that
     * planning next week on a Sunday still reaches next Sunday. No add row and no done section
     * — a day is set, changed or cleared, never ticked.
     */
    private void renderDinnerWeek() {
        ZoneId zone = SessionContext.timeZone();
        LocalDate today = LocalDate.now(zone);
        UI ui = UI.getCurrent();
        Locale locale = ui == null ? Locale.ENGLISH : ui.getLocale();
        Map<LocalDate, ListItem> set = lists.dinners(homeCode, today, today.plusDays(WINDOW_DAYS - 1));
        Map<Long, String> names = memberNames();

        Div week = new Div();
        week.addClassName("shared-list");
        week.addClassName("dinner-week");
        for (int i = 0; i < WINDOW_DAYS; i++) {
            LocalDate day = today.plusDays(i);
            week.add(dinnerRow(day, i == 0, set.get(day), names, locale));
        }
        body.add(week);
    }

    /** "Wednesday 16.9." — the weekday in the UI language (Charts uses the same recipe), then the date. */
    static String dayLabel(LocalDate day, Locale locale) {
        // STANDALONE, not FULL: in Finnish the format-context form is the essive ("keskiviikkona",
        // "on Wednesday"), which reads oddly as a heading; standalone gives "keskiviikko".
        String weekday = day.getDayOfWeek().getDisplayName(TextStyle.FULL_STANDALONE, locale);
        // Finnish and Swedish weekday names come back lowercase; this is a row heading.
        weekday = weekday.substring(0, 1).toUpperCase(locale) + weekday.substring(1);
        return weekday + " " + DateTimeFormatter.ofPattern("d.M.").format(day);
    }

    private Div dinnerRow(LocalDate day, boolean isToday, ListItem item, Map<Long, String> names,
                          Locale locale) {
        String label = dayLabel(day, locale);
        Div dayEl = new Div();
        dayEl.setText(label);
        dayEl.addClassName("dinner-day");

        TextField field = new TextField();
        field.setPlaceholder(T.tr("list.dinner.placeholder"));
        field.setAriaLabel(label);
        field.setMaxLength(InputLimits.LIST_ITEM);
        field.setClearButtonVisible(true);
        field.setValueChangeMode(ValueChangeMode.EAGER);
        field.setValue(dinnerDrafts.getOrDefault(day, item == null ? "" : item.getText()));
        field.addValueChangeListener(e -> dinnerDrafts.put(day, e.getValue()));

        Runnable save = () -> {
            lists.setDinner(homeCode, day, memberId, field.getValue());
            dinnerDrafts.remove(day);
            refresh();
        };
        field.addKeyDownListener(Key.ENTER, e -> save.run());
        Button saveBtn = new Button(VaadinIcon.CHECK.create(), e -> save.run());
        saveBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL,
                ButtonVariant.LUMO_ICON);
        saveBtn.setAriaLabel(T.tr("list.dinner.save"));
        Div fieldLine = new Div(field, saveBtn);
        fieldLine.addClassName("dinner-field");

        Div row = new Div(dayEl, fieldLine);
        row.addClassName("list-row");
        row.addClassName("dinner-row");
        if (isToday) {
            row.addClassName("today");
        }
        if (item != null && item.getCreatedByMemberId() != null) {
            Span sub = new Span(T.tr("list.dinner.setBy",
                    names.getOrDefault(item.getCreatedByMemberId(), "?"), T.ago(item.getCreatedAt())));
            sub.addClassName("sub");
            row.add(sub);
        }
        return row;
    }

    /** Quick entry: type, Enter (or the button), and the box is ready for the next line. */
    private Div addRow() {
        TextField input = new TextField();
        input.setPlaceholder(T.tr("list.add.placeholder"));
        input.setAriaLabel(T.tr("list.add.placeholder"));
        input.setMaxLength(InputLimits.LIST_ITEM);
        input.setClearButtonVisible(true);
        input.setValueChangeMode(ValueChangeMode.EAGER);
        input.setValue(draft);
        input.addValueChangeListener(e -> draft = e.getValue());

        Runnable submit = () -> {
            if (lists.add(homeCode, kind, listId, memberId, input.getValue()).isPresent()) {
                draft = "";
                focusOnRender = true;
                refresh();
            } else {
                input.focus();
            }
        };
        // keydown, not keypress: the keypress event is deprecated and some keyboards and
        // automation never raise it for Enter, whereas keydown is universal.
        input.addKeyDownListener(Key.ENTER, e -> submit.run());
        Button add = new Button(T.tr("list.add"), e -> submit.run());
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);

        if (focusOnRender) {
            input.focus();
            focusOnRender = false;
        }
        Div row = new Div(input, add);
        row.addClassName("list-add-row");
        return row;
    }

    private Div itemRow(ListItem item, Map<Long, String> names, ListReminder reminder) {
        Checkbox tick = new Checkbox();
        tick.setValue(item.isDone());
        tick.setAriaLabel(item.getText());
        tick.addValueChangeListener(e -> {
            // Only a person's tap: programmatic values during a rebuild are not decisions.
            if (e.isFromClient()) {
                lists.setDone(item.getId(), memberId, e.getValue());
                refresh();
            }
        });

        Div text = new Div();
        text.setText(item.getText());
        text.addClassName("list-text");
        // Tapping the words edits them — the one place a thumb already lands on a line.
        text.addClickListener(e -> editDialog(item));
        Div info = new Div(text);
        info.addClassName("grow");
        if (item.isDone()) {
            text.addClassName("done");
            String who = names.getOrDefault(item.getDoneByMemberId(), "?");
            Span sub = new Span(T.tr("list.tickedBy", who, T.ago(item.getDoneAt())));
            sub.addClassName("sub");
            info.add(sub);
        }
        Runnable openReminder = () -> new ListReminderDialog(reminders, lists, push, pushSender,
                memberId, homeCode, item, reminder, names, this::refresh).open();
        if (reminder != null) {
            // Absolute, never "in 2h": the list only redraws when something in the home changes.
            Span when = new Span(ListReminderDialog.badge(reminder));
            when.addClassName("sub");
            when.addClassName("list-reminder");
            if (reminder.isFired()) {
                when.addClassName("fired");
            }
            // The badge is the reminder: tapping it changes or cancels it, same as the bell.
            when.addClickListener(e -> openReminder.run());
            info.add(when);
        }

        Div tools = new Div();
        tools.addClassName("row-tools");
        // Only on open lines and only when push is configured, matching the chore card's ⏰.
        if (!item.isDone() && pushSender.isEnabled()) {
            Button remind = new Button(VaadinIcon.BELL_O.create(), e -> openReminder.run());
            remind.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL,
                    ButtonVariant.LUMO_ICON);
            remind.addClassName("list-remind-btn");
            if (reminder != null) {
                remind.addClassName("armed");
                remind.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            }
            remind.setAriaLabel(T.tr("listReminder.aria"));
            tools.add(remind);
        }

        Runnable delete = () -> {
            lists.delete(item.getId(), memberId);
            offerUndo(item);
            refresh();
        };
        Button remove = new Button(VaadinIcon.TRASH.create(), e -> delete.run());
        remove.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL,
                ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_ERROR);
        remove.setAriaLabel(T.tr("list.delete"));
        tools.add(remove);

        // The row is two layers: the face people see, and behind it a tray that a left swipe
        // uncovers (list-swipe.js moves the face; these are ordinary server-side buttons). The
        // tray repeats actions the face already offers, so it is hidden from assistive tech and
        // the tab order — a duplicate, not a second way that only fingers can reach.
        Button swipeEdit = new Button(VaadinIcon.PENCIL.create(), e -> editDialog(item));
        swipeEdit.addClassName("swipe-edit");
        swipeEdit.setText(T.tr("list.edit"));
        Button swipeDelete = new Button(VaadinIcon.TRASH.create(), e -> delete.run());
        swipeDelete.addClassName("swipe-delete");
        swipeDelete.setText(T.tr("list.delete"));
        for (Button b : List.of(swipeEdit, swipeDelete)) {
            b.setTabIndex(-1);
        }
        Div tray = new Div(swipeEdit, swipeDelete);
        tray.addClassName("swipe-actions");
        tray.getElement().setAttribute("aria-hidden", "true");

        Div face = new Div(tick, info, tools);
        face.addClassName("swipe-face");

        Div row = new Div(tray, face);
        row.addClassName("list-row");
        row.addClassName("list-item");
        if (item.isDone()) {
            row.addClassName("done");
        }
        return row;
    }

    private void editDialog(ListItem item) {
        new TextPromptDialog(T.tr("list.edit.title"), null, item.getText(),
                InputLimits.LIST_ITEM, T.tr("common.save"), text -> {
                    boolean ok = lists.rename(item.getId(), memberId, text);
                    if (ok) {
                        refresh();
                    }
                    return ok;
                }).open();
    }

    /**
     * One tap deletes a line, and the trash sits at the row's edge where a thumb lands. Rather
     * than a confirm dialog in front of every delete, the safety net comes after: "Removed X —
     * Undo" for a few seconds. Undo adds the line back as a fresh open item (the original row is
     * gone), which is what someone who deleted by mistake wants.
     */
    private void offerUndo(ListItem item) {
        Notification n = new Notification();
        n.setPosition(Notification.Position.TOP_CENTER);
        n.setDuration(6000);
        Span text = new Span(T.tr("list.deleted", item.getText()));
        Button undo = new Button(T.tr("undo.action"), e -> {
            lists.add(homeCode, item.getKind(), item.getListId(), memberId, item.getText());
            n.close();
            refresh();
        });
        undo.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        undo.addClassName("undo-btn");
        HorizontalLayout row = new HorizontalLayout(text, undo);
        row.setAlignItems(FlexComponent.Alignment.CENTER);
        n.add(row);
        n.open();
    }
}
