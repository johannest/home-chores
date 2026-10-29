package com.homechores.ui;

import com.homechores.domain.ChoreCatalog;
import com.homechores.domain.ChoreCatalog.Entry;
import com.homechores.domain.ChoreCatalog.Pick;
import com.homechores.domain.ChoreCatalog.Section;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.checkbox.CheckboxGroupVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.function.SerializableConsumer;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The starter-chore picker on the create-home form (§4.2.1): the {@link ChoreCatalog} as one
 * checkbox group per board section, plus whether to file them that way on the board too.
 * Done hands back the pick; closing any other way
 * keeps what was chosen before, so a stray tap outside the dialog undoes nothing.
 */
class StarterChoresDialog extends Dialog {

    private final Map<Section, CheckboxGroup<Entry>> groups = new EnumMap<>(Section.class);

    StarterChoresDialog(Pick pick, SerializableConsumer<Pick> onDone) {
        addClassName("starter-dialog");
        setHeaderTitle(T.tr("starter.title"));

        Paragraph intro = new Paragraph(T.tr("starter.intro"));
        intro.addClassName("feedback-hint");

        Button all = new Button(T.tr("starter.all"), e -> groups.forEach((section, group) ->
                group.setValue(Set.copyOf(ChoreCatalog.inSection(section)))));
        Button none = new Button(T.tr("starter.none"), e -> groups.values().forEach(CheckboxGroup::clear));
        all.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        none.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        Div bulk = new Div(all, none);
        bulk.addClassName("starter-bulk");
        Checkbox inSections = new Checkbox(T.tr("starter.inSections"), pick.inSections());
        add(intro, inSections, bulk);

        for (Section section : Section.values()) {
            CheckboxGroup<Entry> group = new CheckboxGroup<>(
                    section.emoji() + " " + T.tr(section.messageKey()));
            group.addClassName("starter-section");
            group.addThemeVariants(CheckboxGroupVariant.LUMO_VERTICAL);
            List<Entry> entries = ChoreCatalog.inSection(section);
            group.setItems(entries);
            group.setItemLabelGenerator(e -> e.emoji() + " " + T.tr(e.messageKey()));
            group.setValue(Set.copyOf(entries.stream().filter(e -> pick.keys().contains(e.key())).toList()));
            groups.put(section, group);
            add(group);
        }

        Button done = new Button(T.tr("starter.done"), e -> {
            Set<String> keys = new HashSet<>();
            groups.values().forEach(g -> g.getValue().forEach(entry -> keys.add(entry.key())));
            onDone.accept(new Pick(keys, inSections.getValue()));
            close();
        });
        done.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        getFooter().add(new Button(T.tr("common.cancel"), e -> close()), done);
    }
}
