package com.homechores.ui;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.testbench.unit.SpringUIUnitTest;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;

/**
 * The base of every UI unit test here. It installs TestBench's signals test environment, which
 * {@code SpringUIUnitTest} (TestBench 25.3) forgets — its {@code initVaadinEnvironment} override
 * never calls {@code initSignalsSupport()}. Without it, the home's shared revision signal
 * confirms each bump through {@code VaadinService.getExecutor()}, a thread pool, and the view's
 * effect lands in the UI's access queue to run whenever the pool and the session lock happen
 * to align — which is why "another phone changed something" tests failed now and then. With
 * it, every confirm and effect is queued and {@code runPendingSignalsTasks()} drains them on
 * the test thread, deterministically. JUnit runs the superclass's {@code @BeforeEach} first, so
 * the Vaadin environment exists by the time this runs, and TestBench's own {@code @AfterEach}
 * unregisters the environment.
 *
 * <p>It also holds the helpers every UI test reaches for — what is {@link #usable}, and the rows
 * of the header's and the admin rows' "⋯" menus — so they live once rather than in each class.
 */
abstract class HomeChoresUiTest extends SpringUIUnitTest {

    @BeforeEach
    void installSignalsTestEnvironment() {
        initSignalsSupport();
    }

    /** Visible up the whole ancestor chain (so components in a hidden form are excluded). */
    protected boolean usable(Component c) {
        Component cur = c;
        while (cur != null) {
            if (!cur.isVisible()) {
                return false;
            }
            cur = cur.getParent().orElse(null);
        }
        return c.getUI().isPresent();
    }

    /** Every MenuItem in the UI, sub menus included — the header "⋯" and the admin rows' "⋯". */
    protected Stream<MenuItem> allMenuItems() {
        return $(MenuBar.class).all().stream()
                .filter(this::usable)
                .flatMap(m -> m.getItems().stream())
                .flatMap(this::withSubItems);
    }

    private Stream<MenuItem> withSubItems(MenuItem item) {
        return Stream.concat(Stream.of(item),
                item.getSubMenu().getItems().stream().flatMap(this::withSubItems));
    }

    /** The visible text of every menu row, in menu order. */
    protected List<String> menuItems() {
        return allMenuItems().map(i -> i.getElement().getTextRecursively().trim()).toList();
    }

    /** The menu row with this visible text, whatever its state. */
    protected MenuItem menuItem(String text) {
        return allMenuItems()
                .filter(i -> text.equals(i.getElement().getTextRecursively().trim()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No menu item: " + text));
    }

    /** Fires a click on an enabled menu row by its visible text — what a tap on the open menu does. */
    protected void clickMenuItem(String text) {
        MenuItem item = allMenuItems()
                .filter(i -> text.equals(i.getElement().getTextRecursively().trim()) && i.isEnabled())
                .findFirst()
                .orElseThrow(() -> new AssertionError("No usable menu item: " + text));
        ComponentUtil.fireEvent(item, new ClickEvent<>(item));
    }
}
