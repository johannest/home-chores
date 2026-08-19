package com.homechores.ui;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.IntegerField;

/**
 * "How often?" for a chore: a short list of cadences people actually think in, with a free day
 * count kept behind "Custom…" for the rest.
 *
 * <p>The presets are only a friendlier way to write {@link com.homechores.domain.ChoreTask}'s
 * {@code intervalDays} — that int stays the single source of truth for due dates, and the values
 * these presets write are the canonical ones {@link com.homechores.domain.Cadence} buckets the board
 * by. A chore edited before this existed (or by hand) round-trips through "Custom…" without losing
 * its interval.
 *
 * <p>Shared by both chore editors in {@link AdminPanel} so they cannot drift apart.
 */
class FrequencyField extends VerticalLayout {

    /** The cadences offered, in the order they are listed. CUSTOM reveals the day count. */
    enum Preset {
        ANYTIME(0, "admin.freq.anytime"),
        DAILY(1, "admin.freq.daily"),
        WEEKLY(7, "admin.freq.weekly"),
        FORTNIGHTLY(14, "admin.freq.fortnightly"),
        MONTHLY(30, "admin.freq.monthly"),
        QUARTERLY(90, "admin.freq.quarterly"),
        YEARLY(365, "admin.freq.yearly"),
        CUSTOM(-1, "admin.freq.custom");

        private final int days;
        private final String key;

        Preset(int days, String key) {
            this.days = days;
            this.key = key;
        }
    }

    /** Seeded into the day count when the user picks "Custom…" with nothing typed yet. */
    private static final int CUSTOM_SEED = 3;

    private final Select<Preset> preset = new Select<>();
    private final IntegerField custom = new IntegerField();

    FrequencyField() {
        preset.setLabel(T.tr("admin.freq.label"));
        preset.setItems(Preset.values());
        preset.setItemLabelGenerator(p -> T.tr(p.key));
        preset.setHelperText(T.tr("admin.freq.helper"));
        preset.setWidthFull();
        preset.setValue(Preset.ANYTIME);

        // Reuses the keys the old bare day-count field used, so no translation is thrown away.
        custom.setLabel(T.tr("admin.chore.interval"));
        custom.setHelperText(T.tr("admin.chore.interval.helper"));
        custom.setMin(1);
        custom.setStepButtonsVisible(true);
        custom.setWidthFull();
        custom.setVisible(false);

        preset.addValueChangeListener(e -> {
            boolean isCustom = e.getValue() == Preset.CUSTOM;
            if (isCustom && custom.getValue() == null) {
                custom.setValue(CUSTOM_SEED);
            }
            custom.setVisible(isCustom);
        });

        setPadding(false);
        setSpacing(false);
        setWidthFull();
        add(preset, custom);
    }

    /** The interval in days, as the service wants it. Never negative. */
    int getIntervalDays() {
        Preset p = preset.getValue();
        if (p == null) {
            return 0;
        }
        if (p != Preset.CUSTOM) {
            return p.days;
        }
        return custom.getValue() == null ? 0 : Math.max(0, custom.getValue());
    }

    /** Shows an existing interval: an exact preset if there is one, otherwise "Custom…". */
    void setIntervalDays(int days) {
        int d = Math.max(0, days);
        Preset match = null;
        for (Preset p : Preset.values()) {
            if (p != Preset.CUSTOM && p.days == d) {
                match = p;
                break;
            }
        }
        // Pre-fill the day count even while hidden, so switching to Custom shows the real value.
        custom.setValue(d <= 0 ? null : d);
        preset.setValue(match != null ? match : Preset.CUSTOM);
        custom.setVisible(match == null);
    }
}
