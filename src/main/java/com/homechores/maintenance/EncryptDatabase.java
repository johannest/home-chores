package com.homechores.maintenance;

import com.homechores.DatabaseEncryption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import org.h2.api.ErrorCode;
import org.h2.store.FileLister;
import org.h2.tools.ChangeFileEncryption;

/**
 * Converts the H2 file in place: {@code encrypt} (plain → {@code FLASHCHORES_DB_KEY}),
 * {@code decrypt} (back to plain, the rollback) and {@code rekey} ({@code FLASHCHORES_DB_OLD_KEY}
 * → {@code FLASHCHORES_DB_KEY}). Driven by {@code tools/flashchores-admin.py encrypt-db} and its
 * siblings, with the service stopped.
 *
 * <p>A plain {@code main}, not a {@link MaintenanceRunner} command: Spring would open the database
 * this has to rewrite. Keys come from the environment only, never the command line, where every
 * user of a shared host can read them in the process list.
 *
 * <p>A database that is still open is refused before anything else happens. Then the file is
 * copied aside, converted, and opened with the new key to prove it reads. Only then is the copy
 * deleted — it holds the old form, plaintext after an {@code encrypt} — and on any failure the
 * copy is put back, so the database is never left half converted. The order matters: a copy put
 * back over a file the running service is still writing would replace its work with the moment
 * the copy was taken, so the open check comes first and no copy exists to put back wrongly.
 */
public final class EncryptDatabase {

    private EncryptDatabase() {
    }

    /** @param args the command, then the database's base path ({@code data/homechores}) */
    public static void main(String[] args) {
        if (args.length != 2) {
            fail("usage: EncryptDatabase encrypt|decrypt|rekey <base path, e.g. data/homechores>");
        }
        try {
            System.out.println(run(args[0], Path.of(args[1]), System.getenv()));
        } catch (IllegalStateException | IllegalArgumentException e) {
            fail(e.getMessage());
        } catch (Exception e) {
            fail(args[0] + " failed, the database is unchanged: " + e);
        }
    }

    private static void fail(String message) {
        System.err.println(message);
        System.exit(2);
    }

    /** @return what was done, for the operator */
    static String run(String command, Path base, Map<String, String> env) throws Exception {
        Path file = Path.of(base + ".mv.db");
        if (!Files.isRegularFile(file)) {
            throw new IllegalStateException("No database at " + file);
        }
        boolean encrypted = DatabaseEncryption.isEncrypted(file);
        String key = env.get("FLASHCHORES_DB_KEY");
        String from;
        String to;
        switch (command) {
            case "encrypt" -> {
                require(!encrypted, file + " is already encrypted.");
                DatabaseEncryption.requireUsableKey(key);
                from = null;
                to = key;
            }
            case "decrypt" -> {
                require(encrypted, file + " is not encrypted.");
                require(key != null, "Set FLASHCHORES_DB_KEY to the key it is encrypted with.");
                from = key;
                to = null;
            }
            case "rekey" -> {
                require(encrypted, file + " is not encrypted; use encrypt.");
                from = env.get("FLASHCHORES_DB_OLD_KEY");
                require(from != null, "Set FLASHCHORES_DB_OLD_KEY to the current key.");
                DatabaseEncryption.requireUsableKey(key);
                // The shell that runs the service often carries the current key already; taking
                // it as the new one would report a rotation that never happened.
                require(!from.equals(key), "FLASHCHORES_DB_KEY is the same as FLASHCHORES_DB_OLD_KEY:"
                        + " nothing to change.");
                to = key;
            }
            default -> throw new IllegalArgumentException("Unknown command: " + command);
        }

        requireClosed(base, file);
        Path aside = file.resolveSibling(file.getFileName() + ".before-" + command);
        Files.copy(file, aside, StandardCopyOption.REPLACE_EXISTING);
        try {
            ChangeFileEncryption.execute(base.toAbsolutePath().getParent().toString(),
                    base.getFileName().toString(), "AES",
                    chars(from), chars(to), true);
            requireReadable(base, to);
        } catch (Exception e) {
            try {
                Files.move(aside, file, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception restoring) {
                // The conversion's failure is the story; the copy that could not be put back is
                // a footnote to it, and the operator still has that copy on disk.
                e.addSuppressed(restoring);
            }
            throw e;
        }
        Files.delete(aside);

        if (to == null) {
            return "Decrypted " + file + ". Unset FLASHCHORES_DB_KEY before starting the app.";
        }
        // The trace file is never encrypted and holds past failing statements; with the key set
        // the app stops writing one (DatabaseEncryption), so the old one is only a leak.
        Files.deleteIfExists(Path.of(base + ".trace.db"));
        return (command.equals("encrypt") ? "Encrypted " : "Re-keyed ") + file
                + ". Start the app with FLASHCHORES_DB_KEY set to this key, and keep a copy of the"
                + " key somewhere other than this host: without it the data cannot be read.";
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static char[] chars(String key) {
        return key == null ? null : key.toCharArray();
    }

    /**
     * H2's own in-use check — the lock file, and the lock the store holds on the file itself — run
     * before the copy exists. The service's own refusal, not H2's, because the operator has to read
     * it and act on it.
     */
    private static void requireClosed(Path base, Path file) {
        try {
            FileLister.tryUnlockDatabase(FileLister.getDatabaseFiles(
                    base.toAbsolutePath().getParent().toString(), base.getFileName().toString(), true),
                    "encryption");
        } catch (SQLException e) {
            throw new IllegalStateException(file + " is open. Stop the service first; nothing was"
                    + " changed.", e);
        }
    }

    /**
     * Opens the converted file with {@code key}. H2 decrypts the store before it checks the user,
     * so a refused login still proves the file reads; only a wrong file key is a failure. The
     * user's own password is the app's business, and this tool does not need to know it.
     */
    private static void requireReadable(Path base, String key) throws SQLException {
        String url = "jdbc:h2:file:" + base.toAbsolutePath() + ";IFEXISTS=TRUE"
                + (key == null ? "" : ";CIPHER=AES");
        try (Connection c = DriverManager.getConnection(url, "sa", key == null ? "" : key + " ")) {
            c.isValid(1);
        } catch (SQLException e) {
            if (e.getErrorCode() != ErrorCode.WRONG_USER_OR_PASSWORD) {
                throw e;
            }
        }
    }
}
