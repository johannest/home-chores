package com.homechores;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.server.PWA;
import com.vaadin.flow.shared.ui.Transport;
import com.vaadin.flow.theme.lumo.Lumo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * FlashChores — effortless tracking of small household chores.
 *
 * <p>Server push is enabled so that when one family member completes a chore on
 * their phone, every other member's screen updates live (Vaadin Signals + push).
 *
 * <p>Vaadin 25 uses CSS-based theming: the Lumo theme and our custom stylesheet are
 * loaded explicitly with {@link StyleSheet} (the old {@code @Theme}/{@code theme.json}
 * mechanism is deprecated).
 */
@SpringBootApplication
@Push(transport = Transport.LONG_POLLING)
@StyleSheet(Lumo.STYLESHEET)
@StyleSheet("styles.css")
@PWA(name = "FlashChores", shortName = "FlashChores",
        themeColor = "#10b981", backgroundColor = "#ffffff")
public class Application implements AppShellConfigurator {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
