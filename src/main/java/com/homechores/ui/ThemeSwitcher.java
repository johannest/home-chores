package com.homechores.ui;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.select.Select;

/**
 * Compact theme chooser (auto / light / dark). The preference is per device, persisted in
 * localStorage by {@code window.__applyTheme} (see the inline script in index.html, which
 * also re-applies it before Vaadin loads so there is no flash of the wrong theme). "Auto"
 * follows the OS via the CSS {@code color-scheme: light dark} the server ships by default.
 */
class ThemeSwitcher extends Select<String> {

    ThemeSwitcher() {
        setItems("auto", "light", "dark");
        setItemLabelGenerator(mode -> T.tr("theme." + mode));
        setValue("auto");
        setWidth("7.5em");
        addClassName("lang-select"); // reuse the narrow-on-phones styling

        addValueChangeListener(e -> {
            if (e.getValue() != null && e.isFromClient()) {
                UI.getCurrent().getPage().executeJs("window.__applyTheme($0)", e.getValue());
            }
        });
    }

    @Override
    protected void onAttach(AttachEvent event) {
        super.onAttach(event);
        // Reflect the device's stored choice; the guarded read matches DeviceIdentity.
        event.getUI().getPage()
                .executeJs("try { return localStorage.getItem('flashchores.theme') || 'auto'; }"
                        + " catch (e) { return 'auto'; }")
                .then(String.class, stored -> {
                    if ("light".equals(stored) || "dark".equals(stored)) {
                        setValue(stored);
                    }
                });
    }
}
