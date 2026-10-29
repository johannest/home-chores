package com.homechores.domain;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The chores a new home can start with (§4.2.1): a fixed list a family ticks through when
 * creating the home, instead of typing fifty chores in one by one afterwards.
 *
 * <p>Each entry is a message key rather than a name, so the chore is created in the creator's
 * language and then belongs to the home like any other — renaming or deleting it later touches
 * nothing here. The {@link Entry#starter() starter} entries are the eleven every home used to get,
 * and stay pre-ticked so a family that never opens the picker gets the board it always had.
 */
public final class ChoreCatalog {

    /** A board section the picked chores are filed under; each becomes a {@link ChoreGroup}. */
    public enum Section {
        KITCHEN("🍳"),
        CLEANING("🧽"),
        LAUNDRY("🧺"),
        ERRANDS("🛒"),
        OUTDOORS("🌱"),
        PETS("🐾"),
        MAINTENANCE("🔧"),
        FAMILY("🧒");

        private final String emoji;

        Section(String emoji) {
            this.emoji = emoji;
        }

        public String emoji() {
            return emoji;
        }

        public String messageKey() {
            return "catalog.section." + name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * One chore on offer. {@code intervalDays}, {@code windows} and {@code seasons} are stored
     * as-is on the new {@link ChoreTask}, in the formats its setters take.
     */
    public record Entry(String key, String emoji, Section section, int intervalDays,
                        String windows, String seasons, boolean starter) {

        public String messageKey() {
            return "chore.catalog." + key;
        }
    }

    /**
     * What the creator picked: which chores, and whether to file them under a board section each.
     * Sections are a choice rather than the rule because the eleven starters would make five
     * headings, two over a single card — clutter on a short board, a map on a long one.
     */
    public record Pick(Set<String> keys, boolean inSections) {

        public Pick {
            keys = Set.copyOf(keys);
        }

        /** The eleven starters on a flat board: what a home gets when nobody opens the picker. */
        public static Pick starters() {
            return new Pick(Set.copyOf(starterKeys()), false);
        }
    }

    private static final String NOT_WINTER = "SPRING,SUMMER,AUTUMN";

    /** In board order: by section, and within one the order the chores are filed in. */
    public static final List<Entry> ENTRIES = List.of(
            starter("emptyDishwasher", "🍽️", Section.KITCHEN),
            starter("fillDishwasher", "🫧", Section.KITCHEN),
            starter("cookDinner", "🍳", Section.KITCHEN),
            starter("salad", "🥗", Section.KITCHEN),
            entry("washDishes", "🧽", Section.KITCHEN, 0),
            entry("setTable", "🍴", Section.KITCHEN, 0),
            entry("clearTable", "🥣", Section.KITCHEN, 0),
            entry("wipeCounters", "🧼", Section.KITCHEN, 0),
            entry("kitchenClean", "✨", Section.KITCHEN, 7),
            entry("cleanFridge", "🧊", Section.KITCHEN, 30),
            entry("cleanMicrowave", "♨️", Section.KITCHEN, 14),
            entry("cleanOven", "🔥", Section.KITCHEN, 90),
            entry("bake", "🥐", Section.KITCHEN, 0),
            entry("breakfast", "🥞", Section.KITCHEN, 0),
            entry("packLunch", "🥪", Section.KITCHEN, 0),

            starter("trash", "🗑️", Section.CLEANING),
            starter("vacuum", "🧹", Section.CLEANING),
            starter("vestibule", "🧥", Section.CLEANING),
            entry("recycling", "♻️", Section.CLEANING, 7),
            entry("mop", "🪣", Section.CLEANING, 7),
            entry("toilet", "🚽", Section.CLEANING, 7),
            entry("bathroom", "🚿", Section.CLEANING, 7),
            entry("sauna", "🧖", Section.CLEANING, 30),
            entry("dust", "🪶", Section.CLEANING, 14),
            entry("bedSheets", "🛏️", Section.CLEANING, 14),
            entry("windows", "🪟", Section.CLEANING, 180),
            entry("rugs", "🧶", Section.CLEANING, 90),
            entry("tidyRoom", "🧸", Section.CLEANING, 0),

            entry("startWash", "🔄", Section.LAUNDRY, 0),
            starter("hangLaundry", "🧺", Section.LAUNDRY),
            starter("foldLaundry", "👕", Section.LAUNDRY),
            entry("sortLaundry", "🧦", Section.LAUNDRY, 0),
            entry("ironing", "👔", Section.LAUNDRY, 0),

            entry("groceries", "🛒", Section.ERRANDS, 0),
            entry("returnBottles", "🍾", Section.ERRANDS, 14),
            entry("parcel", "📦", Section.ERRANDS, 0),

            new Entry("waterPlants", "🪴", Section.OUTDOORS, 7, null, null, true),
            new Entry("gardenWork", "🌱", Section.OUTDOORS, 0, null, NOT_WINTER, false),
            new Entry("mowLawn", "🌿", Section.OUTDOORS, 7, null, NOT_WINTER, false),
            new Entry("weed", "🌾", Section.OUTDOORS, 7, null, "SPRING,SUMMER", false),
            new Entry("waterGarden", "💧", Section.OUTDOORS, 2, null, "SUMMER", false),
            new Entry("rakeLeaves", "🍂", Section.OUTDOORS, 7, null, "AUTUMN", false),
            new Entry("shovelSnow", "❄️", Section.OUTDOORS, 0, null, "WINTER", false),
            entry("washCar", "🚗", Section.OUTDOORS, 30),
            // 150 days with the two seasons: due again each spring and autumn, never twice in one.
            new Entry("changeTyres", "🛞", Section.OUTDOORS, 150, null, "SPRING,AUTUMN", false),

            new Entry("dogOut", "🐕", Section.PETS, 0, "08:00-10:00,18:00-22:00", null, true),
            entry("feedPets", "🦴", Section.PETS, 0),
            entry("litterBox", "🐈", Section.PETS, 2),
            entry("cleanCage", "🐠", Section.PETS, 14),
            entry("brushPet", "🪮", Section.PETS, 7),

            entry("renovation", "🔨", Section.MAINTENANCE, 0),
            entry("bikeService", "🚲", Section.MAINTENANCE, 90),
            entry("smokeAlarms", "🚨", Section.MAINTENANCE, 90),
            entry("hoodFilter", "🌬️", Section.MAINTENANCE, 90),
            entry("defrostFreezer", "🥶", Section.MAINTENANCE, 365),

            entry("homework", "📚", Section.FAMILY, 0),
            entry("bedtimeStory", "📖", Section.FAMILY, 0),
            entry("hobbyRide", "🚌", Section.FAMILY, 0),
            entry("schoolBag", "🎒", Section.FAMILY, 0),
            entry("kidsBath", "🛁", Section.FAMILY, 0));

    private ChoreCatalog() {
    }

    private static Entry starter(String key, String emoji, Section section) {
        return new Entry(key, emoji, section, 0, null, null, true);
    }

    private static Entry entry(String key, String emoji, Section section, int intervalDays) {
        return new Entry(key, emoji, section, intervalDays, null, null, false);
    }

    /** The keys pre-ticked in the picker, and seeded when a home is created without one. */
    public static List<String> starterKeys() {
        return ENTRIES.stream().filter(Entry::starter).map(Entry::key).toList();
    }

    public static List<Entry> inSection(Section section) {
        return ENTRIES.stream().filter(e -> e.section() == section).toList();
    }
}
