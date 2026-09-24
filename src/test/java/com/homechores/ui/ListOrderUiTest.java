package com.homechores.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.homechores.domain.CustomList;
import com.homechores.domain.ListKind;
import com.homechores.domain.Member;
import com.homechores.service.ChoreService;
import com.homechores.service.ListItemService;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.checkbox.Switch;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.testbench.unit.SpringUIUnitTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Admin → Lists: ordering the Lists tab and switching the default lists off. */
@SpringBootTest
class ListOrderUiTest extends SpringUIUnitTest {

    @Autowired ChoreService service;
    @Autowired ListItemService lists;

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

    private Member signedIn(String name) {
        Member m = service.createHome("Listy", name);
        SessionContext.signIn(m.getId(), m.getHomeCode());
        navigate(HomeView.class);
        return m;
    }

    private void mainTab(String label) {
        Tabs tabs = $(Tabs.class).first();
        tabs.setSelectedTab(tabs.getChildren().filter(Tab.class::isInstance).map(Tab.class::cast)
                .filter(t -> t.getElement().getTextRecursively().trim().startsWith(label))
                .findFirst().orElseThrow());
    }

    private Tabs kindTabs() {
        return $(Tabs.class).all().stream()
                .filter(t -> t.getClassNames().contains("list-kind-tabs") && usable(t))
                .findFirst().orElseThrow();
    }

    /** Tab labels on the Lists panel, without the trailing "+". */
    private List<String> listTabs() {
        mainTab("Lists");
        List<String> labels = kindTabs().getChildren()
                .filter(c -> !c.getClassNames().contains("list-new-tab"))
                .map(c -> c.getElement().getTextRecursively().trim()).toList();
        return labels;
    }

    private List<Div> adminRows() {
        return $(Div.class).all().stream()
                .filter(d -> d.getClassNames().contains("admin-list-row") && usable(d)).toList();
    }

    private Div adminRow(String label) {
        return adminRows().stream()
                .filter(d -> d.getElement().getTextRecursively().startsWith(label))
                .findFirst().orElseThrow(() -> new AssertionError("No admin list row " + label));
    }

    private void rowMenu(Div row, String text) {
        MenuItem item = $(MenuBar.class, row).first().getItems().get(0).getSubMenu().getItems().stream()
                .filter(i -> text.equals(i.getElement().getTextRecursively().trim()))
                .findFirst().orElseThrow();
        ComponentUtil.fireEvent(item, new ClickEvent<>(item));
    }

    @Test
    void theCardListsEveryList_withASwitchOnlyForTheBuiltIns() {
        Member alex = signedIn("Alex");
        lists.createList(alex.getHomeCode(), alex.getId(), "Gifts");
        mainTab("Admin");

        assertEquals(4, adminRows().size());
        assertEquals(3, adminRows().stream().filter(r -> !$(Switch.class, r).all().isEmpty()).count(),
                "own lists are deleted by members, not switched off");
        assertTrue($(Switch.class, adminRow("Groceries")).first().getValue(), "on by default");
    }

    @Test
    void movingAListInSettings_reordersTheListsTab() {
        Member alex = signedIn("Alex");
        assertEquals(List.of("Groceries", "To-do", "Dinner"), listTabs()); // Groceries is showing
        mainTab("Admin");
        rowMenu(adminRow("Dinner"), "Move up");
        rowMenu(adminRow("Dinner"), "Move up");

        assertEquals(List.of("Dinner", "Groceries", "To-do"), listTabs());
        assertEquals("Groceries", kindTabs().getSelectedTab().getElement().getTextRecursively().trim(),
                "the list on screen follows its tab, not the position");
    }

    @Test
    void theListsTabOpensOnTheFirstListInTheAdminsOrder() {
        Member alex = service.createHome("Listy", "Alex");
        lists.moveList(alex.getHomeCode(), alex.getId(), "TODO", -1);
        SessionContext.signIn(alex.getId(), alex.getHomeCode());
        navigate(HomeView.class);

        assertEquals(List.of("To-do", "Groceries", "Dinner"), listTabs());
        assertEquals(0, kindTabs().getSelectedIndex(), "To-do is first, so it is what opens");
    }

    @Test
    void switchingAListOff_removesItsTab_andOnBringsItBack() {
        Member alex = signedIn("Alex");
        lists.add(alex.getHomeCode(), ListKind.GROCERY, alex.getId(), "Milk");
        mainTab("Admin");
        $(Switch.class, adminRow("Groceries")).first().setValue(false);

        assertTrue(adminRow("Groceries").getClassNames().contains("list-off"));
        assertEquals(List.of("To-do", "Dinner"), listTabs());

        mainTab("Admin");
        $(Switch.class, adminRow("Groceries")).first().setValue(true);
        assertEquals(List.of("Groceries", "To-do", "Dinner"), listTabs());
        assertEquals(1, lists.openItems(alex.getHomeCode(), ListKind.GROCERY).size(), "the milk came back with it");
    }

    @Test
    void theListOnScreenBeingSwitchedOff_landsOnToDo() {
        Member alex = signedIn("Alex");
        mainTab("Lists");
        kindTabs().setSelectedIndex(2); // Dinner
        lists.setListEnabled(alex.getHomeCode(), alex.getId(), ListKind.DINNER, false); // "another phone"
        runPendingSignalsTasks(); // the shared revision reaches this UI through a queued effect

        assertEquals(1, kindTabs().getSelectedIndex());
        assertEquals("To-do", kindTabs().getSelectedTab().getElement().getTextRecursively().trim());
    }

    @Test
    void withToDoOff_aDeletedCustomListFallsBackToTheFirstList() {
        Member alex = signedIn("Alex");
        String code = alex.getHomeCode();
        CustomList gifts = lists.createList(code, alex.getId(), "Gifts").orElseThrow();
        lists.setListEnabled(code, alex.getId(), ListKind.TODO, false);
        mainTab("Lists");
        kindTabs().setSelectedIndex(2); // Groceries, Dinner, Gifts
        lists.deleteList(code, gifts.getId(), alex.getId());
        runPendingSignalsTasks();

        assertEquals(0, kindTabs().getSelectedIndex());
        assertEquals("Groceries", kindTabs().getSelectedTab().getElement().getTextRecursively().trim());
    }

    @Test
    void everythingOff_leavesOnlyThePlusTab_andSaysHowToGetListsBack() {
        Member alex = signedIn("Alex");
        String code = alex.getHomeCode();
        for (ListKind k : ListItemService.BUILT_INS) {
            lists.setListEnabled(code, alex.getId(), k, false);
        }
        assertTrue(listTabs().isEmpty());
        assertEquals(1, kindTabs().getComponentCount(), "just the +");
        assertTrue($(Paragraph.class).all().stream().anyMatch(p -> usable(p)
                && p.getText().startsWith("No lists are switched on")));
        assertFalse($(Div.class).all().stream().anyMatch(d -> d.getClassNames().contains("list-add-row") && usable(d)),
                "nothing to add to");
    }
}
