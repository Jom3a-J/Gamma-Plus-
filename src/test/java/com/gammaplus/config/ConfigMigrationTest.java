package com.gammaplus.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the one-shot rename of {@code lumencraft.json} to {@code gammaplus.json}.
 *
 * <p>This runs once on the machine of every existing user, and a mistake silently resets their
 * settings — so the no-op cases matter as much as the happy path.
 */
class ConfigMigrationTest {

    private static final String SETTINGS = "{\"gammaEnabled\":true,\"gammaLevel\":7.5}";

    @Test
    @DisplayName("moves the old file and preserves its contents")
    void migratesOldFile(@TempDir Path dir) throws IOException {
        Path legacy = dir.resolve("lumencraft.json");
        Path current = dir.resolve("gammaplus.json");
        Files.writeString(legacy, SETTINGS);

        assertEquals(ConfigMigration.Result.MIGRATED, ConfigMigration.migrate(legacy, current));

        assertTrue(Files.exists(current), "the new file should now exist");
        assertFalse(Files.exists(legacy), "the old file should have been moved, not copied");
        assertEquals(SETTINGS, Files.readString(current), "settings must survive the rename");
    }

    @Test
    @DisplayName("leaves an existing config alone")
    void doesNotClobberExistingConfig(@TempDir Path dir) throws IOException {
        Path legacy = dir.resolve("lumencraft.json");
        Path current = dir.resolve("gammaplus.json");
        Files.writeString(legacy, SETTINGS);
        Files.writeString(current, "{\"gammaLevel\":1.0}");

        assertEquals(ConfigMigration.Result.NOTHING_TO_DO, ConfigMigration.migrate(legacy, current));

        assertEquals("{\"gammaLevel\":1.0}", Files.readString(current),
                "a config the user is already using must never be overwritten");
        assertTrue(Files.exists(legacy), "the old file is left as-is rather than destroyed");
    }

    @Test
    @DisplayName("does nothing on a fresh install")
    void doesNothingWhenNoLegacyFile(@TempDir Path dir) {
        Path legacy = dir.resolve("lumencraft.json");
        Path current = dir.resolve("gammaplus.json");

        assertEquals(ConfigMigration.Result.NOTHING_TO_DO, ConfigMigration.migrate(legacy, current));

        assertFalse(Files.exists(current), "no config should be conjured out of nothing");
    }

    @Test
    @DisplayName("creates the config directory if it is missing")
    void createsMissingParentDirectory(@TempDir Path dir) throws IOException {
        Path legacy = dir.resolve("lumencraft.json");
        Path current = dir.resolve("nested").resolve("gammaplus.json");
        Files.writeString(legacy, SETTINGS);

        assertEquals(ConfigMigration.Result.MIGRATED, ConfigMigration.migrate(legacy, current));

        assertTrue(Files.exists(current));
        assertEquals(SETTINGS, Files.readString(current));
    }

    @Test
    @DisplayName("is safe to run twice")
    void isIdempotent(@TempDir Path dir) throws IOException {
        Path legacy = dir.resolve("lumencraft.json");
        Path current = dir.resolve("gammaplus.json");
        Files.writeString(legacy, SETTINGS);

        assertEquals(ConfigMigration.Result.MIGRATED, ConfigMigration.migrate(legacy, current));
        assertEquals(ConfigMigration.Result.NOTHING_TO_DO, ConfigMigration.migrate(legacy, current));

        assertEquals(SETTINGS, Files.readString(current));
    }
}
