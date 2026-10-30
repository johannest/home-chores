package com.homechores;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Encrypts the H2 database file at rest when {@code FLASHCHORES_DB_KEY} is set (README,
 * "Encrypting the database").
 *
 * <p>H2 does the work: {@code CIPHER=AES} in the URL, and the password becomes "file key, space,
 * user password". This class only builds that from the ordinary {@code spring.datasource.*}
 * settings, so {@code application.properties} — and the admin tool's {@code --db-url} — stay as
 * they are, and a dev checkout without the variable runs unencrypted as before.
 *
 * <p>What it protects: a copy of {@code data/homechores.mv.db} that leaves the host — a hosting
 * backup, a misconfigured web root, a disk image. What it does not: anyone who can read the
 * service's environment or its memory, since the key has to be on the host for the app to start
 * unattended.
 *
 * <p>A key that does not match the file is refused here, before H2 sees it, with a message that
 * says what to do: an encrypted file with no key would otherwise read as "file corrupted", and
 * a key set over a plain file would quietly fail every login. The one-off conversion is
 * {@code tools/flashchores-admin.py encrypt-db} ({@link com.homechores.maintenance.EncryptDatabase}).
 */
@Configuration(proxyBeanMethods = false)
public class DatabaseEncryption {

    /** H2's {@code FilePathEncrypt} starts every encrypted file with this. */
    private static final byte[] ENCRYPTED_HEADER = "H2encrypt\n".getBytes(StandardCharsets.US_ASCII);

    /** Shorter keys are refused: the file is only as safe as the key is hard to guess. */
    static final int MIN_KEY_LENGTH = 16;

    private static final String FILE_URL = "jdbc:h2:file:";

    /**
     * Replaces Spring Boot's properties-backed connection details, which step aside for this.
     *
     * @param key the file key; blank leaves the database as it is
     */
    @Bean
    JdbcConnectionDetails jdbcConnectionDetails(DataSourceProperties properties,
                                                @Value("${homechores.db.key:}") String key) {
        Settings s = resolve(properties.determineUrl(), properties.determineUsername(),
                properties.determinePassword(), key);
        String driver = properties.determineDriverClassName();
        return new JdbcConnectionDetails() {
            @Override
            public String getUsername() {
                return s.username();
            }

            @Override
            public String getPassword() {
                return s.password();
            }

            @Override
            public String getJdbcUrl() {
                return s.url();
            }

            @Override
            public String getDriverClassName() {
                return driver;
            }
        };
    }

    record Settings(String url, String username, String password) {
    }

    /**
     * The connection settings with encryption applied, or unchanged where it does not apply (an
     * in-memory database, or no key).
     *
     * @throws IllegalStateException when the key and the file disagree, or the key is unusable
     */
    static Settings resolve(String url, String username, String password, String key) {
        Settings plain = new Settings(url, username, password);
        if (url == null || !url.startsWith(FILE_URL)) {
            return plain;
        }
        Path file = databaseFile(url);
        boolean encrypted = isEncrypted(file);
        if (key == null || key.isBlank()) {
            if (encrypted) {
                throw new IllegalStateException(file + " is encrypted, but FLASHCHORES_DB_KEY is "
                        + "not set. Start the app with the key it was encrypted with.");
            }
            return plain;
        }
        requireUsableKey(key);
        if (Files.exists(file) && !encrypted) {
            throw new IllegalStateException(file + " is not encrypted yet, but FLASHCHORES_DB_KEY "
                    + "is set. Stop the app and run `tools/flashchores-admin.py encrypt-db` once, "
                    + "or unset the key.");
        }
        // H2's trace file sits beside the database unencrypted and copies failing statements
        // into it, values included; the app's own log already records the same exceptions.
        return new Settings(url + ";CIPHER=AES;TRACE_LEVEL_FILE=0", username,
                key + " " + Objects.toString(password, ""));
    }

    /** @throws IllegalStateException for a key H2 cannot take, or one too short to protect */
    public static void requireUsableKey(String key) {
        // H2 splits the password at the first space: file key before it, user password after.
        if (key == null || key.chars().anyMatch(Character::isWhitespace)
                || key.length() < MIN_KEY_LENGTH) {
            throw new IllegalStateException("FLASHCHORES_DB_KEY must be at least "
                    + MIN_KEY_LENGTH + " characters with no spaces, for example the output of "
                    + "`openssl rand -base64 32`.");
        }
    }

    /** {@code jdbc:h2:file:./data/homechores;…} → {@code ./data/homechores.mv.db}. */
    static Path databaseFile(String url) {
        String rest = url.substring(FILE_URL.length());
        int settings = rest.indexOf(';');
        return Path.of((settings < 0 ? rest : rest.substring(0, settings)) + ".mv.db");
    }

    /** Whether {@code file} is an H2 database encrypted with {@code CIPHER=AES}; false when
     *  there is no such file yet. */
    public static boolean isEncrypted(Path file) {
        if (!Files.isRegularFile(file)) {
            return false;
        }
        try (InputStream in = Files.newInputStream(file)) {
            return Arrays.equals(in.readNBytes(ENCRYPTED_HEADER.length), ENCRYPTED_HEADER);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + file, e);
        }
    }
}
