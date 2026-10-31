package com.homechores.ui;

import com.homechores.domain.Member;
import com.homechores.domain.Routine;
import com.homechores.domain.RoutineDone;
import com.homechores.service.ChoreService;
import com.homechores.service.RoutineService;
import com.homechores.service.WebPushSender;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Routines (§4.4f), drawn two ways from one row recipe:
 * <ul>
 *   <li>{@link Mode#ALL}, the Lists panel's Routines tab: every routine of the home — everyone's,
 *       since the family's week is everyone's business — with today's ones tickable, and an
 *       editor for the ones the viewer may change (their own, or all of them for an admin).
 *   <li>{@link Mode#TODAY}, the strip at the top of the Chores tab: only what occurs today and
 *       concerns the viewer, tickable, nothing else. Hidden when there is nothing, so a home that
 *       never makes a routine sees the board it always had.
 * </ul>
 * The shared device lists and ticks but edits nothing: it has no member to own or administer.
 */
class RoutinesPanel extends Div {

    enum Mode { ALL, TODAY }

    private final RoutineService routines;
    private final ChoreService service;
    private final WebPushSender pushSender;
    private final Viewer viewer;
    private final Runnable onChanged;

    RoutinesPanel(Mode mode, RoutineService routines, ChoreService service, WebPushSender pushSender,
                  Viewer viewer, Runnable onChanged) {
        this.routines = routines;
        this.service = service;
        this.pushSender = pushSender;
        this.viewer = viewer;
        this.onChanged = onChanged;
        setWidthFull();
        if (mode == Mode.TODAY) {
            addClassName("routines-today");
            renderToday();
        } else {
            addClassName("routines-panel");
            render();
        }
    }

    private void renderToday() {
        String homeCode = viewer.homeCode();
        List<RoutineService.Today> today = routines.today(homeCode, viewer.memberId());
        setVisible(!today.isEmpty());
        if (today.isEmpty()) {
            return;
        }
        Map<Long, String> names = new HashMap<>();
        service.membersOf(homeCode).forEach(m -> names.put(m.getId(), m.getName()));
        Div title = new Div(T.tr("routine.todayTitle"));
        title.addClassName("section-label");
        Div list = new Div();
        list.addClassName("shared-list");
        for (RoutineService.Today t : today) {
            Routine r = t.routine();
            String sub = r.getTime() == null ? T.tr("routine.anyTime") : RoutineFormat.time(r);
            if (r.getForMemberId() != null && !r.getForMemberId().equals(viewer.memberId())) {
                sub += " · " + names.getOrDefault(r.getForMemberId(), "?"); // the device's view
            }
            list.add(row(r, true, t.done(), sub, names, null));
        }
        add(title, list);
    }

    private void render() {
        String homeCode = viewer.homeCode();
        List<Member> family = service.membersOf(homeCode);
        Map<Long, String> names = new HashMap<>();
        family.forEach(m -> names.put(m.getId(), m.getName()));
        boolean admin = !viewer.isDevice() && family.stream()
                .anyMatch(m -> m.getId().equals(viewer.memberId()) && m.isAdmin());

        if (!viewer.isDevice()) {
            Button add = new Button(T.tr("routine.add"), VaadinIcon.PLUS.create(), e ->
                    new RoutineDialog(routines, homeCode, viewer.memberId(), admin, family,
                            pushSender.isEnabled(), null, onChanged).open());
            add.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
            add.addClassName("routine-add");
            add(add);
        }

        List<Routine> all = routines.routinesOf(homeCode, viewer.memberId());
        if (all.isEmpty()) {
            Paragraph empty = new Paragraph(T.tr("routine.empty"));
            empty.addClassName("feedback-hint");
            add(empty);
            return;
        }
        LocalDate today = routines.today(homeCode);
        Map<Long, RoutineDone> ticks = routines.doneToday(homeCode);
        Locale locale = UI.getCurrent() == null ? Locale.ENGLISH : UI.getCurrent().getLocale();
        Div list = new Div();
        list.addClassName("shared-list");
        for (Routine r : all) {
            boolean editable = admin || (viewer.memberId() != null
                    && viewer.memberId().equals(r.getForMemberId()));
            Runnable edit = editable ? () -> new RoutineDialog(routines, homeCode, viewer.memberId(),
                    admin, family, pushSender.isEnabled(), r, onChanged).open() : null;
            list.add(row(r, r.occursOn(today), ticks.get(r.getId()),
                    RoutineFormat.schedule(r, locale, names), names, edit));
        }
        add(list);
    }

    /**
     * One routine as a list row: a tick when it occurs today, its name, the schedule beneath, and
     * — when the viewer may change it — tapping the name or the pencil opens the editor.
     */
    private Div row(Routine r, boolean today, RoutineDone done, String sub, Map<Long, String> names,
            Runnable edit) {
        Div face = new Div();
        face.addClassName("routine-row");
        if (today) {
            face.add(tick(r, done));
        }
        Div text = new Div();
        text.setText((r.getEmoji() == null ? "" : r.getEmoji() + " ") + r.getName());
        text.addClassName("list-text");
        Div info = new Div(text);
        info.addClassName("grow");
        Span schedule = new Span(sub);
        schedule.addClassName("sub");
        info.add(schedule);
        if (done != null) {
            text.addClassName("done");
            Span by = new Span(T.tr("routine.doneBy", done.getDoneByMemberId() == null
                    ? T.tr("list.device") : names.getOrDefault(done.getDoneByMemberId(), "?")));
            by.addClassName("sub");
            info.add(by);
        }
        face.add(info);
        if (edit != null) {
            text.addClassName("editable");
            text.addClickListener(e -> edit.run());
            Button pencil = new Button(VaadinIcon.PENCIL.create(), e -> edit.run());
            pencil.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL,
                    ButtonVariant.LUMO_ICON);
            pencil.setAriaLabel(T.tr("routine.edit"));
            Div tools = new Div(pencil);
            tools.addClassName("row-tools");
            face.add(tools);
        }
        Div row = new Div(face);
        row.addClassNames("list-row", "routine-item");
        if (done != null) {
            row.addClassName("done");
        }
        return row;
    }

    private Checkbox tick(Routine r, RoutineDone done) {
        Checkbox tick = new Checkbox();
        tick.setValue(done != null);
        tick.setAriaLabel(r.getName());
        tick.addValueChangeListener(e -> {
            if (e.isFromClient()) {
                routines.setDone(viewer.homeCode(), r.getId(), viewer.author(), e.getValue());
                if (e.getValue()) {
                    Celebrations.effect("tick");
                }
                onChanged.run();
            }
        });
        return tick;
    }
}
