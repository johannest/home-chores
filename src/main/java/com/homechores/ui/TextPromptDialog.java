package com.homechores.ui;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.textfield.TextField;
import java.util.function.Predicate;

/**
 * One text field and a confirm button: editing a list line, naming a new list, renaming one.
 * Enter confirms. {@code onSave} returns whether the text was taken; on false the dialog stays
 * open with the field marked invalid (a blank name, say), so nothing typed is lost.
 */
class TextPromptDialog extends Dialog {

    final TextField field = new TextField();

    TextPromptDialog(String title, String label, String initial, int maxLength, String confirm,
                     Predicate<String> onSave) {
        setHeaderTitle(title);
        setWidth("min(90vw, 24em)");
        addClassName("text-prompt-dialog");

        if (label != null) {
            field.setLabel(label); // the item editor has none: its title already says it
        } else {
            field.setAriaLabel(title);
        }
        field.setMaxLength(maxLength);
        field.setValue(initial == null ? "" : initial);
        field.setWidthFull();
        field.setClearButtonVisible(true);
        field.setAutoselect(true);
        add(field);

        Runnable save = () -> {
            if (onSave.test(field.getValue())) {
                close();
            } else {
                field.setInvalid(true);
                field.focus();
            }
        };
        // keydown, not keypress — see ListPanel.addRow.
        field.addKeyDownListener(Key.ENTER, e -> save.run());
        Button ok = new Button(confirm, e -> save.run());
        ok.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        getFooter().add(new Button(T.tr("common.cancel"), e -> close()), ok);
        addOpenedChangeListener(e -> {
            if (e.isOpened()) {
                field.focus();
            }
        });
    }
}
