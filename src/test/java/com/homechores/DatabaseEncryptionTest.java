package com.homechores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.homechores.DatabaseEncryption.Settings;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.h2.api.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Encryption at rest (§5): the key turns {@code CIPHER=AES} on, the data really is unreadable
 * without it, and a key that disagrees with the file stops the app with a message instead of
 * reaching H2.
 */
class DatabaseEncryptionTest {

    private static final String KEY = "0123456789abcdefKEY";

    @TempDir
    Path dir;

    private String url() {
        return "jdbc:h2:file:" + dir.resolve("homechores") + ";DB_CLOSE_DELAY=-1";
    }

    @Test
    void withoutAKey_aPlainFileIsLeftAlone() {
        Settings s = DatabaseEncryption.resolve(url(), "sa", "", "");
        assertEquals(new Settings(url(), "sa", ""), s);
    }

    @Test
    void anInMemoryDatabase_isNeverTouched_evenWithAKey() {
        String mem = "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1";
        assertEquals(new Settings(mem, "sa", ""), DatabaseEncryption.resolve(mem, "sa", "", KEY));
    }

    @Test
    void aKey_encryptsANewFile_whoseContentsCannotBeReadWithoutIt() throws Exception {
        Settings s = DatabaseEncryption.resolve(url(), "sa", "", KEY);
        assertEquals(url() + ";CIPHER=AES;TRACE_LEVEL_FILE=0", s.url());
        assertEquals(KEY + " ", s.password(), "file key, a space, then the user's own password");

        try (Connection c = DriverManager.getConnection(s.url(), s.username(), s.password())) {
            c.createStatement().execute("create table member(name varchar)");
            c.createStatement().execute("insert into member values ('Mummo Maija')");
            c.createStatement().execute("shutdown");
        }
        Path file = dir.resolve("homechores.mv.db");
        assertTrue(DatabaseEncryption.isEncrypted(file));
        assertFalse(new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1)
                .contains("Mummo Maija"), "no plaintext name in the file");
        assertFalse(Files.exists(dir.resolve("homechores.trace.db")), "no unencrypted trace file");

        SQLException wrongKey = assertThrows(SQLException.class, () -> DriverManager.getConnection(
                url() + ";CIPHER=AES", "sa", "wrong-key-0123456789 ").close());
        assertEquals(ErrorCode.FILE_ENCRYPTION_ERROR_1, wrongKey.getErrorCode());
    }

    @Test
    void anEncryptedFile_withNoKey_refusesToStart_namingTheVariable() throws Exception {
        Settings s = DatabaseEncryption.resolve(url(), "sa", "", KEY);
        DriverManager.getConnection(s.url(), s.username(), s.password()).close();

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> DatabaseEncryption.resolve(url(), "sa", "", ""));
        assertTrue(e.getMessage().contains("FLASHCHORES_DB_KEY is not set"), e.getMessage());
    }

    @Test
    void aKey_overAPlainFile_refusesToStart_pointingAtTheConversion() throws Exception {
        DriverManager.getConnection(url(), "sa", "").close();

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> DatabaseEncryption.resolve(url(), "sa", "", KEY));
        assertTrue(e.getMessage().contains("encrypt-db"), e.getMessage());
    }

    @Test
    void aShortKey_orOneWithASpace_isRefused() {
        assertThrows(IllegalStateException.class,
                () -> DatabaseEncryption.resolve(url(), "sa", "", "short"));
        assertThrows(IllegalStateException.class,
                () -> DatabaseEncryption.resolve(url(), "sa", "", "0123456789 abcdefgh"));
    }

    @Test
    void theFileIsFoundFromTheUrl_ignoringItsSettings() {
        assertEquals(Path.of("./data/homechores.mv.db"),
                DatabaseEncryption.databaseFile("jdbc:h2:file:./data/homechores;DB_CLOSE_DELAY=-1"));
        assertEquals(Path.of("/srv/x.mv.db"), DatabaseEncryption.databaseFile("jdbc:h2:file:/srv/x"));
    }
}
