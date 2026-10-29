package com.homechores.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.homechores.domain.ChoreCatalog.Entry;
import com.homechores.domain.ChoreCatalog.Section;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The starter-chore catalogue (§4.2.1) is data; these pin that it is well-formed data. */
class ChoreCatalogTest {

    private static final List<String> BUNDLES =
            List.of("messages.properties", "messages_fi.properties", "messages_sv.properties");

    @Test
    void everyChoreAndSection_isNamedInAllThreeLanguages_andNoTwoShareAName() {
        for (String bundle : BUNDLES) {
            Properties p = load(bundle);
            Set<String> names = new HashSet<>();
            for (Entry e : ChoreCatalog.ENTRIES) {
                String name = p.getProperty(e.messageKey());
                assertTrue(name != null && !name.isBlank(), e.messageKey() + " in " + bundle);
                assertTrue(name.length() <= InputLimits.TASK_NAME);
                assertTrue(names.add(name), "two chores called \"" + name + "\" in " + bundle);
            }
            for (Section s : Section.values()) {
                String name = p.getProperty(s.messageKey());
                assertTrue(name != null && name.length() <= InputLimits.GROUP_NAME,
                        s.messageKey() + " in " + bundle + " fits a group name");
            }
        }
    }

    @Test
    void keysAreUnique_andCadencesAreOnesTheChoreEditorAccepts() {
        Set<String> keys = new HashSet<>();
        for (Entry e : ChoreCatalog.ENTRIES) {
            assertTrue(keys.add(e.key()), "duplicate key " + e.key());
            assertTrue(e.intervalDays() >= 0 && e.intervalDays() <= InputLimits.MAX_DAYS, e.key());
            assertEquals(e.seasons(), Seasons.normalize(e.seasons()), e.key() + " seasons are canonical");
            if (e.windows() != null) {
                assertEquals(e.windows(), TimeWindows.normalize(e.windows()), e.key());
            }
        }
    }

    @Test
    void theStarterKeys_areTheElevenEveryHomeUsedToGet() {
        assertEquals(List.of("emptyDishwasher", "fillDishwasher", "cookDinner", "salad", "trash",
                        "vacuum", "vestibule", "hangLaundry", "foldLaundry", "waterPlants", "dogOut"),
                ChoreCatalog.starterKeys());
        assertFalse(ChoreCatalog.starterKeys().contains("mowLawn"));
    }

    @Test
    void entriesAreListedInSectionOrder_soTheBoardAndTheCatalogueAgree() {
        int last = -1;
        for (Entry e : ChoreCatalog.ENTRIES) {
            assertTrue(e.section().ordinal() >= last, e.key() + " is out of section order");
            last = e.section().ordinal();
        }
    }

    private static Properties load(String name) {
        Properties p = new Properties();
        try (InputStream in = ChoreCatalogTest.class.getClassLoader().getResourceAsStream(name)) {
            assertTrue(in != null, name + " is on the classpath");
            p.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return p;
    }
}
