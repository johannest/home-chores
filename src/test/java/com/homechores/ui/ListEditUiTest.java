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
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
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
class ListEditUiTest extends HomeChoresUiTest {

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
        awaitRedraw(() -> kindTabs().getSelectedIndex() == 1);

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
    void theUndoToast_opensAtTheBottom_notUnderTheCameraIsland() {
        Member alex = signedIn("Alex");
        lists.add(alex.getHomeCode(), ListKind.TODO, alex.getId(), "Call the plumber");
        openLists(1);
        Div tray = $(Div.class, rows().get(0)).all().stream()
                .filter(d -> d.getClassNames().contains("swipe-actions")).findFirst().orElseThrow();

        test(button(tray, "swipe-delete")).click();

        Notification undo = $(Notification.class).all().stream()
                .filter(Notification::isOpened).findFirst().orElseThrow();
        assertEquals(Notification.Position.BOTTOM_CENTER, undo.getPosition());
        assertTrue(undo.getElement().getTextRecursively().contains("Undo"));
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

    /** Lines with a reminder sort by when they are due — soon ones up, far ones down — and only
     *  the plain lines between them can be lifted; a drop through the panel rearranges them. */
    @Test
    void remindersSortByDueDate_andHoldingAndDraggingAPlainLine_reordersIt() {
        Member alex = signedIn("Alex");
        String code = alex.getHomeCode();
        ListItem milk = lists.add(code, ListKind.TODO, alex.getId(), "Milk").orElseThrow();
        ListItem plumber = lists.add(code, ListKind.TODO, alex.getId(), "Call the plumber").orElseThrow();
        ListItem eggs = lists.add(code, ListKind.TODO, alex.getId(), "Eggs").orElseThrow();
        ListItem taxes = lists.add(code, ListKind.TODO, alex.getId(), "Taxes").orElseThrow();
        reminders.schedule(plumber.getId(), alex.getId(), Instant.now().plus(Duration.ofDays(20)), Locale.ENGLISH);
        reminders.schedule(taxes.getId(), alex.getId(), Instant.now().plus(Duration.ofMinutes(5)), Locale.ENGLISH);
        openLists(1);

        assertEquals(List.of("Taxes", "Milk", "Eggs", "Call the plumber"), rowTexts(),
                "due in five minutes leads, due in twenty days trails, the plain lines between");
        Div list = (Div) rows().get(0).getParent().orElseThrow();
        assertTrue(list.getClassNames().contains("draggable"), "anyone in the home may arrange it");
        assertEquals(List.of(false, true, true, false),
                rows().stream().map(r -> r.getElement().hasAttribute("data-item-id")).toList(),
                "only the plain lines carry the id a hold can lift by");

        $(ListPanel.class).first().place(eggs.getId(), milk.getId());
        awaitRedraw(() -> rowTexts().equals(List.of("Taxes", "Eggs", "Milk", "Call the plumber")));
        assertEquals(List.of("Taxes", "Eggs", "Milk", "Call the plumber"), rowTexts(), "eggs before milk");
        assertEquals(List.of("Eggs", "Milk"), lists.openItems(code, ListKind.TODO).stream()
                .filter(l -> !l.equals(plumber) && !l.getId().equals(taxes.getId()) && !l.getId().equals(plumber.getId()))
                .map(ListItem::getText).toList(), "written through to the family's order");

        $(ListPanel.class).first().place(milk.getId(), 999_999L);
        assertEquals(List.of("Taxes", "Eggs", "Milk", "Call the plumber"), rowTexts(),
                "a neighbour that is not ours moves nothing, and the preview is redrawn");
    }

    /** Both ids of a drop come from the browser. Another home's line — dragged, or named as the
     *  neighbour — moves nothing in either home; a ticked line sits in a section that is no drop
     *  target and cannot be dragged back; and a custom list is arranged the same way, without a
     *  neighbour from the built-in list. */
    @Test
    void aDropNamingAnotherHomesLine_movesNothing_andACustomListArrangesToo() {
        Member alex = signedIn("Alex");
        String code = alex.getHomeCode();
        ListItem milk = lists.add(code, ListKind.TODO, alex.getId(), "Milk").orElseThrow();
        ListItem eggs = lists.add(code, ListKind.TODO, alex.getId(), "Eggs").orElseThrow();
        Member kim = service.createHome("Elsewhere", "Kim");
        String theirCode = kim.getHomeCode();
        ListItem theirs = lists.add(theirCode, ListKind.TODO, kim.getId(), "Theirs").orElseThrow();
        lists.add(theirCode, ListKind.TODO, kim.getId(), "Theirs too");
        openLists(1);
        ListPanel panel = $(ListPanel.class).first();

        panel.place(theirs.getId(), milk.getId());
        panel.place(eggs.getId(), theirs.getId());
        assertEquals(List.of("Milk", "Eggs"), rowTexts(), "nothing moved here");
        assertEquals(List.of("Theirs", "Theirs too"), lists.openItems(theirCode, ListKind.TODO).stream()
                .map(ListItem::getText).toList(), "nor there");

        lists.setDone(milk.getId(), alex.getId(), true);
        awaitRedraw(() -> rows().stream().anyMatch(r -> r.getClassNames().contains("done")));
        Div ticked = rows().stream().filter(r -> r.getClassNames().contains("done")).findFirst().orElseThrow();
        assertFalse(ticked.getElement().hasAttribute("data-item-id"), "a ticked line cannot be lifted");
        assertFalse(((Div) ticked.getParent().orElseThrow()).getClassNames().contains("draggable"),
                "and the Done section is no drop target");
        panel.place(milk.getId(), eggs.getId());
        assertEquals(List.of("Eggs"), lists.openItems(code, ListKind.TODO).stream()
                .map(ListItem::getText).toList(), "a ticked line is not arranged");

        CustomList gifts = lists.createList(code, alex.getId(), "Gifts").orElseThrow();
        ListItem socks = lists.add(code, ListKind.TODO, gifts.getId(), alex.getId(), "Socks").orElseThrow();
        ListItem book = lists.add(code, ListKind.TODO, gifts.getId(), alex.getId(), "Book").orElseThrow();
        panel.show(ListKind.TODO, gifts.getId());
        assertEquals(List.of("Socks", "Book"), rowTexts());
        assertTrue(((Div) rows().get(0).getParent().orElseThrow()).getClassNames().contains("draggable"));
        panel.place(book.getId(), socks.getId());
        awaitRedraw(() -> rowTexts().equals(List.of("Book", "Socks")));
        assertEquals(List.of("Book", "Socks"), rowTexts(), "a custom list is the family's to arrange too");
        panel.place(socks.getId(), eggs.getId());
        assertEquals(List.of("Book", "Socks"), rowTexts(), "a neighbour on the To-do list is no neighbour here");
    }

    /** ↑/↓ are the keyboard path to arranging a list: on the plain lines only, disabled at the
     *  ends of the plain run, and a press moves the line the way a drop there would. */
    @Test
    void theArrows_sitOnPlainLinesOnly_stopAtTheEnds_andMoveALine() {
        Member alex = signedIn("Alex");
        String code = alex.getHomeCode();
        ListItem milk = lists.add(code, ListKind.TODO, alex.getId(), "Milk").orElseThrow();
        lists.add(code, ListKind.TODO, alex.getId(), "Eggs");
        lists.add(code, ListKind.TODO, alex.getId(), "Bread");
        ListItem taxes = lists.add(code, ListKind.TODO, alex.getId(), "Taxes").orElseThrow();
        reminders.schedule(taxes.getId(), alex.getId(), Instant.now().plus(Duration.ofMinutes(5)), Locale.ENGLISH);
        lists.setDone(milk.getId(), alex.getId(), true);
        openLists(1);
        assertEquals(List.of("Taxes", "Eggs", "Bread", "Milk"), rowTexts());

        assertTrue($(Button.class, rows().get(0)).all().stream().noneMatch(b -> b.hasClassName("list-move-btn")),
                "a line pinned by its reminder has no arrows");
        assertTrue($(Button.class, rows().get(3)).all().stream().noneMatch(b -> b.hasClassName("list-move-btn")),
                "nor has a ticked line");
        assertFalse(button(rows().get(1), "list-move-up").isEnabled(), "the first plain line cannot go up");
        assertTrue(button(rows().get(1), "list-move-down").isEnabled());
        assertTrue(button(rows().get(2), "list-move-up").isEnabled());
        assertFalse(button(rows().get(2), "list-move-down").isEnabled(), "the last plain line cannot go down");
        assertEquals("Move up", button(rows().get(2), "list-move-up").getAriaLabel().orElseThrow());

        test(button(rows().get(1), "list-move-down")).click();
        awaitRedraw(() -> rowTexts().equals(List.of("Taxes", "Bread", "Eggs", "Milk")));
        assertEquals(List.of("Taxes", "Bread", "Eggs", "Milk"), rowTexts(), "down past the bread");
        test(button(rows().get(2), "list-move-up")).click();
        awaitRedraw(() -> rowTexts().equals(List.of("Taxes", "Eggs", "Bread", "Milk")));
        assertEquals(List.of("Taxes", "Eggs", "Bread", "Milk"), rowTexts(), "and back up");
        assertEquals(List.of("Eggs", "Bread", "Taxes"), lists.openItems(code, ListKind.TODO).stream()
                .map(ListItem::getText).toList(), "written through to the family's order");
    }

    private List<String> rowTexts() {
        return rows().stream().map(r -> $(Div.class, r).all().stream()
                .filter(d -> d.getClassNames().contains("list-text")).findFirst().orElseThrow().getText())
                .toList();
    }
}
