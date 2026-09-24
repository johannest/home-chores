package com.homechores.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.homechores.domain.CustomList;
import com.homechores.domain.ListItem;
import com.homechores.domain.ListKind;
import com.homechores.domain.Member;
import com.homechores.service.ChoreService;
import com.homechores.service.ListItemService;
import com.homechores.service.ListReminderService;
import com.homechores.service.WebPushSender;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.testbench.unit.SpringUIUnitTest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Custom named lists, editing a line's text, changing its reminder, and the swipe tray's buttons.
 * Push is reported enabled so the reminder badge and bell are on the page (see
 * {@link ListReminderUiTest}).
 */
@SpringBootTest
class ListEditUiTest extends SpringUIUnitTest {

    @Autowired ChoreService service;
    @Autowired ListItemService lists;
    @Autowired ListReminderService reminders;

    @MockitoBean WebPushSender sender;

    @BeforeEach
    void pushIsConfigured() {
        when(sender.isEnabled()).thenReturn(true);
        when(sender.publicKey()).thenReturn("test-key");
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

    private Member signedIn(String name) {
        Member m = service.createHome("Listy", name);
        SessionContext.signIn(m.getId(), m.getHomeCode());
        navigate(HomeView.class);
        return m;
    }

    private Tabs kindTabs() {
        return $(Tabs.class).all().stream()
                .filter(t -> t.getClassNames().contains("list-kind-tabs") && usable(t))
                .findFirst().orElseThrow();
    }

    private void openLists(int kindIndex) {
        Tabs tabs = $(Tabs.class).first();
        Tab lists = tabs.getChildren().filter(Tab.class::isInstance).map(Tab.class::cast)
                .filter(t -> t.getElement().getTextRecursively().trim().startsWith("Lists"))
                .findFirst().orElseThrow();
        tabs.setSelectedTab(lists);
        kindTabs().setSelectedIndex(kindIndex);
    }

    private List<Div> rows() {
        return $(Div.class).all().stream()
                .filter(d -> d.getClassNames().contains("list-item") && usable(d))
                .toList();
    }

    private <T extends Dialog> List<T> open(Class<T> type) {
        return $(type).all().stream().filter(Dialog::isOpened).toList();
    }

    private Button button(Component in, String cls) {
        return $(Button.class, in).all().stream()
                .filter(b -> b.getClassNames().contains(cls)).findFirst().orElseThrow();
    }

    private Button footerButton(Dialog d, String text) {
        return d.getFooter().getElement().getChildren()
                .map(e -> e.getComponent().orElse(null))
                .filter(c -> c instanceof Button b && text.equals(b.getText()))
                .map(Button.class::cast).findFirst().orElseThrow();
    }

    private void clickMenuItem(String text) {
        MenuItem item = $(MenuBar.class).all().stream().filter(this::usable)
                .flatMap(m -> m.getItems().stream())
                .flatMap(i -> java.util.stream.Stream.concat(java.util.stream.Stream.of(i),
                        i.getSubMenu().getItems().stream()))
                .filter(i -> text.equals(i.getElement().getTextRecursively().trim()))
                .findFirst().orElseThrow(() -> new AssertionError("No menu item: " + text));
        ComponentUtil.fireEvent(item, new ClickEvent<>(item));
    }

    @Test
    void thePlusTab_namesANewList_selectsIt_andItsLinesStayOffToDo() {
        Member alex = signedIn("Alex");
        String code = alex.getHomeCode();
        openLists(0);
        Tabs tabs = kindTabs();
        assertEquals(4, tabs.getComponentCount(), "three built-ins and the +");

        tabs.setSelectedIndex(3);
        TextPromptDialog d = open(TextPromptDialog.class).get(0);
        assertEquals(0, kindTabs().getSelectedIndex(), "+ is an action, not a place to stay");
        test(d.field).setValue("Hardware store");
        test(footerButton(d, "Create")).click();

        assertEquals(1, lists.customLists(code).size());
        Tabs after = kindTabs();
        assertEquals(5, after.getComponentCount());
        assertEquals(3, after.getSelectedIndex(), "the new list is showing");
        assertTrue(after.getElement().getTextRecursively().contains("Hardware store"));

        $(com.vaadin.flow.component.textfield.TextField.class).all().stream()
                .filter(f -> "Add an item…".equals(f.getPlaceholder()) && usable(f))
                .findFirst().ifPresent(f -> test(f).setValue("Screws"));
        $(Button.class).all().stream().filter(b -> "Add".equals(b.getText()) && usable(b))
                .findFirst().ifPresent(b -> test(b).click());
        Long listId = lists.customLists(code).get(0).getId();
        assertEquals(1, lists.openItems(code, ListKind.TODO, listId).size());
        assertTrue(lists.openItems(code, ListKind.TODO).isEmpty(), "not on the built-in To-do");

        kindTabs().setSelectedIndex(1);
        assertTrue(rows().isEmpty(), "To-do does not show the hardware list's screws");
    }

    @Test
    void aBlankName_keepsTheDialogOpen() {
        signedIn("Alex");
        openLists(0);
        kindTabs().setSelectedIndex(3);
        TextPromptDialog d = open(TextPromptDialog.class).get(0);
        test(footerButton(d, "Create")).click();
        assertTrue(d.isOpened());
        assertTrue(d.field.isInvalid());
    }

    @Test
    void deletingAList_asksFirst_thenLandsOnToDo() {
        Member alex = signedIn("Alex");
        String code = alex.getHomeCode();
        CustomList gifts = lists.createList(code, alex.getId(), "Gifts").orElseThrow();
        lists.add(code, ListKind.TODO, gifts.getId(), alex.getId(), "Book");
        openLists(3);
        assertEquals(1, rows().size());

        clickMenuItem("Delete list");
        Dialog confirm = $(Dialog.class).all().stream()
                .filter(d -> d.isOpened() && d.getClassNames().contains("delete-list-dialog"))
                .findFirst().orElseThrow();
        assertEquals(1, lists.customLists(code).size(), "nothing gone before the confirm");
        test(footerButton(confirm, "Delete")).click();

        assertTrue(lists.customLists(code).isEmpty());
        assertEquals(1, kindTabs().getSelectedIndex(), "back on To-do");
        assertEquals(4, kindTabs().getComponentCount());
    }

    @Test
    void renamingAList_relabelsItsTab() {
        Member alex = signedIn("Alex");
        String code = alex.getHomeCode();
        lists.createList(code, alex.getId(), "Gifts");
        openLists(3);

        clickMenuItem("Rename list");
        TextPromptDialog d = open(TextPromptDialog.class).get(0);
        assertEquals("Gifts", d.field.getValue(), "starts on the current name");
        test(d.field).setValue("Birthday gifts");
        test(footerButton(d, "Save")).click();

        assertEquals("Birthday gifts", lists.customLists(code).get(0).getName());
        assertTrue(kindTabs().getElement().getTextRecursively().contains("Birthday gifts"));
        assertEquals(3, kindTabs().getSelectedIndex(), "still on it");
    }

    @Test
    void anotherPhoneDeletingTheShownList_fallsBackToToDo() {
        Member alex = signedIn("Alex");
        String code = alex.getHomeCode();
        CustomList gifts = lists.createList(code, alex.getId(), "Gifts").orElseThrow();
        openLists(3);
        Member sam = service.joinHome(code, "Sam").orElseThrow();

        lists.deleteList(code, gifts.getId(), sam.getId()); // bumps the home: the board redraws

        assertEquals(1, kindTabs().getSelectedIndex());
    }

    @Test
    void tappingTheText_editsTheLine() {
        Member alex = signedIn("Alex");
        ListItem milk = lists.add(alex.getHomeCode(), ListKind.GROCERY, alex.getId(), "Mlik").orElseThrow();
        openLists(0);

        Div text = $(Div.class, rows().get(0)).all().stream()
                .filter(d -> d.getClassNames().contains("list-text")).findFirst().orElseThrow();
        test(text).click();
        TextPromptDialog d = open(TextPromptDialog.class).get(0);
        assertEquals("Mlik", d.field.getValue());
        test(d.field).setValue("Milk");
        test(footerButton(d, "Save")).click();

        assertFalse(d.isOpened());
        assertEquals("Milk", lists.find(milk.getId()).orElseThrow().getText());
        assertTrue(rows().get(0).getElement().getTextRecursively().contains("Milk"));
    }

    @Test
    void theSwipeTray_editsAndDeletes() {
        Member alex = signedIn("Alex");
        String code = alex.getHomeCode();
        lists.add(code, ListKind.TODO, alex.getId(), "Call the plumber");
        openLists(1);

        Div row = rows().get(0);
        Div tray = $(Div.class, row).all().stream()
                .filter(d -> d.getClassNames().contains("swipe-actions")).findFirst().orElseThrow();
        assertEquals("true", tray.getElement().getAttribute("aria-hidden"),
                "the tray repeats the face's actions, so assistive tech is not shown it twice");

        test(button(tray, "swipe-edit")).click();
        assertEquals(1, open(TextPromptDialog.class).size(), "Edit opens the same dialog as a tap");
        open(TextPromptDialog.class).get(0).close();

        test(button(tray, "swipe-delete")).click();
        assertTrue(lists.openItems(code, ListKind.TODO).isEmpty());
        assertTrue(rows().isEmpty());
    }

    @Test
    void tappingTheReminderBadge_opensItsTime_readyToChange() {
        Member alex = signedIn("Alex");
        String code = alex.getHomeCode();
        ListItem line = lists.add(code, ListKind.TODO, alex.getId(), "Call the plumber").orElseThrow();
        Instant due = reminders.schedule(line.getId(), alex.getId(),
                Instant.now().plus(Duration.ofDays(2)), Locale.ENGLISH);
        openLists(1);

        Span badge = $(Span.class, rows().get(0)).all().stream()
                .filter(s -> s.getClassNames().contains("list-reminder")).findFirst().orElseThrow();
        test(badge).click();

        ListReminderDialog d = open(ListReminderDialog.class).get(0);
        DateTimePicker picker = $(DateTimePicker.class, d).first();
        assertTrue(usable(picker), "the picker is unfolded straight away");
        assertEquals(LocalDateTime.ofInstant(due, SessionContext.timeZone()).truncatedTo(ChronoUnit.MINUTES),
                picker.getValue(), "on the reminder's own time, not tomorrow 9:00");
        assertTrue($(Button.class, d).all().stream().anyMatch(b -> "Update reminder".equals(b.getText())));
    }
}
