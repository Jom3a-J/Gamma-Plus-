package com.gammaplus.config;

import com.gammaplus.GammaMod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * ConfigMigration — moves a config file left behind under an older filename.
 *
 * <p>Split out of {@link GammaModConfig} so it can be tested against a temporary directory:
 * {@code GammaModConfig} resolves its paths from {@code FabricLoader} in a static initialiser,
 * which cannot run outside a Fabric runtime.
 */
final class ConfigMigration {

    enum Result {
        /** Nothing to migrate: the current file already exists, or the old one does not. */
        NOTHING_TO_DO,
        MIGRATED,
        FAILED
    }

    private ConfigMigration() {}

    /**
     * Moves {@code legacy} to {@code current} when — and only when — {@code current} does not yet
     * exist and {@code legacy} does.
     *
     * <p>A move rather than copy-then-delete, so the settings are never in a state where the only
     * copy has been removed. On failure the old file is left untouched, which is recoverable: the
     * user can rename it by hand.
     */
    static Result migrate(Path legacy, Path current) {
        if (Files.exists(current) || !Files.exists(legacy)) {
            return Result.NOTHING_TO_DO;
        }
        try {
            Path parent = current.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.move(legacy, current);
            GammaMod.LOGGER.info("[Gamma Plus] Migrated config {} -> {}",
                    legacy.getFileName(), current.getFileName());
            return Result.MIGRATED;
        } catch (IOException e) {
            GammaMod.LOGGER.warn("[Gamma Plus] Could not migrate {} to {} — the old file was left in place.",
                    legacy.getFileName(), current.getFileName(), e);
            return Result.FAILED;
        }
    }
}
