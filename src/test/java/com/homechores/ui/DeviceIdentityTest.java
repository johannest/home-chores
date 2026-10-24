package com.homechores.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.homechores.ui.DeviceIdentity.Reading;
import org.junit.jupiter.api.Test;

/** What the browser's stored value is read as, and what the log says about the browser. */
class DeviceIdentityTest {

    private static final String IPHONE_SAFARI = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_6 like Mac OS X)"
            + " AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.6 Mobile/15E148 Safari/604.1";

    @Test
    void aCurrentValue_readsAsAnIdentity() {
        Reading r = DeviceIdentity.parse("42|K7QP4ZT|0123456789abcdef");
        assertTrue(r.stored().isPresent());
        assertEquals(42L, r.stored().get().id());
        assertEquals("K7QP4ZT", r.stored().get().homeCode());
        assertEquals("0123456789abcdef", r.stored().get().secret());
        assertFalse(r.unreadable());
    }

    @Test
    void nothingStored_isNotAFailure() {
        assertEquals(Reading.NOTHING, DeviceIdentity.parse(null));
        assertEquals(Reading.NOTHING, DeviceIdentity.parse(""));
        assertEquals(Reading.NOTHING, DeviceIdentity.parse("  "));
    }

    /** The pre-secret format and a hand-edited value are told apart from "never joined". */
    @Test
    void aLegacyOrTamperedValue_readsAsUnreadable() {
        assertEquals(Reading.UNREADABLE, DeviceIdentity.parse("42|K7QP4ZT"), "pre-secret pair");
        assertEquals(Reading.UNREADABLE, DeviceIdentity.parse("42||secret"), "no home code");
        assertEquals(Reading.UNREADABLE, DeviceIdentity.parse("42|K7QP4ZT|"), "no secret");
        assertEquals(Reading.UNREADABLE, DeviceIdentity.parse("mom|K7QP4ZT|secret"), "not an id");
    }

    @Test
    void browserLabel_namesDeviceOsAndBrowser_notTheWholeString() {
        assertEquals("iPhone iOS 18.6, Safari 18", DeviceIdentity.browserLabel(IPHONE_SAFARI));
        assertEquals("iPhone iOS 18.6, Safari 18, webview",
                DeviceIdentity.browserLabel(IPHONE_SAFARI.replace(" Safari/604.1", "")),
                "an installed app or an in-app browser has no Safari token");
        assertEquals("iPhone iOS 17.5, Chrome 126",
                DeviceIdentity.browserLabel("Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X)"
                        + " AppleWebKit/605.1.15 (KHTML, like Gecko) CriOS/126.0.6478.54"
                        + " Mobile/15E148 Safari/604.1"));
        assertEquals("Android 14, Chrome 128",
                DeviceIdentity.browserLabel("Mozilla/5.0 (Linux; Android 14; Pixel 8)"
                        + " AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"));
        assertEquals("unknown browser", DeviceIdentity.browserLabel(null));
        assertEquals("other, other", DeviceIdentity.browserLabel("curl/8.4.0"));
    }
}
