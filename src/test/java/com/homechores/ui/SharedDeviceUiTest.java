package com.homechores.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.homechores.domain.ChoreTask;
import com.homechores.domain.DivisionStyle;
import com.homechores.domain.Home;
import com.homechores.domain.Member;
import com.homechores.domain.SharedDevice;
import com.homechores.service.ChoreService;
import com.homechores.service.ListItemService;
import com.homechores.service.SharedDeviceService;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.TextField;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * The board on the family's shared device (§4.1.3): nobody's, with three tabs, and a "who did
 * it?" picker where a phone has "yes, I did it".
 */
@SpringBootTest
class SharedDeviceUiTest extends HomeChoresUiTest {

    @Autowired
    ChoreService service;

    @Autowired
    SharedDeviceService devices;

    @Autowired
    ListItemService lists;

    private Member alex;
    private Member milla;
    private String code;
    private SharedDevice device;

    /** A home with an admin, a child without a phone, and the kitchen tablet signed in. */
    private void openAsDevice() {
        alex = service.createHome("Nest", "Alex");
        code = alex.getHomeCode();
        milla = service.addMemberWithoutDevice(code, "Milla", alex.getId()).orElseThrow();
        String pin = service.findHome(code).orElseThrow().getAdminPin();
        device = devices.pair(code, pin, "Kitchen iPad", "Shared device").paired().device();
        SessionContext.signInDevice(device.getId(), code);
        navigate(HomeView.class);
    }

    @Test
    void theDevice_getsThreeTabs_noAdmin_andSaysWhatItIs() {
        openAsDevice();
        assertInstanceOf(HomeView.class, getCurrentView());
        assertEquals(3, tabCount(), "Chores, Lists, Stats — never Admin");
        assertTrue(hasSpanWithText("📺 Kitchen iPad · shared device"));
        assertFalse(buttons().contains("Admin?"), "a counter-top screen is never an admin");
        assertFalse(buttons().contains("Leave"));
        assertTrue($(Div.class).all().stream().noneMatch(d -> d.getClassNames().contains("daily-ring")
                && usable(d)), "the daily target is one person's, and the device is nobody");
    }

    @Test
    void tappingACard_asksWho_andThePickIsTheConfirmation() {
        openAsDevice();
        ChoreTask chore = service.tasksOf(code).get(0);

        clickChoreCard(0);
        assertTrue(hasSpanWithText("Who did it?") || hasDivWithText("Who did it?"));
        List<Button> chips = chipButtons();
        assertEquals(List.of("Alex", "Milla"), chips.stream().map(Button::getText).toList(),
                "the family as chips");
        clickButton("Cancel");
        assertEquals(0, service.completionCount(milla.getId()), "cancelling records nothing");

        clickChoreCard(0);
        click(chipButtons().get(1));
        assertEquals(1, service.completionCount(milla.getId()), "counted for Milla");
        assertEquals(0, service.completionCount(alex.getId()));
        assertEquals(milla.getId(), service.recentCompletions(code, 1).get(0).getMemberId());
        assertTrue(hasSpanWithText("Milla just did " + chore.getEmoji() + " " + chore.getName())
                        || spanTexts().stream().anyMatch(t -> t.startsWith("Milla just did")),
                "the undo strip names who it was logged for: " + spanTexts());

        clickButton("Undo");
        assertEquals(0, service.completionCount(milla.getId()), "and takes it back for them");
    }

    /** The locks are the picked member's: the card says whose turn it is, and tapping it for
     *  somebody else is refused in their name. */
    @Test
    void underEnforcedRotation_theCardSaysWhoseTurn_andAWrongPickIsRefused() {
        openAsDevice();
        Home home = service.findHome(code).orElseThrow();
        home.setDivisionStyle(DivisionStyle.ROTATING);
        home.setRotationEnforced(true);
        service.saveHome(home);
        awaitRedraw(() -> spanTexts().stream().anyMatch(t -> t.endsWith(" today")));

        List<ChoreTask> chores = service.tasksOf(code);
        Long alexs = service.rotationAssignedChoreId(home, alex.getId(), java.time.LocalDate.now());
        int alexIndex = 0;
        while (!chores.get(alexIndex).getId().equals(alexs)) {
            alexIndex++;
        }
        Div alexsCard = $(Div.class).all().stream()
                .filter(d -> String.valueOf(alexs).equals(d.getElement().getAttribute("data-task-id"))
                        && usable(d))
                .findFirst().orElseThrow();
        assertFalse(alexsCard.getClassNames().contains("locked"),
                "nobody's board locks no card on whose turn it is");
        assertFalse(alexsCard.getClassNames().contains("mine"));

        clickChoreCard(alexIndex);
        click(chipButtons().get(1)); // Milla, but it is Alex's chore today
        assertEquals(0, service.completionCount(milla.getId()), "refused, in her name");

        clickChoreCard(alexIndex);
        click(chipButtons().get(0));
        assertEquals(1, service.completionCount(alex.getId()));
    }

    @Test
    void statsTab_showsOneMembersNumbersAtATime() {
        openAsDevice();
        service.completeFor(service.tasksOf(code).get(0).getId(), milla.getId(), alex.getId());
        selectTab("Stats");
        assertEquals(1, $(Tabs.class).all().stream().filter(this::usable).count(),
                "no My / Home switch: the device is nobody, and never an admin");
        Select<Member> who = $(Select.class).all().stream().filter(this::usable).findFirst()
                .map(s -> (Select<Member>) s).orElseThrow();
        assertEquals("Whose statistics?", who.getLabel());
        who.setValue(milla);
        assertTrue(hasH2Containing("1 chores done"), "Milla's total");
    }

    @Test
    void listLinesFromTheDevice_carryNoAuthor_andNoBell() {
        openAsDevice();
        selectTab("Lists");
        TextField input = $(TextField.class).all().stream().filter(this::usable).findFirst()
                .orElseThrow();
        input.setValue("Milk");
        clickButton("Add");
        var lines = lists.openItems(code, com.homechores.domain.ListKind.GROCERY); // the first list
        assertEquals(1, lines.size());
        assertEquals(null, lines.get(0).getCreatedByMemberId());
        assertTrue($(Button.class).all().stream().noneMatch(b ->
                b.getClassNames().contains("list-remind-btn") && usable(b)));
    }

    /** Unpairing from a phone shows the device out on the next redraw, identity dropped. */
    @Test
    void anUnpairedDevice_isShownOut() {
        openAsDevice();
        assertTrue(devices.unpair(device.getId(), alex.getId())); // the admin, on their phone
        awaitRedraw(() -> getCurrentView() instanceof LandingView);
        assertFalse(SessionContext.isSignedIn());
    }

    // ---- helpers (the private ones in HomeUiTest, for the three this class needs) ----

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

    private List<String> buttons() {
        return $(Button.class).all().stream().filter(this::usable).map(Button::getText).toList();
    }

    private List<Button> chipButtons() {
        return $(Button.class).all().stream()
                .filter(b -> b.getClassNames().contains("who-chip") && usable(b)).toList();
    }

    private void click(Button b) {
        ComponentUtil.fireEvent(b, new ClickEvent<>(b));
    }

    private void clickButton(String text) {
        Button b = $(Button.class).all().stream()
                .filter(x -> text.equals(x.getText()) && usable(x))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No usable button: " + text));
        click(b);
    }

    private void clickChoreCard(int index) {
        Div area = $(Div.class).all().stream()
                .filter(d -> d.getClassNames().contains("complete-area") && usable(d))
                .toList().get(index);
        ComponentUtil.fireEvent(area, new ClickEvent<>(area));
    }

    private int tabCount() {
        return (int) $(Tabs.class).first().getChildren().count();
    }

    private void selectTab(String label) {
        Tabs tabs = $(Tabs.class).first();
        Tab tab = tabs.getChildren()
                .filter(Tab.class::isInstance).map(Tab.class::cast)
                .filter(t -> t.getElement().getTextRecursively().trim().startsWith(label))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No tab: " + label));
        tabs.setSelectedTab(tab);
    }

    private List<String> spanTexts() {
        return $(Span.class).all().stream().filter(this::usable).map(Span::getText).toList();
    }

    private boolean hasSpanWithText(String text) {
        return spanTexts().contains(text);
    }

    private boolean hasDivWithText(String text) {
        return $(Div.class).all().stream().filter(this::usable).anyMatch(d -> text.equals(d.getText()));
    }

    private boolean hasH2Containing(String text) {
        return $(com.vaadin.flow.component.html.H2.class).all().stream().filter(this::usable)
                .anyMatch(h -> h.getText().contains(text));
    }

    private void awaitRedraw(BooleanSupplier redrawn) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!redrawn.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("the change never redrew this UI");
            }
            runPendingSignalsTasks(50, TimeUnit.MILLISECONDS);
        }
    }
}
