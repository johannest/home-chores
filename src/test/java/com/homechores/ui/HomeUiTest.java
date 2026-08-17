package com.homechores.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.homechores.domain.Home;
import com.homechores.domain.Member;
import com.homechores.service.ChoreService;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.testbench.unit.SpringUIUnitTest;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class HomeUiTest extends SpringUIUnitTest {

    @Autowired
    ChoreService service;

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

    private void setField(String label, String value) {
        TextField f = $(TextField.class).all().stream()
                .filter(x -> label.equals(x.getLabel()) && usable(x))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No usable field: " + label));
        f.setValue(value);
    }

    private int tabCount() {
        return (int) $(Tabs.class).first().getChildren().count();
    }

    private boolean hasSpanWithText(String text) {
        return $(Span.class).all().stream().anyMatch(s -> text.equals(s.getText()));
    }

    // ---- tests --------------------------------------------------------------

    @Test
    void createHome_navigatesToBoard_asAdmin_withThreeTabs() {
        navigate(LandingView.class);
        setField("Home name", "Test Home");
        setField("Your name", "Alex");
        clickButton("Create home 🚀");
        clickButton("Let's go 🚀"); // dismiss the "home created / here's your PIN" dialog

        assertInstanceOf(HomeView.class, getCurrentView());
        assertEquals(3, tabCount(), "admin sees Chores + Stats + Admin");
        assertTrue(hasSpanWithText("Alex"), "creator appears on the leaderboard");
    }

    @Test
    void joinHome_navigatesToBoard_asMember_withTwoTabs() {
        Member admin = service.createHome("Shared", "Alex");

        navigate(LandingView.class);
        $(Tabs.class).first().setSelectedIndex(1); // switch to the "Join a home" tab
        setField("Home code", admin.getHomeCode());
        setField("Your name", "Sam");
        clickButton("Join home 🙌");

        assertInstanceOf(HomeView.class, getCurrentView());
        assertEquals(2, tabCount(), "a plain member has no Admin tab");
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
        clickButton("Add chore");
        setField("Chore name", "Iron shirts");
        clickButton("Save");

        assertEquals(12, service.tasksOf(code).size(), "new chore persisted (11 seeded + 1)");
    }

    /**
     * The recovery path a family member takes after clearing their browser storage: pick
     * yourself out of the member list instead of joining again under the same name.
     */
    @Test
    void rejoinPicker_signsBackInAsTheExistingMember_withoutDuplicating() {
        Member admin = service.createHome("Shared", "Alex");
        Member sam = service.joinHome(admin.getHomeCode(), "Sam").orElseThrow();
        Home home = service.findHome(admin.getHomeCode()).orElseThrow();
        home.setApproveRejoin(false); // no gate: the picker signs in straight away
        service.saveHome(home);

        navigate(LandingView.class);
        $(Tabs.class).first().setSelectedIndex(1); // "Join a home"
        setField("Home code", admin.getHomeCode());
        clickButton("I'm already a member — sign me back in");

        // Two rows, one per member; tap the "That's me" next to Sam.
        assertEquals(2, $(Button.class).all().stream()
                .filter(b -> "That's me".equals(b.getText())).count());
        clickButton("That's me"); // rows are listed join-order, so Alex is first
        assertInstanceOf(HomeView.class, getCurrentView());
        assertEquals(admin.getId(), SessionContext.memberId(), "signed in as the existing Alex");
        assertEquals(2, service.membersOf(admin.getHomeCode()).size(), "no third member created");
        assertEquals(sam.getId(), service.membersOf(admin.getHomeCode()).get(1).getId());
    }

    /** With the gate on and no PIN, the picker parks the device in the approval queue. */
    @Test
    void rejoinPicker_withGateOn_raisesARequestInsteadOfSigningIn() {
        Member admin = service.createHome("Shared", "Alex");
        assertTrue(service.findHome(admin.getHomeCode()).orElseThrow().isApproveRejoin());

        navigate(LandingView.class);
        $(Tabs.class).first().setSelectedIndex(1);
        setField("Home code", admin.getHomeCode());
        clickButton("I'm already a member — sign me back in");
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

    @Test
    void member_doesNotSeeAdminTab() {
        Member admin = service.createHome("Shared", "Alex");
        Member sam = service.joinHome(admin.getHomeCode(), "Sam").orElseThrow();
        SessionContext.signIn(sam.getId(), sam.getHomeCode());
        navigate(HomeView.class);
        assertEquals(2, tabCount());
    }
}
