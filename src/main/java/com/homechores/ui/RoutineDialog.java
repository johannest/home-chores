package com.homechores.ui;

import com.homechores.domain.InputLimits;
import com.homechores.domain.Member;
import com.homechores.domain.Routine;
import com.homechores.service.RoutineService;
import com.homechores.service.RoutineService.Spec;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.timepicker.TimePicker;
import java.time.DayOfWeek;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Makes or changes a {@link Routine} (§4.4f): name, emoji, the weekdays, every week or every
 * other week (and, for the latter, whether it starts this week or next), an optional time, and —
 * for an admin — whose it is. A member who is not an admin makes routines for themselves only,
 * so they get no "For" field; the service re-checks either way.
 */
class RoutineDialog extends Dialog {

    /** "For": a member, or nobody for the whole home. {@code Select} cannot carry a null item
     *  with its own label, so the home is a sentinel without a member. */
    private record Owner(Member member) {
    }

    private static final Owner EVERYONE = new Owner(null);

    RoutineDialog(RoutineService routines, String homeCode, Long memberId, boolean admin,
                  List<Member> family, boolean pushEnabled, Routine existing, Runnable onChanged) {
        addClassName("routine-dialog");
        setWidth("min(92vw, 26em)");
        setHeaderTitle(T.tr(existing == null ? "routine.new" : "routine.edit"));
        Locale locale = UI.getCurrent() == null ? Locale.ENGLISH : UI.getCurrent().getLocale();

        TextField name = new TextField(T.tr("routine.name"));
        name.setPlaceholder(T.tr("routine.name.placeholder"));
        name.setMaxLength(InputLimits.TASK_NAME);
        name.setWidthFull();
        TextField emoji = new TextField(T.tr("routine.emoji"));
        emoji.setMaxLength(InputLimits.EMOJI);
        emoji.addClassName("routine-emoji");
        Div nameRow = new Div(emoji, name);
        nameRow.addClassName("routine-name-row");

        CheckboxGroup<DayOfWeek> days = new CheckboxGroup<>(T.tr("routine.days"));
        days.setItems(DayOfWeek.values());
        days.setItemLabelGenerator(d -> RoutineFormat.dayName(d, locale));
        days.addClassName("routine-days");

        RadioButtonGroup<Integer> repeat = new RadioButtonGroup<>(T.tr("routine.repeat"));
        repeat.setItems(1, Routine.EVERY_OTHER_WEEK);
        repeat.setItemLabelGenerator(n -> T.tr(n == 1 ? "routine.repeat.weekly" : "routine.repeat.biweekly"));
        repeat.setValue(1);

        // Only for every other week, and only when the choice means something: on an existing
        // routine that stays every other week, leaving it alone keeps the weeks it falls on.
        RadioButtonGroup<Boolean> start = new RadioButtonGroup<>(T.tr("routine.start"));
        start.setItems(false, true);
        start.setItemLabelGenerator(next -> T.tr(next ? "routine.start.nextWeek" : "routine.start.thisWeek"));
        start.setValue(false);

        TimePicker time = new TimePicker(T.tr("routine.time"));
        time.setStep(Duration.ofMinutes(15));
        time.setLocale(locale);
        time.setClearButtonVisible(true);
        time.setHelperText(T.tr(pushEnabled ? "routine.time.helper" : "routine.time.helperNoPush"));

        Select<Owner> owner = new Select<>();
        owner.setLabel(T.tr("routine.for"));
        if (admin) {
            List<Owner> owners = new ArrayList<>();
            owners.add(EVERYONE);
            family.forEach(m -> owners.add(new Owner(m)));
            owner.setItems(owners);
            owner.setItemLabelGenerator(o -> o.member() == null ? T.tr("routine.for.everyone")
                    : o.member().getName());
        }

        if (existing != null) {
            name.setValue(existing.getName());
            emoji.setValue(existing.getEmoji() == null ? "" : existing.getEmoji());
            days.setValue(existing.days());
            repeat.setValue(existing.getEveryWeeks());
            time.setValue(existing.getTime());
        } else {
            days.setValue(EnumSet.noneOf(DayOfWeek.class));
        }
        if (admin) {
            Long forId = existing == null ? memberId : existing.getForMemberId();
            owner.setValue(forId == null ? EVERYONE : owner.getListDataView().getItems()
                    .filter(o -> o.member() != null && o.member().getId().equals(forId))
                    .findFirst().orElse(EVERYONE));
        }
        int everyBefore = existing == null ? 1 : existing.getEveryWeeks();
        start.setVisible(false);
        repeat.addValueChangeListener(e -> start.setVisible(
                e.getValue() == Routine.EVERY_OTHER_WEEK && everyBefore != Routine.EVERY_OTHER_WEEK));
        start.setVisible(repeat.getValue() == Routine.EVERY_OTHER_WEEK
                && everyBefore != Routine.EVERY_OTHER_WEEK);

        // Only for a routine of one's own: a parent's "Piano for Milla" is the parents' to see.
        Checkbox onlyMe = new Checkbox(T.tr("routine.private"));
        onlyMe.setHelperText(T.tr("routine.private.helper"));
        onlyMe.setValue(existing != null && existing.isPrivateToOwner());
        Runnable syncOnlyMe = () -> {
            boolean own = !admin || (owner.getValue() != null && owner.getValue().member() != null
                    && owner.getValue().member().getId().equals(memberId));
            onlyMe.setVisible(own);
        };
        syncOnlyMe.run();
        owner.addValueChangeListener(e -> syncOnlyMe.run());

        Paragraph error = new Paragraph();
        error.addClassName("form-error");
        error.setVisible(false);

        add(nameRow, days, repeat, start, time);
        if (admin) {
            add(owner);
        }
        add(onlyMe, error);

        Button save = new Button(T.tr("common.save"), e -> {
            if (days.getValue().isEmpty()) {
                days.setInvalid(true);
                days.setErrorMessage(T.tr("routine.noDays"));
                return;
            }
            Long forId = admin ? Optional.ofNullable(owner.getValue()).map(Owner::member)
                    .map(Member::getId).orElse(null) : memberId;
            Spec spec = new Spec(name.getValue(), emoji.getValue(), days.getValue(),
                    repeat.getValue() == null ? 1 : repeat.getValue(),
                    start.isVisible() && Boolean.TRUE.equals(start.getValue()), time.getValue(), forId,
                    onlyMe.isVisible() && onlyMe.getValue());
            Optional<Routine> saved = existing == null
                    ? routines.create(homeCode, memberId, spec)
                    : routines.update(homeCode, memberId, existing.getId(), spec);
            if (saved.isPresent()) {
                close();
                onChanged.run();
            } else if (name.getValue().isBlank()) {
                name.setInvalid(true);
                name.setErrorMessage(T.tr("routine.noName"));
            } else {
                error.setText(T.tr("routine.limit", InputLimits.MAX_ROUTINES_PER_HOME));
                error.setVisible(true);
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        if (existing != null) {
            Button delete = new Button(T.tr("common.delete"), e -> {
                routines.delete(homeCode, memberId, existing.getId());
                close();
                onChanged.run();
            });
            delete.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
            delete.addClassName("routine-delete");
            getFooter().add(delete);
        }
        getFooter().add(new Button(T.tr("common.cancel"), e -> close()), save);
        name.focus();
    }
}
