package com.homechores.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.homechores.domain.Home;
import com.homechores.domain.ListKind;
import com.homechores.domain.Member;
import com.homechores.service.Author;
import com.homechores.service.ChoreService;
import com.homechores.service.ListItemService;
import com.homechores.service.RoutineService;
import com.homechores.service.RoutineService.Spec;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.internal.PendingJavaScriptInvocation;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Sound and vibration (§4.12c): which effect each moment asks {@code effects.js} for, and the
 * per-device switches in the header menu. What the browser then plays — and that iPhones cannot
 * vibrate — is the browser's part, checked by hand.
 */
@SpringBootTest
class EffectsUiTest extends HomeChoresUiTest {

    @Autowired ChoreService service;
    @Autowired ListItemService lists;
    @Autowired RoutineService routines;

    @BeforeEach
    void useEnglish() {
        UI.getCurrent().setLocale(Locale.ENGLISH);
    }

    @Test
    void aCompletedChore_playsOneEffect_scaledLikeItsConfetti() {
        Member alex = signedInWithoutConfirmation();
        for (int i = 0; i < 3; i++) {
            sentJs("");
            clickChoreCard(0);
            List<List<Object>> sent = sentJs("");
            List<Object> effects = calls(sent, "playEffect");
            assertEquals(1, effects.size(), "one effect per completion");
            assertEquals(calls(sent, "fireConfetti").getFirst(), effects.getFirst());
        }
        assertEquals(3, service.completionCount(alex.getId()));
    }

    @Test
    void aRefusedTap_playsTheBlockedEffect_andNoPraise() {
        Member alex = service.createHome("Chime", "Alex");
        String code = alex.getHomeCode();
        Member sam = service.joinHome(code, "Sam").orElseThrow();
        Home home = service.findHome(code).orElseThrow();
        home.setConfirmCompletion(false);
        service.saveHome(home);
        // Sam said "I'll do it" for every chore, so whichever card Alex taps is Sam's to do.
        service.tasksOf(code).forEach(t -> service.bookChore(t.getId(), sam.getId()));
        SessionContext.signIn(alex.getId(), code);
        navigate(HomeView.class);
        sentJs("");

        clickChoreCard(0);

        assertEquals(List.of("blocked"), effectsSent());
        assertEquals(0, service.completionCount(alex.getId()), "the refused tap recorded nothing");
    }

    @Test
    void tickingAListLine_ticks_butUntickingIsSilent() {
        Member alex = signedInWithoutConfirmation();
        lists.add(alex.getHomeCode(), ListKind.GROCERY, Author.member(alex.getId()), "Milk");
        mainTab("Lists");
        sentJs("");

        test(listTick()).click();
        assertEquals(List.of("tick"), effectsSent());

        test(listTick()).click();
        assertEquals(List.of(), effectsSent());
    }

    @Test
    void tickingARoutine_ticks_butUntickingIsSilent() {
        Member alex = service.createHome("Nest", "Alex");
        routines.create(alex.getHomeCode(), alex.getId(), new Spec("Bins out", "🗑️",
                EnumSet.allOf(DayOfWeek.class), 1, false, LocalTime.of(19, 0), null, false)).orElseThrow();
        SessionContext.signIn(alex.getId(), alex.getHomeCode());
        navigate(HomeView.class);
        sentJs("");

        test(routineTick()).click();
        assertEquals(List.of("tick"), effectsSent());

        test(routineTick()).click();
        assertEquals(List.of(), effectsSent());
    }

    @Test
    void theMenuSwitches_startSoundOffAndVibrationOn_andStoreEachFlip() {
        signedInWithoutConfirmation();
        MenuItem sound = menuItem("Sounds");
        MenuItem vibration = menuItem("Vibration");
        assertFalse(sound.isChecked());
        assertTrue(vibration.isChecked());
        sentJs("");

        click(sound);
        assertTrue(sound.isChecked());
        assertEquals(List.of(List.of("__setEffect", "sound", true)), sentJs("__setEffect"));

        click(sound);
        click(vibration);
        assertFalse(sound.isChecked());
        assertFalse(vibration.isChecked());
        assertEquals(List.of(List.of("__setEffect", "sound", false),
                List.of("__setEffect", "vibration", false)), sentJs("__setEffect"));
    }

    // ---- helpers ----

    private Member signedInWithoutConfirmation() {
        Member alex = service.createHome("Chime", "Alex");
        Home home = service.findHome(alex.getHomeCode()).orElseThrow();
        home.setConfirmCompletion(false);
        service.saveHome(home);
        SessionContext.signIn(alex.getId(), alex.getHomeCode());
        navigate(HomeView.class);
        return alex;
    }

    /** Each call whose expression mentions {@code fn}, sent since the last look, as the
     *  function it calls followed by its arguments; reading empties the queue, so
     *  {@code sentJs("")} also just starts afresh. */
    private List<List<Object>> sentJs(String fn) {
        List<PendingJavaScriptInvocation> pending =
                UI.getCurrent().getInternals().dumpPendingJavaScriptInvocations();
        return pending.stream()
                .filter(p -> p.getInvocation().getExpression().contains(fn))
                .map(p -> {
                    // The expression first, so one dump can be split by function afterwards.
                    List<Object> call = new ArrayList<>();
                    call.add(expressionFunction(p.getInvocation().getExpression()));
                    call.addAll(p.getInvocation().getParameters());
                    return call;
                })
                .toList();
    }

    private List<Object> effectsSent() {
        return calls(sentJs(""), "playEffect");
    }

    /** The first argument of each call to {@code fn} among {@code sent}. */
    private static List<Object> calls(List<List<Object>> sent, String fn) {
        return sent.stream().filter(args -> args.getFirst().equals(fn))
                .map(args -> args.get(1)).toList();
    }

    private static String expressionFunction(String expression) {
        for (String fn : List.of("playEffect", "fireConfetti", "__setEffect")) {
            if (expression.contains(fn)) {
                return fn;
            }
        }
        return expression;
    }

    private void clickChoreCard(int index) {
        Div area = $(Div.class).all().stream()
                .filter(d -> d.getClassNames().contains("complete-area") && usable(d))
                .toList().get(index);
        ComponentUtil.fireEvent(area, new ClickEvent<>(area));
    }

    private Checkbox listTick() {
        Div row = $(Div.class).all().stream()
                .filter(d -> d.getClassNames().contains("list-item") && usable(d))
                .findFirst().orElseThrow();
        return $(Checkbox.class, row).first();
    }

    private Checkbox routineTick() {
        Div row = $(Div.class).all().stream()
                .filter(d -> d.getClassNames().contains("routine-item") && usable(d))
                .findFirst().orElseThrow();
        return $(Checkbox.class, row).first();
    }

    private void mainTab(String label) {
        Tabs tabs = $(Tabs.class).first();
        tabs.setSelectedTab(tabs.getChildren().filter(Tab.class::isInstance).map(Tab.class::cast)
                .filter(t -> t.getElement().getTextRecursively().trim().startsWith(label))
                .findFirst().orElseThrow());
    }

    private MenuItem menuItem(String text) {
        return $(MenuBar.class).all().stream().filter(this::usable)
                .flatMap(m -> m.getItems().stream())
                .flatMap(this::withSubItems)
                .filter(i -> text.equals(i.getElement().getTextRecursively().trim()))
                .findFirst().orElseThrow(() -> new AssertionError("No menu item: " + text));
    }

    private Stream<MenuItem> withSubItems(MenuItem item) {
        return Stream.concat(Stream.of(item),
                item.getSubMenu().getItems().stream().flatMap(this::withSubItems));
    }

    private static void click(MenuItem item) {
        ComponentUtil.fireEvent(item, new ClickEvent<>(item));
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
