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
