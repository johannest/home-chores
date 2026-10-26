package com.homechores.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.homechores.domain.Member;
import com.homechores.service.ChoreService;
import com.homechores.service.SharedDeviceService;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.TextField;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Pairing is a PIN guess, so the pairing dialog spends from a per-connection budget of 10 an
 * hour (§4.1.3) before it asks the service — on top of the home's own 5-tries PIN lock, which
 * is per home and so says nothing about one connection guessing across many homes. The other
 * UI tests run with the limiter off; this one turns it on.
 */
@SpringBootTest(properties = "homechores.ratelimit.enabled=true")
class PairingRateLimitUiTest extends HomeChoresUiTest {

    @Autowired
    ChoreService service;

    @Autowired
    SharedDeviceService devices;

    @Test
    void theEleventhPairingAttemptInAnHour_isRefusedBeforeThePinIsTried() {
        Member admin = service.createHome("Nest", "Alex");
        String pin = service.findHome(admin.getHomeCode()).orElseThrow().getAdminPin();
        navigate(LandingView.class);
        $(Tabs.class).first().setSelectedIndex(1);
        field("Home code").setValue(admin.getHomeCode());
        click("Set up a shared family device");

        String wrong = pin.equals("0000") ? "1111" : "0000";
        field("Admin PIN").setValue(wrong);
        for (int i = 0; i < 10; i++) {
            click("Pair this device");
            assertTrue(field("Admin PIN").isInvalid(), "attempt " + (i + 1) + " reaches the PIN check");
        }
        field("Admin PIN").setValue(pin);
        click("Pair this device");

        List<Notification> open = $(Notification.class).all().stream().filter(Notification::isOpened).toList();
        assertEquals("Too many attempts from your connection. Please wait a few minutes and try again.",
                test(open.get(open.size() - 1)).getText());
        assertTrue(devices.devicesOf(admin.getHomeCode()).isEmpty(), "even the right PIN pairs nothing now");
    }

    private TextField field(String label) {
        return $(TextField.class).all().stream()
                .filter(f -> label.equals(f.getLabel()) && f.isVisible() && f.getUI().isPresent())
                .findFirst().orElseThrow(() -> new AssertionError("No field: " + label));
    }

    private void click(String text) {
        Button b = $(Button.class).all().stream()
                .filter(x -> text.equals(x.getText()) && x.isVisible() && x.getUI().isPresent())
                .findFirst().orElseThrow(() -> new AssertionError("No button: " + text));
        ComponentUtil.fireEvent(b, new ClickEvent<>(b));
    }
}
