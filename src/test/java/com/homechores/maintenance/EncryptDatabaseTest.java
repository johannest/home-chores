package com.homechores.maintenance;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.homechores.DatabaseEncryption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The one-off conversion behind {@code flashchores-admin.py encrypt-db / decrypt-db / rekey-db}:
 * the data survives each step, nothing plain is left beside an encrypted file, and a refused or
 * failed step leaves the database exactly as it was.
 */
class EncryptDatabaseTest {

    private static final String KEY = "first-key-0123456789";
    private static final String NEW_KEY = "second-key-0123456789";

    @TempDir
    Path dir;
    private Path base;
    private Path file;

    @BeforeEach
    void plainDatabaseWithOneMember() throws Exception {
        base = dir.resolve("homechores");
        file = dir.resolve("homechores.mv.db");
        try (Connection c = DriverManager.getConnection("jdbc:h2:file:" + base, "sa", "")) {
            c.createStatement().execute("create table member(name varchar)");
            c.createStatement().execute("insert into member values ('Mummo Maija')");
        }
        Files.writeString(dir.resolve("homechores.trace.db"), "insert into member values ('Mummo Maija')");
    }

    private String memberName(String key) throws Exception {
        String url = "jdbc:h2:file:" + base + ";IFEXISTS=TRUE" + (key == null ? "" : ";CIPHER=AES");
        try (Connection c = DriverManager.getConnection(url, "sa", key == null ? "" : key + " ");
             ResultSet r = c.createStatement().executeQuery("select name from member")) {
            r.next();
            return r.getString(1);
        }
    }

    @Test
    void encrypt_keepsTheData_andLeavesNoPlainCopyOrTraceBehind() throws Exception {
        EncryptDatabase.run("encrypt", base, Map.of("FLASHCHORES_DB_KEY", KEY));

        assertTrue(DatabaseEncryption.isEncrypted(file));
        assertEquals("Mummo Maija", memberName(KEY));
        try (var files = Files.list(dir)) {
            assertEquals(List.of(file), files.toList(),
                    "no .before-encrypt copy, no old trace file");
        }
    }

    @Test
    void rekey_thenDecrypt_roundTripsTheData() throws Exception {
        EncryptDatabase.run("encrypt", base, Map.of("FLASHCHORES_DB_KEY", KEY));
        EncryptDatabase.run("rekey", base,
                Map.of("FLASHCHORES_DB_OLD_KEY", KEY, "FLASHCHORES_DB_KEY", NEW_KEY));
        assertEquals("Mummo Maija", memberName(NEW_KEY));

        EncryptDatabase.run("decrypt", base, Map.of("FLASHCHORES_DB_KEY", NEW_KEY));
        assertFalse(DatabaseEncryption.isEncrypted(file));
        assertEquals("Mummo Maija", memberName(null));
    }

    @Test
    void aWrongOldKey_failsAndPutsTheFileBackUntouched() throws Exception {
        EncryptDatabase.run("encrypt", base, Map.of("FLASHCHORES_DB_KEY", KEY));
        byte[] before = Files.readAllBytes(file);

        assertThrows(Exception.class, () -> EncryptDatabase.run("rekey", base,
                Map.of("FLASHCHORES_DB_OLD_KEY", "not-the-key-0123456789", "FLASHCHORES_DB_KEY", NEW_KEY)));

        assertArrayEquals(before, Files.readAllBytes(file));
        assertFalse(Files.exists(dir.resolve("homechores.mv.db.before-rekey")));
        assertEquals("Mummo Maija", memberName(KEY));
    }

    @Test
    void anOpenDatabase_isRefusedBeforeAnyCopyIsMade_andWhatItWritesMeanwhileSurvives() throws Exception {
        // The "service", holding the database open for the whole attempt.
        try (Connection service = DriverManager.getConnection("jdbc:h2:file:" + base, "sa", "")) {
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> EncryptDatabase.run("encrypt", base, Map.of("FLASHCHORES_DB_KEY", KEY)));
            assertTrue(e.getMessage().contains("Stop the service"), e.getMessage());
            assertFalse(Files.exists(dir.resolve("homechores.mv.db.before-encrypt")),
                    "refused before a copy existed, so there was nothing to put back over the live file");
            service.createStatement().execute("insert into member values ('Pappa Pekka')");
        }
        assertFalse(DatabaseEncryption.isEncrypted(file));
        try (Connection c = DriverManager.getConnection("jdbc:h2:file:" + base + ";IFEXISTS=TRUE", "sa", "");
             ResultSet r = c.createStatement().executeQuery("select count(*) from member")) {
            r.next();
            assertEquals(2, r.getInt(1), "what the service wrote after the refusal is still there");
        }
    }

    @Test
    void rekeyingToTheCurrentKey_isRefused_andChangesNothing() throws Exception {
        EncryptDatabase.run("encrypt", base, Map.of("FLASHCHORES_DB_KEY", KEY));
        byte[] before = Files.readAllBytes(file);

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> EncryptDatabase.run(
                "rekey", base, Map.of("FLASHCHORES_DB_OLD_KEY", KEY, "FLASHCHORES_DB_KEY", KEY)));

        assertTrue(e.getMessage().contains("same"), e.getMessage());
        assertArrayEquals(before, Files.readAllBytes(file));
        assertEquals("Mummo Maija", memberName(KEY));
    }

    @Test
    void refusals_changeNothing() throws Exception {
        byte[] before = Files.readAllBytes(file);
        assertThrows(IllegalStateException.class,
                () -> EncryptDatabase.run("encrypt", base, Map.of("FLASHCHORES_DB_KEY", "short")));
        assertThrows(IllegalStateException.class,
                () -> EncryptDatabase.run("encrypt", base, Map.of()));
        assertThrows(IllegalStateException.class,
                () -> EncryptDatabase.run("decrypt", base, Map.of("FLASHCHORES_DB_KEY", KEY)));
        assertThrows(IllegalStateException.class,
                () -> EncryptDatabase.run("encrypt", dir.resolve("missing"), Map.of("FLASHCHORES_DB_KEY", KEY)));
        assertArrayEquals(before, Files.readAllBytes(file));

        EncryptDatabase.run("encrypt", base, Map.of("FLASHCHORES_DB_KEY", KEY));
        assertThrows(IllegalStateException.class,
                () -> EncryptDatabase.run("encrypt", base, Map.of("FLASHCHORES_DB_KEY", KEY)),
                "encrypting twice");
    }
}
