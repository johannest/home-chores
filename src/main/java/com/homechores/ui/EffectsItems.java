package com.homechores.ui;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.contextmenu.SubMenu;
import com.vaadin.flow.component.page.Page;

/**
 * The "Sounds" and "Vibration" switches under "Sound &amp; vibration" in the board header's menu
 * ({@link HeaderMenu}).
 *
 * <p>Like the appearance rows, both are per device and per browser: the choice is stored and
 * acted on in {@code effects.js} ({@code window.__setEffect}, {@code window.playEffect}) and the
 * server never keeps it. Sound starts off and vibration on. {@link #sync} asks the browser what
 * is stored to place the check marks, and whether it can vibrate at all — iPhones cannot, and a
 * switch that does nothing is hidden rather than offered.
 */
final class EffectsItems {

    private final MenuItem sound;
    private final MenuItem vibration;
    private boolean soundOn;
    private boolean vibrationOn = true;

    EffectsItems(SubMenu sub) {
        sound = sub.addItem(T.tr("effects.sound"));
        sound.addClickListener(e -> {
            soundOn = !soundOn;
            apply(sound, "sound", soundOn);
        });
        vibration = sub.addItem(T.tr("effects.vibration"));
        vibration.addClickListener(e -> {
            vibrationOn = !vibrationOn;
            apply(vibration, "vibration", vibrationOn);
        });
        for (MenuItem item : new MenuItem[] {sound, vibration}) {
            item.setCheckable(true);
            item.setKeepOpen(true);
        }
        sound.setChecked(soundOn);
        vibration.setChecked(vibrationOn);
    }

    /** Sets the check mark ourselves rather than trusting the item's own toggle, so the mark
     *  and the stored choice cannot drift apart. */
    private static void apply(MenuItem item, String which, boolean on) {
        item.setChecked(on);
        UI.getCurrent().getPage().executeJs("window.__setEffect($0, $1)", which, on);
    }

    /** One round trip: "sound|vibration|can-vibrate", the guarded read matching AppearanceItems. */
    void sync(Page page) {
        page.executeJs(
                        "var v = 'vibrate' in navigator;"
                        + " try { return (localStorage.getItem('flashchores.sound') || 'off') + '|'"
                        + " + (localStorage.getItem('flashchores.vibration') || 'on') + '|' + v; }"
                        + " catch (e) { return 'off|on|' + v; }")
                .then(String.class, stored -> {
                    String[] parts = (stored == null ? "off|on|true" : stored).split("\\|", 3);
                    soundOn = "on".equals(parts[0]);
                    sound.setChecked(soundOn);
                    if (parts.length > 1) {
                        vibrationOn = "on".equals(parts[1]);
                        vibration.setChecked(vibrationOn);
                    }
                    if (parts.length > 2) {
                        vibration.setVisible(Boolean.parseBoolean(parts[2]));
                    }
                });
    }
}
