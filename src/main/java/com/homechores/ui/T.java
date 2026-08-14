package com.homechores.ui;

import com.vaadin.flow.component.UI;

/** Tiny translation shortcut usable from views and helper classes alike. */
final class T {

    private T() {
    }

    /** Translates {@code key} in the current UI's locale, with optional {0},{1}… params. */
    static String tr(String key, Object... params) {
        UI ui = UI.getCurrent();
        return ui != null ? ui.getTranslation(key, params) : key;
    }
}
