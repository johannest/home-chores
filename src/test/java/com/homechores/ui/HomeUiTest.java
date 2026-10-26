package com.homechores.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.homechores.domain.ChoreTask;
import com.homechores.domain.Completion;
import com.homechores.domain.CompletionRepository;
import com.homechores.domain.CompletionStatus;
import com.homechores.domain.Home;
import com.homechores.domain.ListKind;
import com.homechores.domain.Member;
import com.homechores.domain.Season;
import com.homechores.domain.Trophy;
import com.homechores.service.Author;
import com.homechores.service.ChoreService;
import com.homechores.service.CreditService;
import com.homechores.service.ListItemService;
import com.homechores.service.SharedDeviceService;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.server.VaadinSession;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class HomeUiTest extends HomeChoresUiTest {

    @Autowired
    ChoreService service;
    @Autowired
    CreditService creditService;

    @Autowired
    CompletionRepository completions;

    @Autowired
    ListItemService lists;

    @Autowired
    SharedDeviceService sharedDevices;

    /** Pin the UI language to English so the text-based lookups below are stable. */
    @BeforeEach
    void useEnglish() {
        UI.getCurrent().setLocale(Locale.ENGLISH);
    }

    // ---- helpers ------------------------------------------------------------

    /** Visible up the whole ancestor chain (so components in a hidden form are excluded). */
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

    /**
     * The label a user would read: header buttons keep their text in a child span (so it
     * can be hidden on phones), which leaves {@code getText()} empty — fall back to the
     * accessible name those buttons set for exactly this reason.
     */
    private String labelOf(Button b) {
        String text = b.getText();
        if (text != null && !text.isBlank()) {
            return text.trim();
        }
        return b.getAriaLabel().orElseGet(() -> b.getElement().getTextRecursively()).trim();
    }

    /** Every MenuItem in the UI, sub menus included — the header "⋯" and the admin rows' "⋯". */
    private java.util.stream.Stream<MenuItem> allMenuItems() {
        return $(MenuBar.class).all().stream()
                .filter(this::usable)
                .flatMap(m -> m.getItems().stream())
                .flatMap(this::withSubItems);
    }

    private java.util.stream.Stream<MenuItem> withSubItems(MenuItem item) {
        return java.util.stream.Stream.concat(java.util.stream.Stream.of(item),
                item.getSubMenu().getItems().stream().flatMap(this::withSubItems));
    }

    /** Fires a click on a menu row by its visible text — what a tap on the open menu does. */
    private void clickMenuItem(String text) {
        MenuItem item = allMenuItems()
                .filter(i -> text.equals(i.getElement().getTextRecursively().trim()) && i.isEnabled())
                .findFirst()
                .orElseThrow(() -> new AssertionError("No usable menu item: " + text));
        ComponentUtil.fireEvent(item, new ClickEvent<>(item));
    }

    private void clickButton(String text) {
        Button b = $(Button.class).all().stream()
                .filter(x -> text.equals(labelOf(x)) && usable(x))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No usable button: " + text));
        ComponentUtil.fireEvent(b, new ClickEvent<>(b));
    }

    /** Ticks the user-agreement checkbox on whichever landing form is visible. */
    private void acceptTerms() {
        Checkbox cb = $(Checkbox.class).all().stream()
                .filter(this::usable)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No usable terms checkbox"));
        cb.setValue(true);
    }

    private void setField(String label, String value) {
        TextField f = $(TextField.class).all().stream()
                .filter(x -> label.equals(x.getLabel()) && usable(x))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No usable field: " + label));
        f.setValue(value);
    }

    /** Taps the nth chore card's complete area, the way a member does on the board. */
    private void clickChoreCard(int index) {
        Div area = $(Div.class).all().stream()
                .filter(d -> d.getClassNames().contains("complete-area") && usable(d))
                .toList().get(index);
        ComponentUtil.fireEvent(area, new ClickEvent<>(area));
    }

    private String valueOf(String label) {
        return $(TextField.class).all().stream()
                .filter(x -> label.equals(x.getLabel()) && usable(x))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No usable field: " + label))
                .getValue();
    }

    private void setTextArea(String label, String value) {
        TextArea a = $(TextArea.class).all().stream()
                .filter(x -> label.equals(x.getLabel()) && usable(x))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No usable text area: " + label));
        a.setValue(value);
    }

    /** Taps the board's "Other help" card. */
    private void clickHelpCard() {
        Div card = $(Div.class).all().stream()
                .filter(d -> d.getClassNames().contains("help-card") && usable(d))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No Other help card on the board"));
        ComponentUtil.fireEvent(card, new ClickEvent<>(card));
    }

    @SuppressWarnings("unchecked")
    private <T> Select<T> selectByLabel(String label) {
        return (Select<T>) $(Select.class).all().stream()
                .filter(x -> label.equals(x.getLabel()) && usable(x))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No usable select: " + label));
    }

    private int maxInactiveInterval() {
        return VaadinSession.getCurrent().getSession().getMaxInactiveInterval();
    }

    private int tabCount() {
        return (int) $(Tabs.class).first().getChildren().count();
    }

    private boolean hasSpanWithText(String text) {
        return $(Span.class).all().stream().anyMatch(s -> text.equals(s.getText()));
    }

    /** Selects a top-level tab by its label, so tests do not hard-code tab positions. */
    private void selectTab(String label) {
        Tabs tabs = $(Tabs.class).first();
        Tab tab = tabs.getChildren()
                .filter(Tab.class::isInstance).map(Tab.class::cast)
                .filter(t -> t.getElement().getTextRecursively().trim().startsWith(label))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No tab: " + label));
        tabs.setSelectedTab(tab);
    }

    /** Leaderboard chips left to right, by member name (the 👑 crown is stripped). */
    private List<String> leaderboardNames() {
        return $(Div.class).all().stream()
                .filter(d -> d.getClassNames().contains("member-chip") && usable(d))
                .map(d -> d.getChildren().filter(Span.class::isInstance).map(Span.class::cast)
                        .map(sp -> sp.getElement().getTextRecursively().replace("👑", "").trim())
                        .findFirst().orElse("?"))
                .toList();
    }

    /** The shared list's add box has no label, only a placeholder — look it up by that. */
    private void setFieldByPlaceholder(String placeholder, String value) {
        TextField f = $(TextField.class).all().stream()
                .filter(x -> placeholder.equals(x.getPlaceholder()) && usable(x))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No usable field with placeholder: " + placeholder));
        f.setValue(value);
    }

    private List<Div> listItemRows() {
        return $(Div.class).all().stream()
                .filter(d -> d.getClassNames().contains("list-item") && usable(d))
                .toList();
    }

    private Tabs listKindTabs() {
        return $(Tabs.class).all().stream()
                .filter(t -> t.getClassNames().contains("list-kind-tabs") && usable(t))
                .findFirst().orElseThrow(() -> new AssertionError("No list sub-tabs"));
    }

    private List<Div> dinnerRows() {
        return $(Div.class).all().stream()
                .filter(d -> d.getClassNames().contains("dinner-row") && usable(d))
                .toList();
    }

    /** The dinner text boxes, today first. */
    private List<TextField> dinnerFields() {
        return $(TextField.class).all().stream()
                .filter(f -> "What's for dinner?".equals(f.getPlaceholder()) && usable(f))
                .toList();
    }

    /** The stats headline tiles (Today / This week / This month / All time). */
    private List<Div> statTiles() {
        return $(Div.class).all().stream()
                .filter(d -> d.getClassNames().contains("stat-tile") && usable(d))
                .toList();
    }

    private Div statTile(String label) {
        return statTiles().stream()
                .filter(d -> d.getElement().getTextRecursively().endsWith(label))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No stat tile: " + label));
    }

    /** An admin card by the start of its summary — the counts in some titles move around. */
    private Details adminSection(String summaryPrefix) {
        return $(Details.class).all().stream()
                .filter(d -> d.getSummaryText() != null
                        && d.getSummaryText().startsWith(summaryPrefix))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No admin section: " + summaryPrefix));
    }

    /**
     * Toggles an admin card the way a tap does.
     *
     * <p>{@code DetailsTester.openDetails()} only calls {@code setOpened} server-side, which is
     * exactly the case the panel must ignore. A real tap does two things: it changes the property
     * and it reports the change as user-originated. Both are needed here, or the test would not
     * touch the listener that remembers the admin's choice.
     */
    private void toggleSection(String summaryPrefix, boolean open) {
        Details d = adminSection(summaryPrefix);
        if (open) {
            test(d).openDetails();
        } else {
            test(d).closeDetails();
        }
        ComponentUtil.fireEvent(d, new Details.OpenedChangeEvent(d, true));
    }

    private void openSection(String summaryPrefix) {
        toggleSection(summaryPrefix, true);
    }

    /** The invite menu specifically — the header also holds the appearance picker, which is a
     *  MenuBar too. */
    private List<MenuBar> inviteMenus() {
        return $(MenuBar.class).all().stream()
                .filter(m -> m.getClassNames().contains("invite-menu") && usable(m))
                .toList();
    }

    private List<Div> filterChips() {
        return $(Div.class).all().stream()
                .filter(d -> d.getClassNames().contains("filter-chip") && usable(d))
                .toList();
    }

    private void clickFilterChip(String label) {
        Div chip = filterChips().stream()
                .filter(d -> label.equals(d.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No filter chip: " + label
                        + " (have " + filterChips().stream().map(Div::getText).toList() + ")"));
        ComponentUtil.fireEvent(chip, new ClickEvent<>(chip));
    }

    private List<String> chipLabels() {
        return filterChips().stream().map(Div::getText).toList();
    }

    private String selectedChip() {
        return filterChips().stream()
                .filter(d -> d.getClassNames().contains("selected"))
                .map(Div::getText)
                .findFirst().orElse(null);
    }

    /** Chore cards currently on the board, excluding the "Other help" and "Add chore" tiles. */
    private long visibleChoreCards() {
        return $(Div.class).all().stream()
                .filter(d -> d.getClassNames().contains("complete-area") && usable(d))
                .count();
    }

    // ---- tests --------------------------------------------------------------

    /**
     * Waits until the redraw another phone's change causes has landed: the shared revision
     * signal confirms the bump, and the view's effect runs, through the signals test environment
     * ({@link HomeChoresUiTest}), whose queue {@code runPendingSignalsTasks} drains on this
     * thread. Polling for the outcome keeps the wait as short as the redraw, and bounded.
     */
    private void awaitRedraw(BooleanSupplier redrawn) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!redrawn.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("the other phone's change never redrew this UI");
            }
            runPendingSignalsTasks(50, TimeUnit.MILLISECONDS);
        }
    }

    @Test
    void createHome_navigatesToBoard_asAdmin_withFourTabs() {
        navigate(LandingView.class);
        setField("Home name", "Test Home");
        setField("Your name", "Alex");
        acceptTerms();
        clickButton("Create home 🚀");
        clickButton("Let's go 🚀"); // dismiss the "home created / here's your PIN" dialog

        assertInstanceOf(HomeView.class, getCurrentView());
        assertEquals(4, tabCount(), "admin sees Chores + List + Stats + Admin");
        assertTrue(hasSpanWithText("Alex"), "creator appears on the leaderboard");
    }

    @Test
    void joinHome_navigatesToBoard_asMember_withThreeTabs() {
        Member admin = service.createHome("Shared", "Alex");
        Home home = service.findHome(admin.getHomeCode()).orElseThrow();
        home.setApproveJoin(false); // no gate: joining signs in straight away
        service.saveHome(home);

        navigate(LandingView.class);
        $(Tabs.class).first().setSelectedIndex(1); // switch to the "Join a home" tab
        setField("Home code", admin.getHomeCode());
        setField("Your name", "Sam");
        acceptTerms();
        clickButton("Join home 🙌");

        assertInstanceOf(HomeView.class, getCurrentView());
        assertEquals(3, tabCount(), "a plain member has no Admin tab");
    }

    /** By default a first-time join waits for an admin — a guessed code alone creates nobody. */
    @Test
    void joinHome_withGateOn_raisesARequestInsteadOfCreatingAMember() {
        Member admin = service.createHome("Shared", "Alex");
        assertTrue(service.findHome(admin.getHomeCode()).orElseThrow().isApproveJoin());

        navigate(LandingView.class);
        $(Tabs.class).first().setSelectedIndex(1);
        setField("Home code", admin.getHomeCode());
        setField("Your name", "Sam");
        acceptTerms();
        clickButton("Join home 🙌");

        assertInstanceOf(LandingView.class, getCurrentView(), "still waiting, not signed in");
        assertNull(SessionContext.memberId());
        assertEquals(1, service.membersOf(admin.getHomeCode()).size(), "no member created yet");
        assertEquals(1, service.pendingRejoins(admin.getHomeCode()).size());

        // The admin letting Sam in is the moment the member comes into being.
        var request = service.pendingRejoins(admin.getHomeCode()).get(0);
        assertTrue(service.decideRejoin(request.getId(), admin.getId(), true));
        assertEquals(2, service.membersOf(admin.getHomeCode()).size());
        assertEquals("Sam", service.membersOf(admin.getHomeCode()).get(1).getName());
    }

    @Test
    void member_canClaimAdmin_withPin_revealingAdminTab() {
        Member admin = service.createHome("Shared", "Alex");
        Home home = service.findHome(admin.getHomeCode()).orElseThrow();
        Member sam = service.joinHome(admin.getHomeCode(), "Sam").orElseThrow();

        SessionContext.signIn(sam.getId(), sam.getHomeCode());
        navigate(HomeView.class);
        assertEquals(3, tabCount());

        clickMenuItem("Admin?");
        setField("Admin PIN", home.getAdminPin());
        clickButton("Unlock");

        assertEquals(4, tabCount(), "correct PIN unlocks the Admin tab");
    }

    @Test
    void admin_canAddChore_fromAdminPanel() {
        Member admin = service.createHome("Shared", "Alex");
        String code = admin.getHomeCode();
        SessionContext.signIn(admin.getId(), code);
        navigate(HomeView.class);

        // Switch to the Admin tab (index 2) and add a chore.
        selectTab("Admin");
        openSection("Chores");
        clickButton("Add chore");
        setField("Chore name", "Iron shirts");
        clickButton("Save");

        assertEquals(12, service.tasksOf(code).size(), "new chore persisted (11 seeded + 1)");
    }

    @Test
    void admin_canCreateAGroup_andTheBoardShowsItAsAHeading() {
        Member admin = service.createHome("Shared", "Alex");
        String code = admin.getHomeCode();
        SessionContext.signIn(admin.getId(), code);
        navigate(HomeView.class);

        selectTab("Admin");
        openSection("Chore groups");
        clickButton("Add group");
        setField("Group name", "Kitchen");
        clickButton("Save");

        assertEquals(1, service.groupsOf(code).size());
        service.setChoreGroup(service.tasksOf(code).get(0).getId(),
                service.groupsOf(code).get(0).getId());

        $(Tabs.class).first().setSelectedIndex(0);
        assertTrue(groupHeadings().stream().anyMatch(h -> h.contains("Kitchen")),
                "the group is a heading on the board, over its own grid");
        assertTrue(groupHeadings().stream().anyMatch(h -> h.contains("Other chores")),
                "and the ungrouped remainder gets named once any group is showing");
    }

    /**
     * The guarantee for every home that exists at upgrade time: no groups means the board it
     * always had — one grid, no headings at all.
     */
    @Test
    void aHomeWithNoGroups_rendersOneUnheadedGrid() {
        Member admin = service.createHome("Plain", "Alex");
        SessionContext.signIn(admin.getId(), admin.getHomeCode());
        navigate(HomeView.class);

        assertTrue(groupHeadings().isEmpty(), "no groups, no headings");
        assertEquals(1, $(Div.class).all().stream()
                .filter(d -> d.getClassNames().contains("task-grid") && usable(d)).count());
    }

    @Test
    void admin_canMoveAChoreUp_whichReordersTheBoard() {
        Member admin = service.createHome("Shared", "Alex");
        String code = admin.getHomeCode();
        SessionContext.signIn(admin.getId(), code);
        navigate(HomeView.class);
        String second = service.tasksOf(code).get(1).getName();

        selectTab("Admin");
        openSection("Chores");
        // Every row's "⋯" menu carries "Move up"; the first row's is disabled, so the second
        // row's is the first usable one.
        clickMenuItem("Move up");

        assertEquals(second, service.tasksOf(code).get(0).getName());
    }

    private List<String> groupHeadings() {
        return $(Div.class).all().stream()
                .filter(d -> d.getClassNames().contains("group-heading") && usable(d))
                .map(Div::getText)
                .toList();
    }

    /**
     * The recovery path a family member takes after clearing their browser storage: type
     * your own nickname instead of joining again under the same name. Nobody is listed —
     * a home code alone must not read the family's names off the screen.
     */
    @Test
    void rejoinDialog_signsBackInByNickname_withoutDuplicating_orListingMembers() {
        Member admin = service.createHome("Shared", "Alex");
        Member sam = service.joinHome(admin.getHomeCode(), "Sam").orElseThrow();
        Home home = service.findHome(admin.getHomeCode()).orElseThrow();
        home.setApproveRejoin(false); // no gate: a matching nickname signs in straight away
        service.saveHome(home);

        navigate(LandingView.class);
        $(Tabs.class).first().setSelectedIndex(1); // "Join a home"
        setField("Home code", admin.getHomeCode());
        clickButton("I'm already a member — sign me back in");

        // One submit button and a name field — no per-member rows revealing the roster.
        assertEquals(1, $(Button.class).all().stream()
                .filter(b -> "That's me".equals(b.getText()) && usable(b)).count());

        setField("Your nickname in this home", "alex"); // case-insensitive on purpose
        clickButton("That's me");
        assertInstanceOf(HomeView.class, getCurrentView());
        assertEquals(admin.getId(), SessionContext.memberId(), "signed in as the existing Alex");
        assertEquals(2, service.membersOf(admin.getHomeCode()).size(), "no third member created");
        assertEquals(sam.getId(), service.membersOf(admin.getHomeCode()).get(1).getId());
    }

    /** A nickname that matches nobody stays in the dialog and creates nothing. */
    @Test
    void rejoinDialog_unknownNickname_isRejectedWithoutARequest() {
        Member admin = service.createHome("Shared", "Alex");

        navigate(LandingView.class);
        $(Tabs.class).first().setSelectedIndex(1);
        setField("Home code", admin.getHomeCode());
        clickButton("I'm already a member — sign me back in");
        setField("Your nickname in this home", "Mallory");
        clickButton("That's me");

        assertInstanceOf(LandingView.class, getCurrentView());
        assertNull(SessionContext.memberId());
        assertTrue(service.pendingRejoins(admin.getHomeCode()).isEmpty());
    }

    /** With the gate on and no PIN, a matching nickname parks the device in the queue. */
    @Test
    void rejoinDialog_withGateOn_raisesARequestInsteadOfSigningIn() {
        Member admin = service.createHome("Shared", "Alex");
        assertTrue(service.findHome(admin.getHomeCode()).orElseThrow().isApproveRejoin());

        navigate(LandingView.class);
        $(Tabs.class).first().setSelectedIndex(1);
        setField("Home code", admin.getHomeCode());
        clickButton("I'm already a member — sign me back in");
        setField("Your nickname in this home", "Alex");
        clickButton("That's me");

        assertInstanceOf(LandingView.class, getCurrentView(), "still waiting, not signed in");
        assertNull(SessionContext.memberId());
        assertEquals(1, service.pendingRejoins(admin.getHomeCode()).size());
    }

    // ---- Restoring a stored identity, and explaining why not (§4.1) ------------

    private LandingView landing() {
        navigate(LandingView.class);
        return (LandingView) getCurrentView();
    }

    /** Walked from the view, not queried: {@code $()} skips hidden components, and hidden is
     *  what the notice must be on a plain first visit. */
    private Div restoreNotice() {
        return ((Component) getCurrentView()).getChildren().flatMap(Component::getChildren)
                .filter(c -> c instanceof Div d && d.hasClassName("auth-notice"))
                .map(Div.class::cast).findFirst().orElseThrow();
    }

    @Test
    void storedIdentity_withTheCurrentSecret_signsStraightIn() {
        Member admin = service.createHome("Shared", "Alex");
        String secret = service.issueDeviceSecret(admin.getId());

        landing().restoreMember(DeviceIdentity.parse(admin.getId() + "|" + admin.getHomeCode() + "|" + secret));

        assertInstanceOf(HomeView.class, getCurrentView());
        assertEquals(admin.getId(), SessionContext.memberId());
        assertEquals(SessionContext.ADMIN_TIMEOUT_SECONDS, maxInactiveInterval());
    }

    /** The one-device-per-member rule, explained: the phone whose secret a later sign-in
     *  replaced is told so and pointed at "sign me back in", not at joining twice. */
    @Test
    void storedIdentity_withAReplacedSecret_explainsAndOpensTheJoinTab() {
        Member admin = service.createHome("Shared", "Alex");
        String thisPhone = service.issueDeviceSecret(admin.getId());
        service.issueDeviceSecret(admin.getId()); // another browser signed in as Alex since

        landing().restoreMember(DeviceIdentity.parse(admin.getId() + "|" + admin.getHomeCode() + "|" + thisPhone));

        assertInstanceOf(LandingView.class, getCurrentView(), "not signed in");
        assertNull(SessionContext.memberId());
        assertTrue(restoreNotice().isVisible());
        assertTrue(restoreNotice().getText().startsWith("Another device signed in as you"),
                restoreNotice().getText());
        assertEquals(1, $(Tabs.class).first().getSelectedIndex(), "the Join tab, where sign-back-in is");
    }

    @Test
    void storedIdentity_ofARemovedMember_saysTheMemberIsGone() {
        Member admin = service.createHome("Shared", "Alex");
        Member sam = service.joinHome(admin.getHomeCode(), "Sam").orElseThrow();
        String secret = service.issueDeviceSecret(sam.getId());
        assertTrue(service.removeMember(sam.getId()));

        landing().restoreMember(DeviceIdentity.parse(sam.getId() + "|" + admin.getHomeCode() + "|" + secret));

        assertInstanceOf(LandingView.class, getCurrentView());
        assertNull(SessionContext.memberId());
        assertEquals("The member or home this device belonged to no longer exists.",
                restoreNotice().getText());
    }

    /** A value from before device secrets reads as stale, not as "never joined". */
    @Test
    void storedIdentity_fromAnOlderVersion_isExplainedAsStale() {
        Member admin = service.createHome("Shared", "Alex");

        landing().restoreMember(DeviceIdentity.parse(admin.getId() + "|" + admin.getHomeCode()));

        assertInstanceOf(LandingView.class, getCurrentView());
        assertNull(SessionContext.memberId());
        assertTrue(restoreNotice().getText().startsWith("This device's saved sign-in is from an older version"),
                restoreNotice().getText());
        assertEquals(1, $(Tabs.class).first().getSelectedIndex());
    }

    /** A first visit has nothing stored and nothing to explain. */
    @Test
    void nothingStored_showsAPlainJoinCard() {
        LandingView view = landing();
        view.restoreMember(DeviceIdentity.parse(""));
        view.restoreSharedDevice(DeviceIdentity.parse(""));

        assertInstanceOf(LandingView.class, getCurrentView());
        assertFalse(restoreNotice().isVisible());
        assertEquals(0, $(Tabs.class).first().getSelectedIndex(), "Create stays in front");
    }

    @Test
    void storedDeviceIdentity_ofAnUnpairedDevice_saysSo() {
        Member admin = service.createHome("Shared", "Alex");

        landing().restoreSharedDevice(DeviceIdentity.parse("999999|" + admin.getHomeCode() + "|secret"));

        assertInstanceOf(LandingView.class, getCurrentView());
        assertNull(SessionContext.deviceId());
        assertEquals("This shared device was unpaired, or its home no longer exists.",
                restoreNotice().getText());
    }

    private SharedDeviceService.Paired pairKitchen(Member admin) {
        String pin = service.findHome(admin.getHomeCode()).orElseThrow().getAdminPin();
        return sharedDevices.pair(admin.getHomeCode(), pin, "Kitchen", "Shared device").paired();
    }

    @Test
    void storedDeviceIdentity_withItsSecret_signsInAsTheDevice() {
        Member admin = service.createHome("Shared", "Alex");
        SharedDeviceService.Paired kitchen = pairKitchen(admin);

        landing().restoreSharedDevice(DeviceIdentity.parse(kitchen.device().getId() + "|"
                + admin.getHomeCode() + "|" + kitchen.secret()));

        assertInstanceOf(HomeView.class, getCurrentView());
        assertEquals(kitchen.device().getId(), SessionContext.deviceId());
        assertNull(SessionContext.memberId(), "the device is nobody");
    }

    /** Device ids count up from one, so the secret is the whole proof: a real id with a
     *  made-up secret, or the real secret sent with another home's code, signs nothing in. */
    @Test
    void storedDeviceIdentity_withAForgedSecretOrAnotherHomesCode_isRefused() {
        Member admin = service.createHome("Shared", "Alex");
        SharedDeviceService.Paired kitchen = pairKitchen(admin);
        Member other = service.createHome("Other", "Pat");

        landing().restoreSharedDevice(DeviceIdentity.parse(kitchen.device().getId() + "|"
                + admin.getHomeCode() + "|" + "0".repeat(32)));
        assertInstanceOf(LandingView.class, getCurrentView());
        assertNull(SessionContext.deviceId());
        assertTrue(restoreNotice().isVisible());

        landing().restoreSharedDevice(DeviceIdentity.parse(kitchen.device().getId() + "|"
                + other.getHomeCode() + "|" + kitchen.secret()));
        assertInstanceOf(LandingView.class, getCurrentView());
        assertNull(SessionContext.deviceId());
        assertFalse(SessionContext.isSignedIn());
    }

    // ---- Pairing a shared device, and unpairing it (§4.1.3) -----------------------

    private Notification lastNotification() {
        List<Notification> open = $(Notification.class).all().stream()
                .filter(Notification::isOpened).toList();
        return open.get(open.size() - 1);
    }

    private void openPairing(String code) {
        navigate(LandingView.class);
        $(Tabs.class).first().setSelectedIndex(1);
        setField("Home code", code);
        clickButton("Set up a shared family device");
    }

    @Test
    void pairing_withTheAdminPin_makesThisBrowserTheDevice_andAWrongPinSaysSo() {
        Member admin = service.createHome("Nest", "Alex");
        String pin = service.findHome(admin.getHomeCode()).orElseThrow().getAdminPin();
        openPairing(admin.getHomeCode());

        setField("Device name", "Kitchen");
        setField("Admin PIN", pin.equals("0000") ? "1111" : "0000");
        clickButton("Pair this device");
        TextField pinField = $(TextField.class).all().stream()
                .filter(f -> "Admin PIN".equals(f.getLabel()) && usable(f)).findFirst().orElseThrow();
        assertTrue(pinField.isInvalid());
        assertEquals("Wrong PIN", pinField.getErrorMessage());
        assertTrue(sharedDevices.devicesOf(admin.getHomeCode()).isEmpty());

        setField("Admin PIN", pin);
        clickButton("Pair this device");
        assertInstanceOf(HomeView.class, getCurrentView());
        assertNull(SessionContext.memberId(), "the device is nobody");
        assertEquals(sharedDevices.devicesOf(admin.getHomeCode()).get(0).getId(), SessionContext.deviceId());
        assertEquals("Kitchen", sharedDevices.devicesOf(admin.getHomeCode()).get(0).getName());
    }

    @Test
    void pairing_withNoHomeCode_asksForIt_inThePairingsOwnWords() {
        navigate(LandingView.class);
        $(Tabs.class).first().setSelectedIndex(1);
        clickButton("Set up a shared family device");
        assertEquals("Enter your home code first, then pair the device.",
                test(lastNotification()).getText(), "not \"sign back in\": nobody is signing in");
    }

    @Test
    void pairing_aSixthDevice_isRefused_withWhereToUnpairOne() {
        Member admin = service.createHome("Nest", "Alex");
        String pin = service.findHome(admin.getHomeCode()).orElseThrow().getAdminPin();
        for (int i = 0; i < 5; i++) {
            sharedDevices.pair(admin.getHomeCode(), pin, "Tablet " + i, "Shared device");
        }
        openPairing(admin.getHomeCode());
        setField("Admin PIN", pin);
        clickButton("Pair this device");

        assertInstanceOf(LandingView.class, getCurrentView());
        assertEquals("This home already has as many shared devices as it can. Unpair one in the Admin tab first.",
                test(lastNotification()).getText());
        assertEquals(5, sharedDevices.devicesOf(admin.getHomeCode()).size());
    }

    @Test
    void adminTab_listsTheSharedDevices_andUnpairingOneAsksFirst() {
        Member admin = service.createHome("Nest", "Alex");
        String code = admin.getHomeCode();
        String pin = service.findHome(code).orElseThrow().getAdminPin();
        sharedDevices.pair(code, pin, "Kitchen iPad", "Shared device");
        SessionContext.signIn(admin.getId(), code);
        navigate(HomeView.class);
        selectTab("Admin");
        openSection("Shared devices");
        assertEquals("Shared devices (1)", adminSection("Shared devices").getSummaryText());
        assertTrue($(Div.class).all().stream().filter(this::usable)
                .anyMatch(d -> "Kitchen iPad".equals(d.getText())));

        clickButton("Unpair");
        clickButton("Cancel");
        assertEquals(1, sharedDevices.devicesOf(code).size(), "cancelled: still paired");

        clickButton("Unpair");
        clickButton("Confirm");
        assertTrue(sharedDevices.devicesOf(code).isEmpty());
        awaitRedraw(() -> adminSection("Shared devices").getSummaryText().equals("Shared devices (0)"));
    }

    /** The wipe is irreversible, so a wrong code must not delete anything. */
    @Test
    void deleteHome_requiresTheHomeCodeTyped_correctly() {
        Member admin = service.createHome("Doomed", "Alex");
        String code = admin.getHomeCode();
        SessionContext.signIn(admin.getId(), code);
        navigate(HomeView.class);
        selectTab("Admin");
        openSection("Danger zone");

        clickButton("Delete this home");
        setField("Type the home code " + code + " to confirm", "WRONG");
        clickButton("Delete everything");
        assertTrue(service.findHome(code).isPresent(), "a wrong code deletes nothing");
        assertInstanceOf(HomeView.class, getCurrentView());

        setField("Type the home code " + code + " to confirm", code);
        clickButton("Delete everything");

        assertTrue(service.findHome(code).isEmpty(), "home wiped");
        assertTrue(service.membersOf(code).isEmpty());
        assertNull(SessionContext.memberId(), "the deleting admin is signed out");
        assertInstanceOf(LandingView.class, getCurrentView());
    }

    /** A stray tap must ask first, and answering "not yet" must record nothing. */
    @Test
    void tappingAChore_asksBeforeRecordingIt() {
        Member admin = service.createHome("Confirmed", "Alex");
        String code = admin.getHomeCode();
        SessionContext.signIn(admin.getId(), code);
        navigate(HomeView.class);

        clickChoreCard(0);
        clickButton("Not yet");
        assertEquals(0, service.completionCount(admin.getId()), "declining records nothing");

        clickChoreCard(0);
        clickButton("Yes, I did it ✅");
        assertEquals(1, service.completionCount(admin.getId()));
    }

    @Test
    void withConfirmationOff_aTapCompletesStraightAway() {
        Member admin = service.createHome("Fast", "Alex");
        String code = admin.getHomeCode();
        Home home = service.findHome(code).orElseThrow();
        home.setConfirmCompletion(false);
        service.saveHome(home);
        SessionContext.signIn(admin.getId(), code);
        navigate(HomeView.class);

        clickChoreCard(0);

        assertEquals(1, service.completionCount(admin.getId()));
    }

    /** The member's own escape hatch, from the strip that outlives the celebration. */
    @Test
    void memberCanUndoTheirChoreFromTheBoard() {
        Member admin = service.createHome("Undo", "Alex");
        SessionContext.signIn(admin.getId(), admin.getHomeCode());
        navigate(HomeView.class);
        clickChoreCard(0);
        clickButton("Yes, I did it ✅");
        assertEquals(1, service.completionCount(admin.getId()));

        clickButton("Undo");

        assertEquals(0, service.completionCount(admin.getId()));
    }

    @Test
    void admin_canUnmarkAChoreFromRecentActivity() {
        Member admin = service.createHome("Correct", "Alex");
        String code = admin.getHomeCode();
        Member sam = service.joinHome(code, "Sam").orElseThrow();
        service.complete(service.tasksOf(code).get(0).getId(), sam.getId());
        assertEquals(1, service.completionCount(sam.getId()));

        SessionContext.signIn(admin.getId(), code);
        navigate(HomeView.class);
        selectTab("Admin");
        openSection("Recent chores");

        clickButton("Unmark");
        clickButton("Confirm");

        assertEquals(0, service.completionCount(sam.getId()),
                "an admin can take back someone else's chore");
    }

    /** Help that no chore covers: the member writes it, and nothing counts until reviewed. */
    @Test
    void member_canLogOtherHelp_whichWaitsForAnAdmin() {
        Member admin = service.createHome("Helpful", "Alex");
        String code = admin.getHomeCode();
        Member sam = service.joinHome(code, "Sam").orElseThrow();
        SessionContext.signIn(sam.getId(), code);
        navigate(HomeView.class);

        clickHelpCard();
        setTextArea("What did you do?", "Carried the shopping in");
        clickButton("Send for approval");

        assertEquals(0, service.completionCount(sam.getId()), "not counted before a decision");
        assertEquals(1, service.pendingOtherHelp(code).size());
        assertEquals("Carried the shopping in", service.pendingOtherHelp(code).get(0).getNote());
    }

    /** Accepting counts it, and the follow-up prompt turns it into a card anyone can tap. */
    @Test
    void admin_acceptsOtherHelp_andCanAddItToTheChores() {
        Member admin = service.createHome("Helpful", "Alex");
        String code = admin.getHomeCode();
        Member sam = service.joinHome(code, "Sam").orElseThrow();
        service.logOtherHelp(code, sam.getId(), "Washed the car");

        SessionContext.signIn(admin.getId(), code);
        navigate(HomeView.class);
        selectTab("Admin");

        clickButton("Accept");
        clickButton("Accept help");
        assertEquals(1, service.completionCount(sam.getId()), "accepted help counts");

        // The prompt that follows offers the same help as a permanent chore.
        assertEquals("Washed the car", valueOf("Chore name"));
        clickButton("Add as chore");

        assertEquals(12, service.tasksOf(code).size(), "11 seeded + the promoted one");
        assertTrue(service.tasksOf(code).stream()
                .anyMatch(t -> "Washed the car".equals(t.getName())));
    }

    @Test
    void admin_canDeclineOtherHelp() {
        Member admin = service.createHome("Helpful", "Alex");
        String code = admin.getHomeCode();
        Member sam = service.joinHome(code, "Sam").orElseThrow();
        service.logOtherHelp(code, sam.getId(), "Not much really");

        SessionContext.signIn(admin.getId(), code);
        navigate(HomeView.class);
        selectTab("Admin");

        clickButton("Decline");

        assertEquals(0, service.completionCount(sam.getId()));
        assertTrue(service.pendingOtherHelp(code).isEmpty());
        assertEquals(11, service.tasksOf(code).size(), "declining adds no chore");
    }

    /** The phone-less member's chores still get onto the board, logged by an admin. */
    @Test
    void admin_canLogAChoreForAnotherMember() {
        Member admin = service.createHome("Household", "Alex");
        String code = admin.getHomeCode();
        Member sam = service.joinHome(code, "Sam").orElseThrow();

        SessionContext.signIn(admin.getId(), code);
        navigate(HomeView.class);
        selectTab("Admin");
        openSection("Log a chore for someone");

        // "Who did it" offers the other members only, so the default is already Sam.
        Select<Member> who = selectByLabel("Who did it");
        assertEquals(sam.getId(), who.getValue().getId());
        Select<ChoreTask> which = selectByLabel("Which chore");
        which.setValue(service.tasksOf(code).get(1));
        clickButton("Log it");

        assertEquals(1, service.completionCount(sam.getId()));
        assertEquals(0, service.completionCount(admin.getId()), "not logged for the admin");
    }

    /**
     * Sessions are short so idle phones cost the server nothing; admins get longer because
     * their work is reading and filling in forms (see SessionContext).
     */
    @Test
    void sessionLifetime_isShortForMembersAndLongerForAdmins() {
        Member admin = service.createHome("Timed", "Alex");
        String code = admin.getHomeCode();
        Member sam = service.joinHome(code, "Sam").orElseThrow();

        SessionContext.signIn(sam.getId(), code);
        navigate(HomeView.class);
        assertEquals(SessionContext.MEMBER_TIMEOUT_SECONDS, maxInactiveInterval());

        // Claiming admin on the same device moves it onto the admin lifetime.
        clickMenuItem("Admin?");
        setField("Admin PIN", service.findHome(code).orElseThrow().getAdminPin());
        clickButton("Unlock");
        assertEquals(SessionContext.ADMIN_TIMEOUT_SECONDS, maxInactiveInterval());
    }

    @Test
    void member_doesNotSeeAdminTab() {
        Member admin = service.createHome("Shared", "Alex");
        Member sam = service.joinHome(admin.getHomeCode(), "Sam").orElseThrow();
        SessionContext.signIn(sam.getId(), sam.getHomeCode());
        navigate(HomeView.class);
        assertEquals(3, tabCount());
    }

    // ---- Board filter chips -------------------------------------------------

    @Test
    void freshHome_offersOnlyTheCadencesItActuallyHas() {
        Member alex = service.createHome("Shared", "Alex");
        String code = alex.getHomeCode();
        SessionContext.signIn(alex.getId(), code);
        navigate(HomeView.class);

        // Everything on a fresh board is on today's list (the Today lens ignores time of
        // day, so the dog walk's hours don't matter) — a Today chip that selects all is
        // suppressed and the default falls back to All, which renders the same board.
        assertEquals(List.of("All", "Anytime", "Weekly"), chipLabels());
        assertEquals("All", selectedChip());
    }

    /** A parked interval chore is exactly what makes "Today" a real lens — and the default. */
    @Test
    void todayChip_isTheDefault_andHidesAChoreDoneWithinItsInterval() {
        Member alex = service.createHome("Shared", "Alex");
        String code = alex.getHomeCode();
        // Complete the weekly chore: it leaves today's list until its interval elapses.
        ChoreTask weekly = service.tasksOf(code).stream()
                .filter(t -> t.getIntervalDays() > 0).findFirst().orElseThrow();
        service.completeFor(weekly.getId(), alex.getId(), alex.getId());
        SessionContext.signIn(alex.getId(), code);
        navigate(HomeView.class);

        assertTrue(chipLabels().contains("Today"),
                "something is parked, so narrowing to today's list means something");
        assertEquals("Today", selectedChip(), "the board opens on today's chores");
        assertFalse($(Div.class).all().stream()
                .filter(d -> d.getClassNames().contains("task-card") && usable(d))
                .anyMatch(d -> d.getElement().getTextRecursively().contains("Water plants")),
                "the chore we just did is not on today's list");
    }

    /** The default lens must not hide the two non-chore tiles. */
    @Test
    void otherHelpAndAddChore_showUnderTheDefaultTodayLens() {
        Member alex = service.createHome("Shared", "Alex");
        String code = alex.getHomeCode();
        ChoreTask weekly = service.tasksOf(code).stream()
                .filter(t -> t.getIntervalDays() > 0).findFirst().orElseThrow();
        service.completeFor(weekly.getId(), alex.getId(), alex.getId());
        SessionContext.signIn(alex.getId(), code);
        navigate(HomeView.class);

        assertEquals("Today", selectedChip());
        assertTrue($(Div.class).all().stream()
                .anyMatch(d -> d.getClassNames().contains("help-card") && usable(d)),
                "Other help stays reachable under the default lens");
        assertTrue($(Div.class).all().stream()
                .anyMatch(d -> d.getClassNames().contains("add-card") && usable(d)),
                "so does the admin's Add chore tile");
    }

    @Test
    void narrowingToOneCadence_showsOnlyThoseChores_andHidesTheExtraTiles() {
        Member alex = service.createHome("Shared", "Alex");
        SessionContext.signIn(alex.getId(), alex.getHomeCode());
        navigate(HomeView.class);
        assertEquals(11, visibleChoreCards());

        clickFilterChip("Weekly");

        assertEquals(1, visibleChoreCards(), "only water plants is weekly");
        assertEquals("Weekly", selectedChip());
        assertTrue($(Div.class).all().stream()
                .noneMatch(d -> d.getClassNames().contains("help-card") && usable(d)),
                "Other help is not a weekly chore");
        assertTrue($(Div.class).all().stream()
                .noneMatch(d -> d.getClassNames().contains("add-card") && usable(d)),
                "nor is the Add chore tile");
    }

    @Test
    void chosenFilter_survivesARebuildFromAnotherDevice() {
        Member alex = service.createHome("Shared", "Alex");
        String code = alex.getHomeCode();
        SessionContext.signIn(alex.getId(), code);
        navigate(HomeView.class);
        clickFilterChip("Weekly");

        // Somebody else adds a chore, which bumps the home and rebuilds the whole view.
        service.addTask(code, "Sweep the porch", "🧹", 0);

        assertEquals("Weekly", selectedChip(), "a personal lens is not other people's business");
    }

    /** The one case that must never leave a blank board staring back at the member. */
    @Test
    void completingTheLastDueChore_fallsBackToAll_ratherThanEmptying() {
        Member alex = service.createHome("Shared", "Alex");
        String code = alex.getHomeCode();
        Home home = service.findHome(code).orElseThrow();
        home.setConfirmCompletion(false);
        service.saveHome(home);
        // "Today" can only run out if every chore has an interval, so replace the seeded set
        // with three weekly ones and pre-do one — that third card is what makes "Today" a real
        // choice rather than a synonym for "All".
        for (ChoreTask t : service.tasksOf(code)) {
            service.deleteTask(t.getId());
        }
        service.addTask(code, "Weekly A", "🅰️", 7);
        service.addTask(code, "Weekly B", "🅱️", 7);
        ChoreTask done = service.addTask(code, "Weekly C", "🇨", 7);
        service.completeFor(done.getId(), alex.getId(), alex.getId());

        SessionContext.signIn(alex.getId(), code);
        navigate(HomeView.class);
        clickFilterChip("Today");
        assertEquals(2, visibleChoreCards(), "two of the three are still due");

        clickChoreCard(0);
        clickChoreCard(0); // the board re-rendered, so the survivor is index 0 again

        assertEquals(3, service.completionCount(alex.getId()), "all three really were done");
        // Nothing is due any more, so "Today" is gone and with it the last real choice — the row
        // retires itself. What matters is that the member is looking at their chores, not a blank.
        assertEquals(3, visibleChoreCards(), "board must not be left empty by a filter");
        assertTrue(filterChips().isEmpty(), "no alternatives left, so no row");
    }

    @Test
    void offSeasonChore_isLockedAndGetsItsOwnChip() {
        Member alex = service.createHome("Shared", "Alex");
        String code = alex.getHomeCode();
        Season opposite = Season.values()[(Season.of(LocalDate.now().getMonth()).ordinal() + 2) % 4];
        service.addTask(code, "Shovel snow", "❄️", 0, 0, null, opposite.name());
        SessionContext.signIn(alex.getId(), code);
        navigate(HomeView.class);

        assertTrue(chipLabels().contains("Off-season"), "a parked chore is discoverable");
        clickFilterChip("Off-season");
        assertEquals(1, visibleChoreCards());
        assertTrue($(Div.class).all().stream()
                .anyMatch(d -> d.getClassNames().contains("task-card")
                        && d.getClassNames().contains("locked") && usable(d)),
                "an out-of-season card reads as locked");

        assertEquals(0, service.completionCount(alex.getId()));
        clickChoreCard(0);
        assertEquals(0, service.completionCount(alex.getId()), "and cannot be tapped through");
    }

    @Test
    void offSeasonChip_isAbsentWhenNothingIsParked() {
        Member alex = service.createHome("Shared", "Alex");
        SessionContext.signIn(alex.getId(), alex.getHomeCode());
        navigate(HomeView.class);

        assertFalse(chipLabels().contains("Off-season"));
    }

    // ---- Header: invite menu, chore master ------------------------------------

    @Test
    void soloHome_keepsTheInvitePlumbingInline() {
        Member alex = service.createHome("Solo", "Alex");
        SessionContext.signIn(alex.getId(), alex.getHomeCode());
        navigate(HomeView.class);

        assertTrue($(Span.class).all().stream()
                .anyMatch(s -> s.getClassNames().contains("code-chip") && usable(s)),
                "a lone creator still needs the code front and center");
        // By class, not by type: the appearance picker is a MenuBar in the same header now, so
        // "is there a MenuBar" no longer means "is the invite plumbing collapsed".
        assertTrue(inviteMenus().isEmpty(),
                "no overflow menu while there is nobody to overflow for");
    }

    /**
     * The appearance picker replaced the theme select rather than joining it, so the header gains
     * a setting without gaining width — the ≤640px action row was already at its limit before the
     * theme select and the reminder bell were added to it.
     */
    @Test
    void appearanceMenu_offersBothSchemesAndPalettes() {
        Member alex = service.createHome("Looks", "Alex");
        SessionContext.signIn(alex.getId(), alex.getHomeCode());
        navigate(HomeView.class);

        MenuItem appearance = allMenuItems()
                .filter(i -> "Appearance".equals(i.getElement().getTextRecursively().trim()))
                .findFirst().orElseThrow(() -> new AssertionError("No Appearance entry in the header menu"));

        List<String> labels = appearance.getSubMenu().getItems().stream()
                .map(i -> i.getElement().getTextRecursively().trim())
                .filter(t -> !t.isEmpty())
                .toList();
        assertTrue(labels.stream().anyMatch(l -> l.contains("Dark")), "the scheme rows survive");
        for (String palette : List.of("Green", "Pink", "Blue", "Grey")) {
            assertTrue(labels.stream().anyMatch(l -> l.contains(palette)),
                    palette + " is offered; had " + labels);
        }
        assertEquals(0, $(com.vaadin.flow.component.select.Select.class).all().stream()
                        .filter(this::usable).count(),
                "no selects in the header at all — language is a menu entry too");
        assertTrue(allMenuItems().anyMatch(i -> "Suomi".equals(i.getElement().getTextRecursively().trim())),
                "the languages are offered from the same menu");
    }

    @Test
    void multiMemberHome_collapsesCodeCopyShareIntoTheInviteMenu() {
        Member alex = service.createHome("Family", "Alex");
        String code = alex.getHomeCode();
        service.joinHome(code, "Sam");
        SessionContext.signIn(alex.getId(), code);
        navigate(HomeView.class);

        assertTrue($(Span.class).all().stream()
                .noneMatch(s -> s.getClassNames().contains("code-chip") && usable(s)),
                "the inline code row retires once the family has company");
        MenuBar invite = inviteMenus().stream().findFirst()
                .orElseThrow(() -> new AssertionError("No invite menu"));
        var items = invite.getItems().get(0).getSubMenu().getItems();
        assertEquals(3, items.size(), "code, copy link, share");
        assertTrue(items.get(0).getElement().getTextRecursively().contains(code),
                "the code itself is the first item");
    }

    @Test
    void lastWeeksChoreMaster_isBadgedInTheHeader() {
        Member alex = service.createHome("Champs", "Alex");
        String code = alex.getHomeCode();
        Member sam = service.joinHome(code, "Sam").orElseThrow();
        var done = service.complete(service.tasksOf(code).get(0).getId(), sam.getId());
        Completion c = completions.findById(done.completionId()).orElseThrow();
        c.setDoneAt(java.time.LocalDate.now()
                .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                .minusWeeks(1).plusDays(2).atTime(12, 0)
                .atZone(java.time.ZoneId.systemDefault()).toInstant());
        completions.save(c);

        SessionContext.signIn(alex.getId(), code);
        navigate(HomeView.class);

        assertTrue($(Span.class).all().stream()
                .anyMatch(s -> s.getClassNames().contains("chore-master")
                        && s.getText().contains("Sam") && usable(s)),
                "last week's top member wears the 🥇 badge");
    }

    // ---- Done today list -------------------------------------------------------

    @Test
    void doneTodayList_appearsCollapsed_andNamesTheDoer() {
        Member alex = service.createHome("Feed", "Alex");
        String code = alex.getHomeCode();
        Home home = service.findHome(code).orElseThrow();
        home.setConfirmCompletion(false);
        service.saveHome(home);
        SessionContext.signIn(alex.getId(), code);
        navigate(HomeView.class);
        assertTrue($(Details.class).all().stream()
                .noneMatch(d -> d.getClassNames().contains("done-today") && usable(d)),
                "an empty day earns no section");

        clickChoreCard(0);

        Details section = $(Details.class).all().stream()
                .filter(d -> d.getClassNames().contains("done-today") && usable(d))
                .findFirst().orElseThrow(() -> new AssertionError("No Done today section"));
        assertEquals("Done today (1)", section.getSummaryText());
        assertFalse(section.isOpened(), "collapsed by default");
        assertTrue(section.getElement().getTextRecursively().contains("Alex — "),
                "the row names who did what");
    }

    // ---- Escalating celebrations ------------------------------------------------

    @Test
    void thirdChoreOfTheDay_earnsTheOnFireDialog() {
        Member alex = service.createHome("Streaky", "Alex");
        String code = alex.getHomeCode();
        Home home = service.findHome(code).orElseThrow();
        home.setConfirmCompletion(false);
        service.saveHome(home);
        SessionContext.signIn(alex.getId(), code);
        navigate(HomeView.class);

        // The same chore each time: a first-ever completion celebrates "New chore
        // unlocked!" (that branch deliberately outranks the daily tiers), so the tiers
        // show on repeat completions — allowed up to MAX_IN_A_ROW of the same chore.
        clickChoreCard(0);
        assertTrue(celebrateTitles().contains("New chore unlocked!"), "first time this chore");
        clickButton("Done 🎉");
        clickChoreCard(0);
        assertTrue(celebrateTitles().contains("Two down! 💪"), "second chore steps it up");
        clickButton("Done 🎉");
        clickChoreCard(0);
        assertTrue(celebrateTitles().contains("On fire! 🔥"), "third chore: on fire");
    }

    private List<String> celebrateTitles() {
        return $(Div.class).all().stream()
                .filter(d -> d.getClassNames().contains("celebrate-title"))
                .map(d -> d.getElement().getTextRecursively())
                .toList();
    }

    // ---- Trophies -----------------------------------------------------------------

    /** {@code n} approved chores on the home's first chore, done over the past hours. */
    private void approvedBefore(Member m, int n) {
        Long task = service.tasksOf(m.getHomeCode()).get(0).getId();
        for (int i = 0; i < n; i++) {
            Completion c = new Completion(m.getHomeCode(), task, m.getId(), CompletionStatus.APPROVED);
            c.setDoneAt(Instant.now().minus(Duration.ofHours(2L * (n - i))));
            completions.save(c);
        }
    }

    private List<Div> trophyTiles() {
        return $(Div.class).all().stream()
                .filter(d -> d.getClassNames().contains("trophy-tile") && usable(d))
                .toList();
    }

    /** Celebration titles in dialogs that are still open. */
    private List<String> openCelebrateTitles() {
        return $(Div.class).all().stream()
                .filter(d -> d.getClassNames().contains("celebrate-title") && usable(d))
                .map(d -> d.getElement().getTextRecursively())
                .toList();
    }

    @Test
    void trophyShelf_isOnMyStats_withEveryRungAndTheNextOnesProgress() {
        Member alex = service.createHome("Shelf", "Alex");
        approvedBefore(alex, 7);
        service.markTrophiesSeen(alex.getId());
        SessionContext.signIn(alex.getId(), alex.getHomeCode());
        navigate(HomeView.class);
        selectTab("Stats");

        List<Div> tiles = trophyTiles();
        assertEquals(8, tiles.size(), "every rung of the ladder");
        assertTrue(tiles.get(0).getClassNames().contains("earned"), "🌱 at five");
        assertTrue(tiles.get(1).getClassNames().contains("locked"));
        assertTrue(tiles.get(1).getClassNames().contains("next"), "🧹 is the one to aim for");
        assertNull(tiles.get(0).getElement().getAttribute("tabindex"), "a fact, not a control");
        assertEquals("listitem", tiles.get(0).getElement().getAttribute("role"));
        Div bar = $(Div.class).all().stream()
                .filter(d -> d.getClassNames().contains("trophy-progress") && usable(d))
                .findFirst().orElseThrow();
        assertEquals("progressbar", bar.getElement().getAttribute("role"));
        assertEquals("7", bar.getElement().getAttribute("aria-valuenow"));
        assertEquals("10", bar.getElement().getAttribute("aria-valuemax"));
    }

    @Test
    void aTrophyEarnedWhileAway_opensOnTheBoardOnce_andLeadsToTheShelf() {
        Member alex = service.createHome("Away", "Alex");
        approvedBefore(alex, 5); // e.g. approved by an admin; the seen marker is still 0
        SessionContext.signIn(alex.getId(), alex.getHomeCode());
        navigate(HomeView.class);

        assertEquals(List.of("New trophy: Sprout!"), openCelebrateTitles());
        clickButton("See my trophies");
        assertEquals(8, trophyTiles().size(), "straight onto the shelf");

        navigate(PrivacyView.class);
        navigate(HomeView.class);
        assertTrue(openCelebrateTitles().isEmpty(), "shown once, not on every visit");
    }

    @Test
    void severalUnseenTrophies_shareOneDialog() {
        Member alex = service.createHome("Veteran", "Alex");
        approvedBefore(alex, 26);
        SessionContext.signIn(alex.getId(), alex.getHomeCode());
        navigate(HomeView.class);

        assertEquals(List.of("3 new trophies!"), openCelebrateTitles());
    }

    @Test
    void theLeaderboardChip_wearsTheBestTrophy_withAName() {
        Member alex = service.createHome("Badges", "Alex");
        approvedBefore(alex, 10);
        service.markTrophiesSeen(alex.getId());
        SessionContext.signIn(alex.getId(), alex.getHomeCode());
        navigate(HomeView.class);

        Span badge = $(Span.class).all().stream()
                .filter(sp -> sp.getClassNames().contains("trophy-badge") && usable(sp))
                .findFirst().orElseThrow(() -> new AssertionError("no trophy on the chip"));
        assertEquals("🧹", badge.getText());
        assertEquals("Trophy: Helper", badge.getElement().getAttribute("aria-label"));
    }

    @Test
    void tappingTowardsATrophy_saysHowNear_andReachingItShowsTheReward() {
        Member alex = service.createHome("Climb", "Alex");
        String code = alex.getHomeCode();
        Home home = service.findHome(code).orElseThrow();
        home.setConfirmCompletion(false);
        service.saveHome(home);
        creditService.setTrophyReward(code, Trophy.SPROUT, 7, alex.getId());
        approvedBefore(alex, 3);
        SessionContext.signIn(alex.getId(), code);
        navigate(HomeView.class);

        clickChoreCard(1);
        assertTrue($(Paragraph.class).all().stream().anyMatch(p -> usable(p)
                        && "Just 1 more to 🌱 Sprout (+7 💎)!".equals(p.getText())),
                "the fourth chore says the fifth earns a trophy");
        clickButton("Done 🎉");

        clickChoreCard(2);
        assertEquals(List.of("New trophy: Sprout!"), openCelebrateTitles());
        assertTrue($(Paragraph.class).all().stream().anyMatch(p -> usable(p)
                && "+7 💎 reward".equals(p.getText())));
    }

    @Test
    void admin_setsATrophysReward_inTheRewardsSection() {
        Member alex = service.createHome("Payday", "Alex");
        String code = alex.getHomeCode();
        SessionContext.signIn(alex.getId(), code);
        navigate(HomeView.class);
        selectTab("Admin");
        openSection("Rewards");

        IntegerField silver = $(IntegerField.class).all().stream()
                .filter(f -> "💎 reward for Silver".equals(f.getAriaLabel().orElse(null)))
                .findFirst().orElseThrow(() -> new AssertionError("no Silver reward field"));
        test(silver).setValue(20);

        assertEquals(Map.of(Trophy.SILVER, 20),
                creditService.trophyRewards(code));
    }

    // ---- Avatars -----------------------------------------------------------------

    @Test
    void chosenAvatar_rendersOnTheLeaderboardChip() {
        Member alex = service.createHome("Faces", "Alex");
        service.setAvatar(alex.getId(), "panda");
        SessionContext.signIn(alex.getId(), alex.getHomeCode());
        navigate(HomeView.class);

        assertTrue($(Image.class).all().stream()
                .anyMatch(i -> i.getSrc().contains("avatars/panda.png") && usable(i)),
                "the picked animal shows in the member's dot");
    }

    // ---- Frequency presets --------------------------------------------------

    /** Any interval must survive a trip through the editor, preset or not. */
    @Test
    void frequencyField_roundTripsEveryInterval() {
        FrequencyField field = new FrequencyField();
        for (int days : new int[] {0, 1, 7, 14, 30, 90, 365, 5, 45, 200}) {
            field.setIntervalDays(days);
            assertEquals(days, field.getIntervalDays(), days + " days should survive the editor");
        }
        field.setIntervalDays(-3);
        assertEquals(0, field.getIntervalDays(), "a negative clamps to anytime");
    }

    @Test
    void choreDialog_savesThePresetsDayCount() {
        Member alex = service.createHome("Shared", "Alex");
        String code = alex.getHomeCode();
        SessionContext.signIn(alex.getId(), code);
        navigate(HomeView.class);
        selectTab("Admin");
        openSection("Chores");
        clickButton("Add chore");

        setField("Chore name", "Change the tyres");
        Select<FrequencyField.Preset> freq = selectByLabel("How often?");
        freq.setValue(FrequencyField.Preset.QUARTERLY);
        clickButton("Save");

        ChoreTask saved = service.tasksOf(code).stream()
                .filter(t -> t.getName().equals("Change the tyres")).findFirst().orElseThrow();
        assertEquals(90, saved.getIntervalDays(), "the preset writes its canonical day count");
    }

    // ---- Admin panel sections ----------------------------------------------

    @Test
    void adminPanel_startsWithTheSetAndForgetSectionsCollapsed() {
        Member alex = service.createHome("Shared", "Alex");
        SessionContext.signIn(alex.getId(), alex.getHomeCode());
        navigate(HomeView.class);
        selectTab("Admin");

        for (String title : List.of("Home settings", "Chore rules", "Backup", "Danger zone", "Chores",
                "Members", "Rewards", "Recent chores", "Log a chore")) {
            assertFalse(adminSection(title).isOpened(), title + " should start collapsed");
        }
    }

    @Test
    void sectionCounts_showInTheSummary_soACollapsedCardStillTellsYouSomething() {
        Member alex = service.createHome("Shared", "Alex");
        service.joinHome(alex.getHomeCode(), "Sam");
        SessionContext.signIn(alex.getId(), alex.getHomeCode());
        navigate(HomeView.class);
        selectTab("Admin");

        assertEquals("Chores (11)", adminSection("Chores").getSummaryText());
        assertEquals("Members (2)", adminSection("Members").getSummaryText());
    }

    /**
     * The section that has teeth for the isFromClient guard: without it the programmatic
     * auto-expand records a preference nobody expressed, and an empty queue would stay open forever.
     */
    @Test
    void pendingApprovals_expandOnlyWhileSomethingIsWaiting() {
        Member alex = service.createHome("Shared", "Alex");
        String code = alex.getHomeCode();
        Home home = service.findHome(code).orElseThrow();
        home.setRequireApproval(true);
        service.saveHome(home);
        Member sam = service.joinHome(code, "Sam").orElseThrow();
        service.complete(service.tasksOf(code).get(0).getId(), sam.getId());

        SessionContext.signIn(alex.getId(), code);
        navigate(HomeView.class);
        selectTab("Admin");
        assertTrue(adminSection("Pending approvals").isOpened(), "a queue opens itself");

        clickButton("Approve");

        assertFalse(adminSection("Pending approvals").isOpened(),
                "and folds away once it is empty");
    }

    @Test
    void openingASection_survivesARebuild() {
        Member alex = service.createHome("Shared", "Alex");
        String code = alex.getHomeCode();
        SessionContext.signIn(alex.getId(), code);
        navigate(HomeView.class);
        selectTab("Admin");
        openSection("Home settings");

        // Any mutation bumps the home and rebuilds all eleven sections from scratch.
        service.addTask(code, "Sweep the porch", "🧹", 0);

        assertTrue(adminSection("Home settings").isOpened(),
                "the admin's choice outlives the rebuild");
    }

    @Test
    void closingAnUrgentSection_sticks() {
        Member alex = service.createHome("Shared", "Alex");
        String code = alex.getHomeCode();
        Home home = service.findHome(code).orElseThrow();
        home.setRequireApproval(true);
        service.saveHome(home);
        Member sam = service.joinHome(code, "Sam").orElseThrow();
        service.complete(service.tasksOf(code).get(0).getId(), sam.getId());
        SessionContext.signIn(alex.getId(), code);
        navigate(HomeView.class);
        selectTab("Admin");

        toggleSection("Pending approvals", false);
        service.addTask(code, "Sweep the porch", "🧹", 0);

        assertFalse(adminSection("Pending approvals").isOpened(),
                "folding a card away is also a choice worth remembering");
    }

    /** US-55: an admin's own help needs nobody's acceptance — it counts and celebrates at once. */
    @Test
    void admin_logsOtherHelp_andItCountsAtOnce() {
        Member admin = service.createHome("Helpful", "Alex");
        String code = admin.getHomeCode();
        SessionContext.signIn(admin.getId(), code);
        navigate(HomeView.class);

        clickHelpCard();
        setTextArea("What did you do?", "Fixed the leaking tap");
        clickButton("Log it");

        assertEquals(1, service.completionCount(admin.getId()), "counted without a review step");
        assertTrue(service.pendingOtherHelp(code).isEmpty(), "nothing waits in the admin queue");
        assertFalse(celebrateTitles().isEmpty(), "and it is celebrated like a chore");
    }

    /** The four headline tiles are the period picker; the duplicate chip row is gone. */
    @Test
    void statsTiles_pickThePeriod_andThereIsNoSeparateChipRow() {
        Member admin = service.createHome("Numbers", "Alex");
        String code = admin.getHomeCode();
        SessionContext.signIn(admin.getId(), code);
        navigate(HomeView.class);
        selectTab("Stats");

        assertEquals(4, statTiles().size(), "Today / This week / This month / All time");
        assertTrue(filterChips().isEmpty(), "no chip row under the tiles any more");
        assertTrue(statTile("This week").getClassNames().contains("selected"), "week is the default lens");
        assertEquals("button", statTile("This week").getElement().getAttribute("role"));

        Div month = statTile("This month");
        ComponentUtil.fireEvent(month, new ClickEvent<>(month));

        assertTrue(statTile("This month").getClassNames().contains("selected"));
        assertFalse(statTile("This week").getClassNames().contains("selected"));
        assertEquals("true", statTile("This month").getElement().getAttribute("aria-pressed"));
        assertTrue($(Div.class).all().stream().anyMatch(d ->
                        d.getClassNames().contains("chart-title")
                                && d.getElement().getTextRecursively().contains("This month")),
                "the per-chore chart follows the tile's lens");
    }

    /** The shared list: anyone adds, anyone ticks, and none of it is a chore. */
    @Test
    void member_canAddTickAndClearSharedListItems_withoutLoggingAChore() {
        Member alex = service.createHome("Listful", "Alex");
        String code = alex.getHomeCode();
        Member sam = service.joinHome(code, "Sam").orElseThrow();
        SessionContext.signIn(sam.getId(), code);
        navigate(HomeView.class);
        selectTab("Lists");

        setFieldByPlaceholder("Add an item…", "Milk");
        clickButton("Add");
        assertEquals(1, listItemRows().size(), "the line shows up");
        assertEquals("Milk", lists.openItems(code, ListKind.GROCERY).get(0).getText());
        assertEquals(sam.getId(), lists.openItems(code, ListKind.GROCERY).get(0).getCreatedByMemberId());

        // Tick it the way a person does: the checkbox listener only reacts to client input.
        Checkbox tick = $(Checkbox.class).all().stream().filter(this::usable).findFirst().orElseThrow();
        test(tick).click();

        assertTrue(lists.openItems(code, ListKind.GROCERY).isEmpty());
        assertEquals(sam.getId(), lists.doneItems(code, ListKind.GROCERY).get(0).getDoneByMemberId());
        assertTrue(listItemRows().get(0).getClassNames().contains("done"), "struck through");
        assertTrue(listItemRows().get(0).getElement().getTextRecursively().contains("Sam"),
                "and it says who ticked it");
        assertEquals(0, service.completionCount(sam.getId()), "ticking a grocery is not a chore");

        clickButton("Clear done");
        assertTrue(listItemRows().isEmpty());
        assertTrue(lists.doneItems(code, ListKind.GROCERY).isEmpty());
    }

    /** Which list one member is looking at survives another phone's edit, like the stats lens. */
    @Test
    void listSubTab_survivesAnotherMembersEdit() {
        Member alex = service.createHome("Listful", "Alex");
        String code = alex.getHomeCode();
        Member sam = service.joinHome(code, "Sam").orElseThrow();
        SessionContext.signIn(alex.getId(), code);
        navigate(HomeView.class);
        selectTab("Lists");

        Tabs kinds = $(Tabs.class).all().stream()
                .filter(t -> t.getClassNames().contains("list-kind-tabs") && usable(t))
                .findFirst().orElseThrow();
        kinds.setSelectedIndex(1); // To-do
        setFieldByPlaceholder("Add an item…", "Call the plumber");
        clickButton("Add");
        assertEquals(1, lists.openItems(code, ListKind.TODO).size(), "landed on the to-do list");

        lists.add(code, ListKind.GROCERY, Author.member(sam.getId()), "Milk"); // Sam's phone bumps the home

        Tabs after = $(Tabs.class).all().stream()
                .filter(t -> t.getClassNames().contains("list-kind-tabs") && usable(t))
                .findFirst().orElseThrow();
        assertEquals(1, after.getSelectedIndex(), "still on To-do");
        assertEquals(1, listItemRows().size(), "showing the to-do, not Sam's grocery");
        assertTrue(listItemRows().get(0).getElement().getTextRecursively().contains("Call the plumber"));
    }

    /** The dinner week: today plus a full week of slots, set inline, a draft outliving another phone's edit. */
    @Test
    void member_canSetTodaysDinner_andADraftSurvivesAnotherPhonesEdit() {
        Member alex = service.createHome("Listful", "Alex");
        String code = alex.getHomeCode();
        Member sam = service.joinHome(code, "Sam").orElseThrow();
        SessionContext.signIn(sam.getId(), code);
        navigate(HomeView.class);
        selectTab("Lists");
        listKindTabs().setSelectedIndex(2); // Dinner

        List<Div> rows = dinnerRows();
        assertEquals(8, rows.size(), "today plus a full week: a Sunday plan reaches next Sunday");
        assertTrue(rows.get(0).getClassNames().contains("today"));
        assertFalse(rows.get(1).getClassNames().contains("today"));
        java.time.LocalDate today = java.time.LocalDate.now(SessionContext.timeZone());
        assertTrue(rows.get(0).getElement().getTextRecursively()
                        .contains(ListPanel.dayLabel(today, Locale.ENGLISH)),
                "the row is labelled with the weekday and date");

        setFieldByPlaceholder("What's for dinner?", "Pasta"); // the first such field is today's
        clickButton("Save");

        assertEquals("Pasta", lists.dinners(code, today, today).get(today).getText());
        assertTrue(dinnerRows().get(0).getElement().getTextRecursively().contains("Sam"),
                "and it says who planned it");

        // A half-typed slot for tomorrow survives Alex planning another day on their phone.
        TextField tomorrow = dinnerFields().get(1);
        tomorrow.setValue("Tac");
        lists.setDinner(code, today.plusDays(3), Author.member(alex.getId()), "Pizza"); // bumps the home
        assertEquals("Tac", dinnerFields().get(1).getValue(), "the draft is still there");
        assertEquals("Pasta", dinnerFields().get(0).getValue(), "today's saved slot too");
    }

    /** The leaderboard ranks by the badge counter, highest first; ties keep the join order. */
    @Test
    void leaderboard_ranksByBadgeCount_highestFirst() {
        Member alex = service.createHome("Ranked", "Alex");
        String code = alex.getHomeCode();
        Member sam = service.joinHome(code, "Sam").orElseThrow();
        service.joinHome(code, "Kim").orElseThrow();
        List<ChoreTask> tasks = service.tasksOf(code);
        service.complete(tasks.get(0).getId(), sam.getId());
        service.complete(tasks.get(1).getId(), sam.getId());
        service.complete(tasks.get(2).getId(), alex.getId());
        SessionContext.signIn(alex.getId(), code);
        navigate(HomeView.class);

        assertEquals(List.of("Sam", "Alex", "Kim"), leaderboardNames(), "two, one, none");

        // The reset counts from its own instant; back-date the completions so they are not in the
        // same millisecond as the reset (the badge counts doneAt >= reset).
        for (Completion c : completions.findAll()) {
            c.setDoneAt(c.getDoneAt().minusSeconds(60));
            completions.save(c);
        }
        service.resetCounters(code); // every badge back to zero → join order again
        Home home = service.findHome(code).orElseThrow();
        assertEquals(0, service.badgeCount(sam.getId(), home), "Sam's badge is back to zero");
        // A genuinely new HomeView (and ChoresPanel): navigating to the route you are already on
        // keeps the instance, and whether the push-driven rebuild has landed yet is timing.
        // Privacy, not Landing: the landing page forwards a signed-in member straight back.
        navigate(PrivacyView.class);
        navigate(HomeView.class);

        assertEquals(List.of("Alex", "Sam", "Kim"), leaderboardNames(), "ties keep the join order");
    }

    // ---- 💎 on the card -------------------------------------------------------

    /** A chore's reward shows on its card, so a member can see what a tap pays before tapping. */
    @Test
    void choreCard_showsItsCreditValue_andOnlyWhenItHasOne() {
        Member alex = service.createHome("Shiny", "Alex");
        String code = alex.getHomeCode();
        service.addTask(code, "Clean the oven", "🧽", 0, 3);
        SessionContext.signIn(alex.getId(), code);
        navigate(HomeView.class);

        List<Span> pills = $(Span.class).all().stream()
                .filter(sp -> sp.getClassNames().contains("card-credit") && usable(sp))
                .toList();
        assertEquals(1, pills.size(), "the eleven default chores pay nothing; only the oven does");
        assertEquals("💎 3", pills.get(0).getText());
        Div card = (Div) pills.get(0).getParent().orElseThrow().getParent().orElseThrow();
        assertTrue(card.getElement().getTextRecursively().contains("Clean the oven"),
                "the pill sits on the chore it belongs to");
        assertTrue(pills.get(0).getParent().orElseThrow().getElement().getClassList()
                .contains("complete-area"), "inside the tap area: tapping it logs the chore");

        assertFalse($(Div.class).all().stream()
                .filter(d -> d.getClassNames().contains("help-card")
                        || d.getClassNames().contains("add-card"))
                .anyMatch(d -> d.getElement().getTextRecursively().contains("💎")),
                "the 🙋 and ＋ tiles are not chores and carry no pill");
    }

    /** The 💎 card appears once there is something to show, as plain numbers rather than a
     *  second lens picker. */
    @Test
    void myStats_showDiamondsEarned_onlyOnceAnyWereEarned() {
        Member alex = service.createHome("Shiny", "Alex");
        String code = alex.getHomeCode();
        SessionContext.signIn(alex.getId(), code);
        navigate(HomeView.class);
        selectTab("Stats");
        assertEquals(4, statTiles().size(), "no diamonds yet: the picker row only");

        ChoreTask oven = service.addTask(code, "Clean the oven", "🧽", 0, 3);
        service.complete(oven.getId(), alex.getId());
        navigate(PrivacyView.class);
        navigate(HomeView.class);
        selectTab("Stats");

        List<Div> readonly = $(Div.class).all().stream()
                .filter(d -> d.getClassNames().contains("stat-tiles")
                        && d.getClassNames().contains("readonly") && usable(d))
                .toList();
        assertEquals(1, readonly.size(), "one 💎 row");
        List<Div> tiles = readonly.get(0).getChildren().map(Div.class::cast).toList();
        assertEquals(4, tiles.size());
        assertTrue(tiles.get(0).getElement().getTextRecursively().startsWith("3"),
                "three diamonds today");
        assertNull(tiles.get(0).getElement().getAttribute("role"),
                "plain numbers, not a second picker");
        assertTrue($(Div.class).all().stream().anyMatch(d ->
                        d.getClassNames().contains("chart-title")
                                && d.getElement().getTextRecursively().contains("Diamonds")),
                "the card has its own title");
    }

    /** The redeemed card appears once something was redeemed: earned · redeemed · balance as
     *  plain tiles, then the rewards; the admin's Home stats list the same rows with names. */
    @Test
    void stats_showRedeemedDiamonds_onlyOnceAnyWereRedeemed_andNameThemForTheAdmin() {
        Member alex = service.createHome("Shiny", "Alex");
        String code = alex.getHomeCode();
        ChoreTask oven = service.addTask(code, "Clean the oven", "🧽", 0, 5);
        service.complete(oven.getId(), alex.getId());
        service.complete(oven.getId(), alex.getId());
        SessionContext.signIn(alex.getId(), code);
        navigate(HomeView.class);
        selectTab("Stats");
        assertEquals(1, readonlyTileRows().size(), "earned, but nothing redeemed: one 💎 row");
        assertTrue(redemptionRows().isEmpty());

        creditService.redeem(code, alex.getId(), 7, "movie night", alex.getId());
        navigate(PrivacyView.class);
        navigate(HomeView.class);
        selectTab("Stats");

        List<Div> rows = readonlyTileRows();
        assertEquals(2, rows.size(), "the earned row and the redeemed row");
        List<String> tiles = rows.get(1).getChildren()
                .map(t -> t.getElement().getTextRecursively()).toList();
        assertEquals(3, tiles.size());
        assertTrue(tiles.get(0).startsWith("10"), "earned");
        assertTrue(tiles.get(1).startsWith("7"), "redeemed");
        assertTrue(tiles.get(2).startsWith("3"), "and what is left");
        assertTrue(rows.get(1).getChildren().allMatch(
                t -> t.getElement().getAttribute("role") == null), "facts, not a picker");
        List<ListItem> items = redemptionRows();
        assertEquals(1, items.size());
        String row = items.get(0).getElement().getTextRecursively();
        assertTrue(row.contains("movie night") && row.contains("💎 7"), row);
        assertFalse(row.contains("Alex"), "my own list needs no name");

        Tabs sub = $(Tabs.class).all().get(1);
        sub.setSelectedTab(sub.getChildren().filter(Tab.class::isInstance).map(Tab.class::cast)
                .filter(t -> t.getElement().getTextRecursively().contains("Home stats"))
                .findFirst().orElseThrow());
        items = redemptionRows();
        assertEquals(1, items.size(), "the admin's list has the same one row");
        row = items.get(0).getElement().getTextRecursively();
        assertTrue(row.contains("Alex · movie night") && row.contains("💎 7"), row);
    }

    private List<Div> readonlyTileRows() {
        return $(Div.class).all().stream()
                .filter(d -> d.getClassNames().contains("stat-tiles")
                        && d.getClassNames().contains("readonly") && usable(d))
                .toList();
    }

    private List<ListItem> redemptionRows() {
        return $(ListItem.class).all().stream()
                .filter(d -> d.getClassNames().contains("redemption-row") && usable(d))
                .toList();
    }

    /** Home stats gives the admin every member's own "my chores" chart, folded per member. */
    @Test
    void homeStats_foldEachMembersOwnChores_andKeepAnOpenFoldOpenAcrossARedraw() {
        Member alex = service.createHome("Numbers", "Alex");
        String code = alex.getHomeCode();
        Member sam = service.joinHome(code, "Sam").orElseThrow();
        service.joinHome(code, "Kim").orElseThrow();
        List<ChoreTask> tasks = service.tasksOf(code);
        service.complete(tasks.get(0).getId(), alex.getId());
        service.complete(tasks.get(0).getId(), sam.getId());
        service.complete(tasks.get(1).getId(), sam.getId());
        SessionContext.signIn(alex.getId(), code);
        navigate(HomeView.class);
        selectTab("Stats");
        Tabs sub = $(Tabs.class).all().get(1);
        sub.setSelectedTab(sub.getChildren().filter(Tab.class::isInstance).map(Tab.class::cast)
                .filter(t -> t.getElement().getTextRecursively().contains("Home stats"))
                .findFirst().orElseThrow());

        List<Details> folds = memberFolds();
        assertEquals(List.of("Alex · 1", "Sam · 2"),
                folds.stream().map(Details::getSummaryText).toList(),
                "one fold per member who did something; Kim did nothing");
        assertTrue(folds.stream().noneMatch(Details::isOpened), "folded by default");
        assertTrue(folds.get(1).getElement().getTextRecursively()
                .contains(tasks.get(1).getName()), "Sam's fold holds Sam's own bars");

        // Open Sam's, then let a change in the home redraw the panel (HomeView calls refresh()
        // on every HomeState bump): the fold the admin opened stays open, and the data is new.
        Details samFold = folds.get(1);
        samFold.setOpened(true);
        ComponentUtil.fireEvent(samFold, new Details.OpenedChangeEvent(samFold, true));
        service.complete(tasks.get(2).getId(), sam.getId());
        $(StatsPanel.class).first().refresh();

        folds = memberFolds();
        assertEquals("Sam · 3", folds.get(1).getSummaryText(), "the new chore counts");
        assertTrue(folds.get(1).isOpened(), "and the fold did not snap shut");
        assertFalse(folds.get(0).isOpened());
    }

    private List<Details> memberFolds() {
        return $(Details.class).all().stream()
                .filter(d -> d.getClassNames().contains("member-chores") && usable(d))
                .toList();
    }

    // ---- hold-and-drag to arrange the board ---------------------------------------

    /** The gesture is wired for admins only, and only onto real chore cards. */
    @Test
    void board_isDraggableForTheAdmin_onChoreCardsOnly() {
        Member alex = service.createHome("Arranged", "Alex");
        String code = alex.getHomeCode();
        Member sam = service.joinHome(code, "Sam").orElseThrow();

        SessionContext.signIn(alex.getId(), code);
        navigate(HomeView.class);
        Div board = boardSections();
        assertTrue(board.getClassNames().contains("draggable"), "the admin may arrange the board");
        List<Div> cards = $(Div.class).all().stream()
                .filter(d -> d.getClassNames().contains("task-card") && usable(d)).toList();
        assertEquals(11 + 2, cards.size(), "eleven chores plus 🙋 and ＋");
        assertEquals(11, cards.stream()
                .filter(d -> d.getElement().hasAttribute("data-task-id")).count(),
                "every chore card carries its id — and nothing else does");
        assertTrue($(Div.class).all().stream()
                .filter(d -> d.getClassNames().contains("task-grid") && usable(d))
                .allMatch(d -> d.getElement().hasAttribute("data-group-id")),
                "each grid names its section (blank for ungrouped)");

        SessionContext.signIn(sam.getId(), code);
        navigate(PrivacyView.class);
        navigate(HomeView.class);
        assertFalse(boardSections().getClassNames().contains("draggable"),
                "a member's board is not arrangeable");
    }

    /** A drop lands where it was dropped, and a member's drop is thrown away. */
    @Test
    void droppingACard_reordersTheBoard_unlessYouAreNotTheAdmin() {
        Member alex = service.createHome("Arranged", "Alex");
        String code = alex.getHomeCode();
        Member sam = service.joinHome(code, "Sam").orElseThrow();
        List<ChoreTask> before = service.tasksOf(code);
        Long fifth = before.get(4).getId();
        Long first = before.get(0).getId();

        SessionContext.signIn(alex.getId(), code);
        navigate(HomeView.class);
        $(ChoresPanel.class).first().place(fifth, null, first);
        List<ChoreTask> after = service.tasksOf(code);
        assertEquals(fifth, after.get(0).getId(), "the dropped card leads the board");
        assertEquals(first, after.get(1).getId());

        SessionContext.signIn(sam.getId(), code);
        navigate(PrivacyView.class);
        navigate(HomeView.class);
        $(ChoresPanel.class).first().place(first, null, fifth);
        assertEquals(List.of(fifth, first),
                service.tasksOf(code).subList(0, 2).stream().map(ChoreTask::getId).toList(),
                "Sam is not the admin, so nothing moved");
    }

    /** An admin's drop carrying another home's ids is thrown away: the board is not theirs. */
    @Test
    void droppingAnotherHomesCard_isThrownAway() {
        Member alex = service.createHome("Ours", "Alex");
        String theirs = service.createHome("Theirs", "Sam").getHomeCode();
        List<ChoreTask> theirBoard = service.tasksOf(theirs);
        List<Long> theirOrder = theirBoard.stream().map(ChoreTask::getId).toList();

        SessionContext.signIn(alex.getId(), alex.getHomeCode());
        navigate(HomeView.class);
        $(ChoresPanel.class).first().place(theirOrder.get(theirOrder.size() - 1), null,
                theirOrder.get(0));

        assertEquals(theirOrder, service.tasksOf(theirs).stream().map(ChoreTask::getId).toList(),
                "an admin of one home cannot arrange another home's board");
    }

    // ---- the admin gate, seen from the member's side ---------------------------------

    /** A wrong PIN marks the field and reveals nothing: no Admin tab, no admin role, and the
     *  session stays on the member lifetime. */
    @Test
    void claimAdmin_withAWrongPin_revealsNothing() {
        Member admin = service.createHome("Shared", "Alex");
        String code = admin.getHomeCode();
        String realPin = service.findHome(code).orElseThrow().getAdminPin();
        Member sam = service.joinHome(code, "Sam").orElseThrow();
        SessionContext.signIn(sam.getId(), code);
        navigate(HomeView.class);

        clickMenuItem("Admin?");
        setField("Admin PIN", "0000".equals(realPin) ? "1111" : "0000");
        clickButton("Unlock");

        TextField pin = $(TextField.class).all().stream()
                .filter(f -> "Admin PIN".equals(f.getLabel()) && usable(f)).findFirst().orElseThrow();
        assertTrue(pin.isInvalid(), "the dialog stays open and says so");
        assertEquals(3, tabCount(), "no Admin tab");
        assertFalse(service.findMember(sam.getId()).orElseThrow().isAdmin());
        assertEquals(SessionContext.MEMBER_TIMEOUT_SECONDS, maxInactiveInterval());
    }

    /** Demotion reaches the demoted admin's own phone live: the next redraw drops the Admin tab
     *  and the panel with it, lands on Chores, and puts the session back on the member lifetime.
     *  That redraw is the only thing between a demoted member and the admin actions. */
    @Test
    void aDemotedAdmin_losesTheAdminTab_live() {
        Member alex = service.createHome("Shared", "Alex");
        String code = alex.getHomeCode();
        Member sam = service.joinHome(code, "Sam").orElseThrow();
        assertTrue(service.setMemberAdmin(sam.getId(), true));
        SessionContext.signIn(sam.getId(), code);
        navigate(HomeView.class);
        selectTab("Admin");
        assertEquals(4, tabCount());
        assertEquals(SessionContext.ADMIN_TIMEOUT_SECONDS, maxInactiveInterval());

        assertTrue(service.setMemberAdmin(sam.getId(), false)); // Alex, on another phone
        awaitRedraw(() -> tabCount() == 3);

        assertEquals(3, tabCount(), "the Admin tab went with the role");
        assertTrue($(AdminPanel.class).all().stream().noneMatch(this::usable), "and the panel");
        assertTrue($(Tabs.class).first().getSelectedTab().getElement().getTextRecursively().trim()
                .startsWith("Chores"), "landed on the board, not on a tab that is gone");
        assertEquals(SessionContext.MEMBER_TIMEOUT_SECONDS, maxInactiveInterval());
    }

    /** A member an admin removed, and everyone in a home an admin deleted, is shown out on the
     *  next redraw rather than left on a board that is no longer theirs. */
    @Test
    void aRemovedMember_isShownOut_andSoIsEveryoneWhenTheHomeGoes() {
        Member alex = service.createHome("Shared", "Alex");
        String code = alex.getHomeCode();
        Member sam = service.joinHome(code, "Sam").orElseThrow();
        SessionContext.signIn(sam.getId(), code);
        navigate(HomeView.class);

        assertTrue(service.removeMember(sam.getId())); // Alex, on another phone
        awaitRedraw(() -> getCurrentView() instanceof LandingView);
        assertInstanceOf(LandingView.class, getCurrentView(), "removed: back on the landing page");
        assertNull(SessionContext.memberId(), "and signed out");

        Member kim = service.joinHome(code, "Kim").orElseThrow();
        SessionContext.signIn(kim.getId(), code);
        navigate(HomeView.class);
        assertTrue(service.deleteHome(code));
        awaitRedraw(() -> getCurrentView() instanceof LandingView);
        assertInstanceOf(LandingView.class, getCurrentView(), "the home is gone: so is the board");
        assertNull(SessionContext.memberId());
    }

    /** The members card refuses to demote or remove the last admin, so a home can never be
     *  left with nobody to run it — through the row menu, not only in the service. */
    @Test
    void theSoleAdmin_cannotDemoteOrRemoveThemselves_fromTheMembersCard() {
        Member alex = service.createHome("Solo", "Alex");
        String code = alex.getHomeCode();
        SessionContext.signIn(alex.getId(), code);
        navigate(HomeView.class);
        selectTab("Admin");
        openSection("Members");

        clickMenuItem("Demote");
        assertTrue(service.findMember(alex.getId()).orElseThrow().isAdmin(), "the last admin stays one");

        clickMenuItem("Remove member");
        clickButton("Confirm");
        assertTrue(service.findMember(alex.getId()).isPresent(), "and stays a member");
        assertInstanceOf(HomeView.class, getCurrentView(), "still on the board");
        assertEquals(4, tabCount(), "still the admin");
    }

    /** A child with no phone is added from the Members card: name, the agreement ticked on
     *  their behalf, and they appear with "no phone yet" under their name. */
    @Test
    void membersCard_addsAMemberWithoutAPhone_behindTheAgreement() {
        Member alex = service.createHome("Nest", "Alex");
        String code = alex.getHomeCode();
        SessionContext.signIn(alex.getId(), code);
        navigate(HomeView.class);
        selectTab("Admin");
        openSection("Members");

        clickButton("Add a member without a phone");
        setField("Name", "Milla");
        Button add = $(Button.class).all().stream()
                .filter(b -> "Add".equals(labelOf(b)) && usable(b)).findFirst().orElseThrow();
        assertFalse(add.isEnabled(), "disabled until the agreement is accepted on their behalf");
        acceptTerms();
        assertTrue(add.isEnabled());
        clickButton("Add");

        awaitRedraw(() -> adminSection("Members").getSummaryText().equals("Members (2)"));
        Member milla = service.findMemberByName(code, "Milla").orElseThrow();
        assertFalse(milla.hasDevice());
        assertTrue($(Span.class).all().stream().filter(this::usable)
                .anyMatch(sp -> "no phone yet".equals(sp.getText())), "the card says so");
    }

    /** A name of spaces is no name, and a taken one is named back in the refusal. */
    @Test
    void membersCard_addMember_refusesABlankName_andNamesATakenOne() {
        Member alex = service.createHome("Nest", "Alex");
        SessionContext.signIn(alex.getId(), alex.getHomeCode());
        navigate(HomeView.class);
        selectTab("Admin");
        openSection("Members");

        clickButton("Add a member without a phone");
        acceptTerms();
        setField("Name", "   ");
        Button add = $(Button.class).all().stream()
                .filter(b -> "Add".equals(labelOf(b)) && usable(b)).findFirst().orElseThrow();
        assertFalse(add.isEnabled(), "spaces are not a name");

        setField("Name", " Alex ");
        assertTrue(add.isEnabled());
        clickButton("Add");
        Notification refused = $(Notification.class).all().stream()
                .filter(Notification::isOpened).findFirst().orElseThrow();
        assertEquals("There is already a member called Alex", test(refused).getText());
        assertEquals(1, service.membersOf(alex.getHomeCode()).size());
    }

    /** The home-wide numbers are the admin's: a member's Stats has no sub-tabs at all. */
    @Test
    void member_getsNoHomeStatsTab() {
        Member alex = service.createHome("Shared", "Alex");
        Member sam = service.joinHome(alex.getHomeCode(), "Sam").orElseThrow();
        SessionContext.signIn(sam.getId(), sam.getHomeCode());
        navigate(HomeView.class);
        selectTab("Stats");
        assertEquals(1, $(Tabs.class).all().stream().filter(this::usable).count(),
                "only the main tabs: no My stats / Home stats switch for a member");
        assertFalse($(Tab.class).all().stream().anyMatch(
                t -> t.getElement().getTextRecursively().contains("Home stats")));
    }

    /** A blank note reads "(no note)", and past ten rows the card says how many it left out. */
    @Test
    void stats_redeemedList_saysNoNote_andCountsTheRowsTheCapHid() {
        Member alex = service.createHome("Shiny", "Alex");
        String code = alex.getHomeCode();
        ChoreTask oven = service.addTask(code, "Clean the oven", "🧽", 0, 20);
        service.complete(oven.getId(), alex.getId());
        for (int i = 1; i <= 11; i++) {
            assertTrue(creditService.redeem(code, alex.getId(), 1, "treat " + i, alex.getId()));
        }
        assertTrue(creditService.redeem(code, alex.getId(), 1, "   ", alex.getId()));
        SessionContext.signIn(alex.getId(), code);
        navigate(HomeView.class);
        selectTab("Stats");

        List<ListItem> items = redemptionRows();
        assertEquals(10, items.size(), "a phone screen's worth");
        String newest = items.get(0).getElement().getTextRecursively();
        assertTrue(newest.contains("(no note)"), newest);
        assertFalse(newest.contains("Redeemed"), "the stored marker never shows as a reward");
        assertTrue(items.get(9).getElement().getTextRecursively().contains("treat 3"),
                "treats 1 and 2 are the ones the cap hid");
        assertTrue($(Paragraph.class).all().stream().filter(this::usable)
                .anyMatch(p -> "…and 2 earlier".equals(p.getText())), "and the card says so");
    }

    private Div boardSections() {
        return $(Div.class).all().stream()
                .filter(d -> d.getClassNames().contains("board-sections") && usable(d))
                .findFirst().orElseThrow();
    }


    /** The redraw that drops the Admin tab cannot close a dialog that was already open — it
     *  hangs off the UI, not the panel — so the action behind it re-checks the role itself:
     *  a demoted admin's Save does nothing but say why. */
    @Test
    void aDemotedAdmins_openDialog_noLongerActs() {
        Member alex = service.createHome("Shared", "Alex");
        String code = alex.getHomeCode();
        Member sam = service.joinHome(code, "Sam").orElseThrow();
        assertTrue(service.setMemberAdmin(sam.getId(), true));
        SessionContext.signIn(sam.getId(), code);
        navigate(HomeView.class);
        selectTab("Admin");
        openSection("Members");
        clickMenuItem("Rename member"); // Alex's row: first by join time
        setField("Name", "Hacked");

        assertTrue(service.setMemberAdmin(sam.getId(), false)); // Alex, on another phone
        awaitRedraw(() -> tabCount() == 3);
        clickButton("Save");

        assertEquals("Alex", service.findMember(alex.getId()).orElseThrow().getName(),
                "the stale dialog's Save did nothing");
    }
}
