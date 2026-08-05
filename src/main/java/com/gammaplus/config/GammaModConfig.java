package com.gammaplus.config;

import com.gammaplus.GammaMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * GammaModConfig — Manages mod configuration state and persistence.
 * Settings are saved as JSON to the config directory.
 */
public class GammaModConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final String CONFIG_FILE = "gammaplus.json";
    /** Filename used before the mod was renamed from LumenCraft; migrated on first load. */
    private static final String LEGACY_CONFIG_FILE = "lumencraft.json";

    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir().resolve(CONFIG_FILE);
    private static final Path LEGACY_CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir().resolve(LEGACY_CONFIG_FILE);

    /** Minimum NV intensity — below this an "enabled" NV has no visible effect and looks broken. */
    private static final double NV_FLOOR = 0.1;

    /** Maximum gamma level. 1.0 = full notGamma blend; values above over-brighten past the shader's clamp. */
    public static final double GAMMA_MAX = 15.0;

    /** Bounds of the Dynamic Lighting transition rate exposed by the settings screen. */
    public static final double RATE_MIN = 0.5;
    public static final double RATE_MAX = 10.0;

    // === Defaults ===
    // Shared with the settings screen so its "reset to default" arrows report the real values.
    public static final boolean DEFAULT_GAMMA_ENABLED   = false;
    public static final double  DEFAULT_GAMMA_LEVEL     = 15.0;
    public static final boolean DEFAULT_NV_ENABLED      = false;
    public static final double  DEFAULT_NV_INTENSITY    = 1.0;
    public static final boolean DEFAULT_DYNAMIC_ENABLED = false;
    public static final double  DEFAULT_DYNAMIC_HIGH    = 15.0;
    public static final double  DEFAULT_DYNAMIC_NORMAL  = 0.0;
    public static final double  DEFAULT_DYNAMIC_RATE    = 2.0;
    public static final int     DEFAULT_DYNAMIC_CAVE_SKYLIGHT_MAX  = 0;
    public static final int     DEFAULT_DYNAMIC_NIGHT_DARKNESS_MIN = 3;

    private static ConfigData config = new ConfigData();

    // === Getters ===
    public static boolean isGammaEnabled() { return config.gammaEnabled; }
    public static double getGammaLevel() { return config.gammaLevel; }
    public static boolean isNightVisionEnabled() { return config.nightVisionEnabled; }
    public static double getNightVisionIntensity() { return config.nightVisionIntensity; }
    public static double getNightVisionFloor() { return NV_FLOOR; }
    public static double getGammaMax() { return GAMMA_MAX; }

    // === Setters ===
    public static void setGammaEnabled(boolean enabled) { config.gammaEnabled = enabled; }
    public static void setGammaLevel(double level) { config.gammaLevel = Math.max(0.0, Math.min(GAMMA_MAX, level)); }
    public static void setNightVisionEnabled(boolean enabled) { config.nightVisionEnabled = enabled; }
    public static void setNightVisionIntensity(double intensity) { config.nightVisionIntensity = Math.max(NV_FLOOR, Math.min(1.0, intensity)); }

    // === Dynamic Lighting getters ===
    public static boolean isDynamicLightingEnabled()    { return config.dynamicLightingEnabled; }
    public static double  getDynamicHighLevel()         { return config.dynamicHighLevel; }
    public static double  getDynamicNormalLevel()       { return config.dynamicNormalLevel; }
    public static double  getDynamicTransitionRate()    { return config.dynamicTransitionRate; }
    public static int     getDynamicCaveSkylightMax()   { return config.dynamicCaveSkylightMax; }
    public static int     getDynamicNightDarknessMin()  { return config.dynamicNightDarknessMin; }

    // === Dynamic Lighting setters ===
    public static void setDynamicLightingEnabled(boolean enabled) { config.dynamicLightingEnabled = enabled; }
    public static void setDynamicHighLevel(double level)        { config.dynamicHighLevel       = Math.max(0.0, Math.min(GAMMA_MAX, level)); }
    public static void setDynamicNormalLevel(double level)      { config.dynamicNormalLevel     = Math.max(0.0, Math.min(GAMMA_MAX, level)); }
    public static void setDynamicTransitionRate(double rate)    { config.dynamicTransitionRate  = Math.max(0.1, Math.min(10.0, rate)); }
    public static void setDynamicCaveSkylightMax(int max)       { config.dynamicCaveSkylightMax = Math.max(0, Math.min(15, max)); }
    public static void setDynamicNightDarknessMin(int min)      { config.dynamicNightDarknessMin= Math.max(0, Math.min(4, min)); }

    // === JSON Serialization Model ===
    private static class ConfigData {
        private boolean gammaEnabled = DEFAULT_GAMMA_ENABLED;
        private double gammaLevel = DEFAULT_GAMMA_LEVEL;
        private boolean nightVisionEnabled = DEFAULT_NV_ENABLED;
        private double nightVisionIntensity = DEFAULT_NV_INTENSITY;

        // Dynamic Lighting — defaults applied automatically when absent from an old config file
        private boolean dynamicLightingEnabled = DEFAULT_DYNAMIC_ENABLED;
        private double  dynamicHighLevel       = DEFAULT_DYNAMIC_HIGH;
        private double  dynamicNormalLevel     = DEFAULT_DYNAMIC_NORMAL;
        private double  dynamicTransitionRate  = DEFAULT_DYNAMIC_RATE;
        private int     dynamicCaveSkylightMax = DEFAULT_DYNAMIC_CAVE_SKYLIGHT_MAX;
        private int     dynamicNightDarknessMin = DEFAULT_DYNAMIC_NIGHT_DARKNESS_MIN;

        private void validate() {
            gammaLevel = Math.max(0.0, Math.min(GAMMA_MAX, gammaLevel));
            nightVisionIntensity = Math.max(NV_FLOOR, Math.min(1.0, nightVisionIntensity));
            dynamicHighLevel = Math.max(0.0, Math.min(GAMMA_MAX, dynamicHighLevel));
            dynamicNormalLevel = Math.max(0.0, Math.min(GAMMA_MAX, dynamicNormalLevel));
            dynamicTransitionRate = Math.max(0.1, Math.min(RATE_MAX, dynamicTransitionRate));
            dynamicCaveSkylightMax = Math.max(0, Math.min(15, dynamicCaveSkylightMax));
            dynamicNightDarknessMin = Math.max(0, Math.min(4, dynamicNightDarknessMin));
        }
    }

    /**
     * Load config from disk. Creates defaults if file doesn't exist.
     *
     * <p>Settings written under the old {@code lumencraft.json} name are carried over the
     * first time this runs, so renaming the file doesn't silently reset anyone's setup.
     */
    public static void load() {
        migrateLegacyConfig();

        if (!Files.exists(CONFIG_PATH)) {
            GammaMod.LOGGER.info("[Gamma Plus] No config found, using defaults.");
            save(); // Create default file
            return;
        }

        try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
            ConfigData loaded = GSON.fromJson(reader, ConfigData.class);
            if (loaded != null) {
                config = loaded;
                config.validate();
                GammaMod.LOGGER.info("[Gamma Plus] Config loaded — Gamma: {} ({}%), NV: {} ({}%), Dynamic: {}",
                        config.gammaEnabled, (int)(config.gammaLevel * 100),
                        config.nightVisionEnabled, (int)(config.nightVisionIntensity * 100),
                        config.dynamicLightingEnabled);
            }
        } catch (IOException | com.google.gson.JsonSyntaxException e) {
            GammaMod.LOGGER.error("[Gamma Plus] Failed to load config, using defaults.", e);
        }
    }

    /**
     * Moves a pre-rename config file to the current filename.
     *
     * <p>A move rather than a copy-then-delete: the settings are never in a state where the
     * only copy has been removed. If anything goes wrong the old file is left untouched and
     * defaults are used, which is recoverable — the user can rename it by hand.
     */
    private static void migrateLegacyConfig() {
        if (Files.exists(CONFIG_PATH) || !Files.exists(LEGACY_CONFIG_PATH)) {
            return;
        }
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.move(LEGACY_CONFIG_PATH, CONFIG_PATH);
            GammaMod.LOGGER.info("[Gamma Plus] Migrated config {} -> {}", LEGACY_CONFIG_FILE, CONFIG_FILE);
        } catch (IOException e) {
            GammaMod.LOGGER.warn("[Gamma Plus] Could not migrate {} to {} — the old file was left in place.",
                    LEGACY_CONFIG_FILE, CONFIG_FILE, e);
        }
    }

    /**
     * Save current config to disk.
     */
    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            GammaMod.LOGGER.error("[Gamma Plus] Failed to save config.", e);
        }
    }
}
