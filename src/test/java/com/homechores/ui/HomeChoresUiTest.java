package com.homechores.ui;

import com.vaadin.testbench.unit.SpringUIUnitTest;
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
 */
abstract class HomeChoresUiTest extends SpringUIUnitTest {

    @BeforeEach
    void installSignalsTestEnvironment() {
        initSignalsSupport();
    }
}
