package com.homechores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.homechores.domain.ChoreTask;
import com.homechores.domain.CompletionRepository;
import com.homechores.domain.Home;
import com.homechores.domain.Member;
import com.homechores.domain.MemberRepository;
import com.homechores.service.BackupService;
import com.homechores.service.ChoreService;
import com.homechores.service.CreditService;
import com.homechores.service.StatsService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * The upgrade path for a family that already has data.
 *
 * <p>Every other test starts from {@code ddl-auto=create-drop} on an empty in-memory
 * database, so none of them can see the one failure mode that actually reaches users: a
 * change that a real, already-populated {@code data/homechores.mv.db} cannot be upgraded
 * to. This test boots the whole application against a file database created from
 * {@code legacy-schema.sql} — a pre-Phase-4 FlashChores, missing every column and table
 * added since — with the production {@code ddl-auto=update}, and then exercises the
 * features that read those rows.
 *
 * <p>What it pins down:
 * <ul>
 *   <li>the context starts at all (a non-null column added without a default fails here);
 *   <li>new boolean settings adopt their documented default on existing homes, rather
 *       than silently reading {@code false} and switching a gate off;
 *   <li>columns that predate a feature read back null and the code copes (the legacy
 *       identity migration keys on exactly that);
 *   <li>an old home still completes chores, aggregates stats and exports a backup.
 * </ul>
 */
@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=update",
    "homechores.ratelimit.enabled=false"
})
// The legacy database is a file, shared by every test in this class, so each test rolls
// its writes back — otherwise one test's rename or completion is the next one's fixture.
@Transactional
class SchemaEvolutionTest {

    /** The legacy database lives beside the build output, not in the user's data/ dir. */
    private static final Path DB_DIR = Path.of("target", "schema-evolution");

    static {
        try {
            Files.createDirectories(DB_DIR);
            for (String suffix : List.of(".mv.db", ".trace.db")) {
                Files.deleteIfExists(DB_DIR.resolve("legacy" + suffix));
            }
            String ddl = new String(SchemaEvolutionTest.class.getResourceAsStream(
                    "/legacy-schema.sql").readAllBytes(), StandardCharsets.UTF_8);
            try (Connection c = DriverManager.getConnection(url(), "sa", "");
                 Statement s = c.createStatement()) {
                s.execute(ddl);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("could not build the legacy database", e);
        }
    }

    private static String url() {
        return "jdbc:h2:file:" + DB_DIR.toAbsolutePath().resolve("legacy") + ";DB_CLOSE_DELAY=-1";
    }

    @DynamicPropertySource
    static void legacyDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", SchemaEvolutionTest::url);
    }

    @Autowired ChoreService service;
    @Autowired StatsService stats;
    @Autowired CreditService credits;
    @Autowired BackupService backup;
    @Autowired MemberRepository members;
    @Autowired CompletionRepository completions;

    private static final String CODE = "OLDHOME";

    @Test
    void theOldHomeSurvivesTheUpgrade() {
        Home home = service.findHome(CODE).orElseThrow();
        assertEquals("The Old House", home.getName());
        assertEquals("4242", home.getAdminPin());
        assertEquals(2, home.getDailyTargetPerMember());
        assertEquals(2, service.membersOf(CODE).size());
        assertEquals(2, service.tasksOf(CODE).size());
    }

    /**
     * The gates default to ON for a home that predates them. Getting this wrong is not a
     * crash but a silent security regression: an existing family's board would quietly
     * stop asking an admin before letting a new device in.
     */
    @Test
    void settingsAddedSinceTheHomeWasCreated_adoptTheirDocumentedDefault() {
        Home home = service.findHome(CODE).orElseThrow();
        assertTrue(home.isApproveRejoin(), "rejoin gate on for a pre-existing home");
        assertTrue(home.isApproveJoin(), "join gate on for a pre-existing home");
        assertTrue(home.isConfirmCompletion(), "confirm-before-completing on");
        assertTrue(home.isAllowOtherHelp(), "other help on");
    }

    /** Nullable columns added later read back null, and everything downstream copes. */
    @Test
    void columnsAddedSinceTheHomeWasCreated_readBackNull() {
        Home home = service.findHome(CODE).orElseThrow();
        assertNull(home.getLastActiveAt(), "no activity was ever recorded for this home");
        assertNotNull(home.lastActiveOrCreated(), "so it falls back to the creation time");

        Member alex = byName("Alex");
        assertNull(alex.getDeviceSecretHash(), "no device had a secret before Phase 4");
        assertNull(alex.getCreatedAt(), "rows predating the column read null");
        assertNull(alex.getAvatar());
        assertNull(alex.getTermsAcceptedAt());
        assertNull(alex.getReminderTime());
    }

    /**
     * The one place a null {@code createdAt} carries meaning: it is how the legacy
     * identity migration tells a genuine pre-upgrade device from a new arrival.
     */
    @Test
    void aPreUpgradeDeviceCanStillBeMigratedOntoADeviceSecret() {
        Member sam = byName("Sam");
        String secret = service.migrateLegacyIdentity(sam.getId(), CODE).orElseThrow();

        assertTrue(service.verifyDeviceSecret(sam.getId(), secret));
        assertTrue(service.migrateLegacyIdentity(sam.getId(), CODE).isEmpty(),
                "and only once");
    }

    @Test
    void theOldHomeCanStillBeUsed() {
        Member alex = byName("Alex");
        ChoreTask dishwasher = service.tasksOf(CODE).stream()
                .filter(t -> t.getName().equals("Empty dishwasher")).findFirst().orElseThrow();

        var outcome = service.complete(dishwasher.getId(), alex.getId());

        assertTrue(outcome.allowed(), "a chore from 2025 is still completable");
        assertEquals(2, service.completionCount(alex.getId()), "on top of the historic one");
    }

    /** Aggregations must not trip over rows written before their columns existed. */
    @Test
    void statisticsReadTheHistoricRows() {
        Member alex = byName("Alex");
        var mine = stats.myStats(alex.getId(), CODE);
        assertEquals(1, mine.totalApproved());
        assertEquals(1, mine.feedback().love());

        var home = stats.homeStats(CODE);
        assertEquals(2, home.perMember().size());
        // Two APPROVED across the family; the REJECTED one counts for nobody.
        assertEquals(2, home.perMember().stream().mapToLong(StatsService.CountBar::value).sum());
    }

    /** Credits earned before completion_id existed have a null link and must survive. */
    @Test
    void creditsWrittenBeforeTheCompletionLinkExisted_stillCount() {
        Member alex = byName("Alex");
        assertEquals(3, credits.balance(alex.getId()));
    }

    @Test
    void theOldHomeStillExportsAndRestores() {
        String json = backup.export(CODE);
        assertTrue(json.contains("The Old House"));

        var result = backup.restore(json.getBytes(StandardCharsets.UTF_8), CODE);

        assertEquals(2, result.members());
        assertEquals(2, result.tasks());
        assertEquals(3, result.completions(), "the rejected one is history too");
        assertEquals("The Old House", service.findHome(CODE).orElseThrow().getName());
    }

    /** Tables added after this database was written are created by the upgrade, empty. */
    @Test
    void tablesAddedSinceAreCreatedEmpty() {
        assertEquals(0, service.pendingRejoinCount(CODE));
        assertTrue(service.pendingOtherHelp(CODE).isEmpty());
    }

    /** The old, wider name columns are kept by ddl-auto=update — the service layer clips. */
    @Test
    void theServiceLayerIsWhatEnforcesLengths_notTheOldColumns() {
        Member sam = byName("Sam");
        service.renameMember(sam.getId(), "x".repeat(200));
        assertEquals(40, byName2(sam.getId()).getName().length(),
                "InputLimits.MEMBER_NAME, not the legacy VARCHAR(255)");
    }

    private Member byName(String name) {
        return service.membersOf(CODE).stream()
                .filter(m -> m.getName().equals(name)).findFirst().orElseThrow();
    }

    private Member byName2(Long id) {
        return members.findById(id).orElseThrow();
    }
}
