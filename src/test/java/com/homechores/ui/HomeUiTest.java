package com.homechores.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.homechores.domain.ChoreTask;
import com.homechores.domain.Completion;
import com.homechores.domain.CompletionRepository;
import com.homechores.domain.Home;
import com.homechores.domain.Member;
import com.homechores.domain.Season;
import com.homechores.service.ChoreService;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.testbench.unit.SpringUIUnitTest;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class HomeUiTest extends SpringUIUnitTest {

    @Autowired
    ChoreService service;

    @Autowired
    CompletionRepository completions;

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

    @Test
    void createHome_navigatesToBoard_asAdmin_withThreeTabs() {
        navigate(LandingView.class);
        setField("Home name", "Test Home");
        setField("Your name", "Alex");
        acceptTerms();
        clickButton("Create home 🚀");
        clickButton("Let's go 🚀"); // dismiss the "home created / here's your PIN" dialog

        assertInstanceOf(HomeView.class, getCurrentView());
        assertEquals(3, tabCount(), "admin sees Chores + Stats + Admin");
        assertTrue(hasSpanWithText("Alex"), "creator appears on the leaderboard");
    }

    @Test
    void joinHome_navigatesToBoard_asMember_withTwoTabs() {
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
        assertEquals(2, tabCount(), "a plain member has no Admin tab");
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
        assertEquals(2, tabCount());

        clickButton("Admin?");
        setField("Admin PIN", home.getAdminPin());
        clickButton("Unlock");

        assertEquals(3, tabCount(), "correct PIN unlocks the Admin tab");
    }

    @Test
    void admin_canAddChore_fromAdminPanel() {
        Member admin = service.createHome("Shared", "Alex");
        String code = admin.getHomeCode();
        SessionContext.signIn(admin.getId(), code);
        navigate(HomeView.class);

        // Switch to the Admin tab (index 2) and add a chore.
        $(Tabs.class).first().setSelectedIndex(2);
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

        $(Tabs.class).first().setSelectedIndex(2);
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

        $(Tabs.class).first().setSelectedIndex(2);
        openSection("Chores");
        // Every row carries "Move up"; the first one is disabled, so the second row's is the
        // first usable one.
        $(Button.class).all().stream()
                .filter(b -> "Move up".equals(b.getAriaLabel().orElse("")) && b.isEnabled()
                        && usable(b))
                .findFirst().orElseThrow()
                .click();

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

    /** The wipe is irreversible, so a wrong code must not delete anything. */
    @Test
    void deleteHome_requiresTheHomeCodeTyped_correctly() {
        Member admin = service.createHome("Doomed", "Alex");
        String code = admin.getHomeCode();
        SessionContext.signIn(admin.getId(), code);
        navigate(HomeView.class);
        $(Tabs.class).first().setSelectedIndex(2); // Admin tab
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
        $(Tabs.class).first().setSelectedIndex(2); // Admin tab
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
        $(Tabs.class).first().setSelectedIndex(2); // Admin tab

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
        $(Tabs.class).first().setSelectedIndex(2);

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
        $(Tabs.class).first().setSelectedIndex(2); // Admin tab
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
        clickButton("Admin?");
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
        assertEquals(2, tabCount());
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

        MenuBar appearance = $(MenuBar.class).all().stream()
                .filter(m -> m.getClassNames().contains("appearance-menu") && usable(m))
                .findFirst().orElseThrow(() -> new AssertionError("No appearance menu"));

        List<String> labels = appearance.getItems().get(0).getSubMenu().getItems().stream()
                .map(i -> i.getElement().getTextRecursively().trim())
                .filter(t -> !t.isEmpty())
                .toList();
        assertTrue(labels.stream().anyMatch(l -> l.contains("Dark")), "the scheme rows survive");
        for (String palette : List.of("Green", "Pink", "Blue", "Grey")) {
            assertTrue(labels.stream().anyMatch(l -> l.contains(palette)),
                    palette + " is offered; had " + labels);
        }
        assertTrue($(com.vaadin.flow.component.select.Select.class).all().stream()
                        .filter(this::usable).count() <= 1,
                "only the language select is left — the theme select gave up its width");
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
        $(Tabs.class).first().setSelectedIndex(2);
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
        $(Tabs.class).first().setSelectedIndex(2);

        for (String title : List.of("Home settings", "Backup", "Danger zone", "Chores",
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
        $(Tabs.class).first().setSelectedIndex(2);

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
        $(Tabs.class).first().setSelectedIndex(2);
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
        $(Tabs.class).first().setSelectedIndex(2);
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
        $(Tabs.class).first().setSelectedIndex(2);

        toggleSection("Pending approvals", false);
        service.addTask(code, "Sweep the porch", "🧹", 0);

        assertFalse(adminSection("Pending approvals").isOpened(),
                "folding a card away is also a choice worth remembering");
    }
}
