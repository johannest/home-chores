package com.homechores.ui;

import com.vaadin.flow.component.UI;
import java.time.Duration;
import java.time.Instant;

/** Tiny translation shortcut usable from views and helper classes alike. */
final class T {

    private T() {
    }

    /** Translates {@code key} in the current UI's locale, with optional {0},{1}… params. */
    static String tr(String key, Object... params) {
        UI ui = UI.getCurrent();
        return ui != null ? ui.getTranslation(key, params) : key;
    }

    /** "Just now" / "5 min ago" / "3 h ago" / "2 d ago" — shared by every activity list. */
    static String ago(Instant when) {
        Duration d = Duration.between(when, Instant.now());
        long mins = d.toMinutes();
        if (mins < 1) {
            return tr("admin.ago.justNow");
        }
        if (mins < 60) {
            return tr("admin.ago.min", mins);
        }
        long hours = d.toHours();
        if (hours < 24) {
            return tr("admin.ago.hours", hours);
        }
        return tr("admin.ago.days", d.toDays());
    }
}
