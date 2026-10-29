package com.homechores.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.homechores.domain.ListKind;
import com.homechores.domain.Member;
import com.homechores.domain.Routine;
import com.homechores.service.ChoreService;
import com.homechores.service.ListItemService;
import com.homechores.service.RoutineService;
import com.homechores.service.RoutineService.Spec;
import com.homechores.service.SharedDeviceService;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.timepicker.TimePicker;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Routines on screen (§4.4f): the Routines tab on Lists, its editor, and the "Today's routines"
 * strip on the Chores tab — for an admin, a plain member and the shared device.
 */
@SpringBootTest
class RoutineUiTest extends HomeChoresUiTest {

    @Autowired ChoreService service;
    @Autowired RoutineService routines;
    @Autowired SharedDeviceService devices;
    @Autowired ListItemService lists;

    private static final EnumSet<DayOfWeek> EVERY_DAY = EnumSet.allOf(DayOfWeek.class);

    @Test
    void anAdmin_addsARoutineInTheEditor_andItShowsOnTheTabAndInTodaysStrip() {
        Member alex = service.createHome("Nest", "Alex");
        SessionContext.signIn(alex.getId(), alex.getHomeCode());
        navigate(HomeView.class);
        assertTrue(rows("routines-today").isEmpty(), "no strip for a home without routines");

        openRoutinesTab();
        assertTrue(texts().stream().anyMatch(t -> t.startsWith("Nothing repeats yet")));
        clickButton("Add routine");
        field("Name").setValue("Workout");
        @SuppressWarnings("unchecked")
        CheckboxGroup<DayOfWeek> days = $(CheckboxGroup.class).all().stream()
                .filter(g -> "Days".equals(g.getLabel())).findFirst().orElseThrow();
        clickButton("Save");
        assertTrue(days.isInvalid(), "no day picked: refused on the spot");

        days.setValue(EVERY_DAY);
        @SuppressWarnings("unchecked")
        RadioButtonGroup<Integer> repeat = $(RadioButtonGroup.class).all().stream()
                .filter(g -> "Repeats".equals(g.getLabel())).findFirst().orElseThrow();
        repeat.setValue(Routine.EVERY_OTHER_WEEK);
        assertTrue(usable($(RadioButtonGroup.class).all().stream()
                .filter(g -> "Starting".equals(g.getLabel())).findFirst().orElseThrow()),
                "every other week asks which week it starts");
        $(TimePicker.class).first().setValue(LocalTime.of(18, 0));
        clickButton("Save");

        Routine made = routines.routinesOf(alex.getHomeCode()).get(0);
        assertEquals("Workout", made.getName());
        assertEquals(alex.getId(), made.getForMemberId(), "an admin's default is themselves");
        assertEquals(Routine.EVERY_OTHER_WEEK, made.getEveryWeeks());
        assertTrue(texts().stream().anyMatch(t -> t.contains("Every day · 18:00 · every other week · Alex")));

        mainTab("Chores");
        assertEquals(1, rows("routines-today").size(), "it falls on today: it is on the strip");
    }

    @Test
    void tickingOnTheStrip_marksItDone_andUntickingTakesItBack() {
        Member alex = service.createHome("Nest", "Alex");
        String code = alex.getHomeCode();
        routines.create(code, alex.getId(), new Spec("Bins out", "🗑️", EVERY_DAY, 1, false,
                LocalTime.of(19, 0), null)).orElseThrow();
        SessionContext.signIn(alex.getId(), code);
        navigate(HomeView.class);

        Checkbox tick = $(Checkbox.class, rows("routines-today").get(0)).first();
        test(tick).click();
        assertTrue(routines.today(code, alex.getId()).get(0).isDone());
        assertTrue(texts().contains("done by Alex"));
        test($(Checkbox.class, rows("routines-today").get(0)).first()).click();
        assertFalse(routines.today(code, alex.getId()).get(0).isDone());
    }

    @Test
    void aMember_editsTheirOwnOnly_andGetsNoForField() {
        Member alex = service.createHome("Nest", "Alex");
        String code = alex.getHomeCode();
        Member sam = service.joinHome(code, "Sam").orElseThrow();
        routines.create(code, alex.getId(), new Spec("Bins out", null, EVERY_DAY, 1, false, null, null));
        routines.create(code, sam.getId(), new Spec("Run", null, EVERY_DAY, 1, false, null, sam.getId()));
        SessionContext.signIn(sam.getId(), code);
        navigate(HomeView.class);
        openRoutinesTab();

        assertEquals(2, rows("routines-panel").size(), "everyone's are listed");
        long pencils = rows("routines-panel").stream()
                .filter(r -> !$(Button.class, r).all().isEmpty()).count();
        assertEquals(1, pencils, "only Sam's own run can be edited");

        clickButton("Add routine");
        assertTrue($(Select.class).all().stream().noneMatch(this::usable),
                "a member makes routines for themselves only");
    }

    @Test
    void theSharedDevice_seesEveryonesToday_ticksThem_butHasNoEditor() {
        Member alex = service.createHome("Nest", "Alex");
        String code = alex.getHomeCode();
        Member milla = service.addMemberWithoutDevice(code, "Milla", alex.getId()).orElseThrow();
        routines.create(code, alex.getId(), new Spec("Piano", "🎹", EVERY_DAY, 1, false,
                LocalTime.of(16, 30), milla.getId())).orElseThrow();
        String pin = service.findHome(code).orElseThrow().getAdminPin();
        Long device = devices.pair(code, pin, "Kitchen", "Kitchen").paired().device().getId();
        SessionContext.signInDevice(device, code);
        navigate(HomeView.class);

        assertTrue(texts().contains("16:30 · Milla"), "the device says whose it is");
        test($(Checkbox.class, rows("routines-today").get(0)).first()).click();
        assertTrue(routines.today(code, null).get(0).isDone());

        openRoutinesTab();
        assertTrue($(Button.class).all().stream().filter(this::usable)
                .noneMatch(b -> "Add routine".equals(b.getText())), "the device edits nothing");
    }

    @Test
    void anAdminCanSwitchTheRoutinesTabOff_whichTakesTheStripWithIt() {
        Member alex = service.createHome("Nest", "Alex");
        String code = alex.getHomeCode();
        routines.create(code, alex.getId(), new Spec("Bins out", null, EVERY_DAY, 1, false, null, null));
        SessionContext.signIn(alex.getId(), code);
        navigate(HomeView.class);
        assertEquals(1, rows("routines-today").size());

        lists.setListEnabled(code, alex.getId(), ListKind.ROUTINES, false);
        navigate(HomeView.class);
        assertTrue(rows("routines-today").isEmpty());
    }

    // ---- helpers ----

    private void openRoutinesTab() {
        mainTab("Lists");
        Tabs kinds = $(Tabs.class).all().stream()
                .filter(t -> t.getClassNames().contains("list-kind-tabs") && usable(t))
                .findFirst().orElseThrow();
        kinds.setSelectedTab(kinds.getChildren().filter(Tab.class::isInstance).map(Tab.class::cast)
                .filter(t -> t.getElement().getTextRecursively().trim().equals("Routines"))
                .findFirst().orElseThrow());
    }

    private void mainTab(String label) {
        Tabs tabs = $(Tabs.class).first();
        tabs.setSelectedTab(tabs.getChildren().filter(Tab.class::isInstance).map(Tab.class::cast)
                .filter(t -> t.getElement().getTextRecursively().trim().startsWith(label))
                .findFirst().orElseThrow());
    }

    /** The routine rows inside the panel or strip with that class. */
    private List<Div> rows(String holderClass) {
        return $(Div.class).all().stream()
                .filter(d -> d.getClassNames().contains("routine-item") && usable(d)
                        && d.getParent().flatMap(Component::getParent)
                                .filter(p -> p.getClassNames().contains(holderClass)).isPresent())
                .toList();
    }

    private TextField field(String label) {
        return $(TextField.class).all().stream().filter(f -> label.equals(f.getLabel()) && usable(f))
                .findFirst().orElseThrow();
    }

    private List<String> texts() {
        return Stream.of(Span.class, Div.class, Paragraph.class)
                .flatMap(type -> $(type).all().stream()).filter(this::usable)
                .map(c -> c.getElement().getText()).filter(t -> !t.isBlank()).toList();
    }

    private void clickButton(String text) {
        Button b = $(Button.class).all().stream()
                .filter(x -> text.equals(x.getText()) && usable(x))
                .findFirst().orElseThrow(() -> new AssertionError("No usable button: " + text));
        ComponentUtil.fireEvent(b, new ClickEvent<>(b));
    }

    private boolean usable(Component c) {
        Component cur = c;
        while (cur != null) {
            if (!cur.isVisible()) {
                return false;
            }
            cur = cur.getParent().orElse(null);
        }
        return c.getUI().isPresent();
    }
}
