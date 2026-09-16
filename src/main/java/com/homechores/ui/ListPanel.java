package com.homechores.ui;

import com.homechores.domain.InputLimits;
import com.homechores.domain.ListItem;
import com.homechores.domain.ListKind;
import com.homechores.domain.Member;
import com.homechores.service.ChoreService;
import com.homechores.service.ListItemService;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The shared family lists: groceries, to-dos and the week's dinners. Anyone adds, anyone ticks,
 * anyone plans. Nothing here is a chore — ticking a line records no completion and earns nothing
 * (see {@link ListItemService}).
 */
class ListPanel extends VerticalLayout {

    private final ListItemService lists;
    private final ChoreService service;
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
    private ListKind kind = ListKind.GROCERY;
    private String draft = "";
    /** Half-typed dinner slots, by day — the same survival trick as {@link #draft}. */
    private final Map<LocalDate, String> dinnerDrafts = new HashMap<>();

    /** Dinner slots shown: today plus a full week, so a Sunday plan reaches next Sunday. */
    static final int WINDOW_DAYS = 8;

    /** Sub-tab order; the tabs and the kinds are matched by index. */
    private static final List<ListKind> KINDS = List.of(ListKind.GROCERY, ListKind.TODO, ListKind.DINNER);

    /** Focus the add box the first time the panel shows — not on every live update, which would
     *  yank the keyboard up on a phone that was only reading the list. */
    private boolean focusOnRender = true;

    ListPanel(ListItemService lists, ChoreService service, String homeCode, Long memberId) {
        this.lists = lists;
        this.service = service;
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
        Tabs tabs = new Tabs(new Tab(T.tr("list.tab.groceries")), new Tab(T.tr("list.tab.todo")),
                new Tab(T.tr("list.tab.dinner")));
        tabs.addClassName("list-kind-tabs");
        tabs.setWidthFull();
        tabs.setSelectedIndex(KINDS.indexOf(kind));
        tabs.addSelectedChangeListener(e -> {
            kind = KINDS.get(tabs.getSelectedIndex());
            draft = "";
            dinnerDrafts.clear();
            renderBody();
        });
        content.add(tabs, body);
        renderBody();
    }

    private void renderBody() {
        body.removeAll();
        if (kind == ListKind.DINNER) {
            renderDinnerWeek();
            return;
        }
        body.add(addRow());

        Map<Long, String> names = memberNames();
        List<ListItem> open = lists.openItems(homeCode, kind);
        List<ListItem> done = lists.doneItems(homeCode, kind);

        if (open.isEmpty() && done.isEmpty()) {
            Paragraph empty = new Paragraph(
                    T.tr("list.empty." + kind.name().toLowerCase(Locale.ROOT)));
            empty.addClassName("feedback-hint");
            body.add(empty);
            return;
        }

        Div openList = new Div();
        openList.addClassName("shared-list");
        for (ListItem item : open) {
            openList.add(itemRow(item, names));
        }
        body.add(openList);

        if (!done.isEmpty()) {
            Span title = new Span(T.tr("list.done", done.size()));
            title.addClassName("list-done-title");
            Button clear = new Button(T.tr("list.clearDone"), e -> {
                lists.clearDone(homeCode, kind);
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
                doneList.add(itemRow(item, names));
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
            if (lists.add(homeCode, kind, memberId, input.getValue()).isPresent()) {
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

    private Div itemRow(ListItem item, Map<Long, String> names) {
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
        Div info = new Div(text);
        info.addClassName("grow");
        if (item.isDone()) {
            text.addClassName("done");
            String who = names.getOrDefault(item.getDoneByMemberId(), "?");
            Span sub = new Span(T.tr("list.tickedBy", who, T.ago(item.getDoneAt())));
            sub.addClassName("sub");
            info.add(sub);
        }

        Button remove = new Button(VaadinIcon.TRASH.create(), e -> {
            lists.delete(item.getId(), memberId);
            refresh();
        });
        remove.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL,
                ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_ERROR);
        remove.setAriaLabel(T.tr("list.delete"));
        Div tools = new Div(remove);
        tools.addClassName("row-tools");

        Div row = new Div(tick, info, tools);
        row.addClassName("list-row");
        row.addClassName("list-item");
        if (item.isDone()) {
            row.addClassName("done");
        }
        return row;
    }
}
