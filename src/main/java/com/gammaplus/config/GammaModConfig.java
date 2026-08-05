package com.gammaplus.config;

import com.gammaplus.GammaMod;
import com.gammaplus.dynamic.DarknessCurve;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

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
    /** Light level at or below which the boost is at full strength. Surface midnight sits at 4. */
    public static final int     DEFAULT_DYNAMIC_DARK_LIGHT   = 4;
    /** Light level at or above which no boost is applied. Well short of midday's 15. */
    public static final int     DEFAULT_DYNAMIC_BRIGHT_LIGHT = 12;
    /**
     * How much placed light counts toward "it is bright here", 0.0–1.0.
     *
     * <p>Half by default. At 1.0 a single torch reads 13–14 and cancels the boost outright, which
     * is technically correct and unpleasant to play with; halving it leaves a torch-lit cave near
     * half boost while a fully lit room still drops well below that.
     */
    public static final double  DEFAULT_DYNAMIC_BLOCK_LIGHT_INFLUENCE = 0.5;
    /** Whether to ask Modrinth once per session whether a newer build exists. */
    public static final boolean DEFAULT_UPDATE_CHECK_ENABLED = true;

    private static ConfigData config = new ConfigData();

    /** Single-threaded so queued writes are serialised; daemon so it never holds up shutdown. */
    private static final ExecutorService SAVE_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "GammaPlus-config-save");
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicBoolean savePending = new AtomicBoolean(false);

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
    public static int     getDynamicDarkLightLevel()    { return config.dynamicDarkLightLevel; }
    public static int     getDynamicBrightLightLevel()  { return config.dynamicBrightLightLevel; }
    public static double  getDynamicBlockLightInfluence() { return config.dynamicBlockLightInfluence; }
    public static boolean isUpdateCheckEnabled()          { return config.updateCheckEnabled; }
    public static void setUpdateCheckEnabled(boolean enabled) { config.updateCheckEnabled = enabled; }

    // === Dynamic Lighting setters ===
    public static void setDynamicLightingEnabled(boolean enabled) { config.dynamicLightingEnabled = enabled; }
    public static void setDynamicHighLevel(double level)        { config.dynamicHighLevel       = Math.max(0.0, Math.min(GAMMA_MAX, level)); }
    public static void setDynamicNormalLevel(double level)      { config.dynamicNormalLevel     = Math.max(0.0, Math.min(GAMMA_MAX, level)); }
    public static void setDynamicTransitionRate(double rate)    { config.dynamicTransitionRate  = Math.max(0.1, Math.min(10.0, rate)); }

    /** Kept strictly below the bright threshold, since an inverted pair has no sensible ramp. */
    public static void setDynamicDarkLightLevel(int level) {
        config.dynamicDarkLightLevel = Math.max(0, Math.min(DarknessCurve.MAX_LIGHT - 1, level));
        config.dynamicBrightLightLevel = Math.max(config.dynamicDarkLightLevel + 1, config.dynamicBrightLightLevel);
    }

    public static void setDynamicBrightLightLevel(int level) {
        config.dynamicBrightLightLevel = Math.max(1, Math.min(DarknessCurve.MAX_LIGHT, level));
        config.dynamicDarkLightLevel = Math.min(config.dynamicBrightLightLevel - 1, config.dynamicDarkLightLevel);
    }

    public static void setDynamicBlockLightInfluence(double influence) {
        config.dynamicBlockLightInfluence = Math.max(0.0, Math.min(1.0, influence));
    }

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
        private int     dynamicDarkLightLevel   = DEFAULT_DYNAMIC_DARK_LIGHT;
        private int     dynamicBrightLightLevel = DEFAULT_DYNAMIC_BRIGHT_LIGHT;
        private double  dynamicBlockLightInfluence = DEFAULT_DYNAMIC_BLOCK_LIGHT_INFLUENCE;
        private boolean updateCheckEnabled = DEFAULT_UPDATE_CHECK_ENABLED;

        // Superseded thresholds from the split cave/night model, boxed so that "absent from the
        // file" is distinguishable from "present and zero". Read once by migrateThresholds() and
        // then dropped, so they disappear from the file on the next save.
        private Integer dynamicCaveSkylightMax;
        private Integer dynamicNightDarknessMin;

        private void validate() {
            gammaLevel = Math.max(0.0, Math.min(GAMMA_MAX, gammaLevel));
            nightVisionIntensity = Math.max(NV_FLOOR, Math.min(1.0, nightVisionIntensity));
            dynamicHighLevel = Math.max(0.0, Math.min(GAMMA_MAX, dynamicHighLevel));
            dynamicNormalLevel = Math.max(0.0, Math.min(GAMMA_MAX, dynamicNormalLevel));
            dynamicTransitionRate = Math.max(0.1, Math.min(RATE_MAX, dynamicTransitionRate));

            dynamicBlockLightInfluence = Math.max(0.0, Math.min(1.0, dynamicBlockLightInfluence));
            dynamicDarkLightLevel = Math.max(0, Math.min(DarknessCurve.MAX_LIGHT - 1, dynamicDarkLightLevel));
            dynamicBrightLightLevel = Math.max(1, Math.min(DarknessCurve.MAX_LIGHT, dynamicBrightLightLevel));
            if (dynamicBrightLightLevel <= dynamicDarkLightLevel) {
                // An inverted pair leaves no ramp at all; give the bright end priority.
                dynamicDarkLightLevel = dynamicBrightLightLevel - 1;
            }
        }
    }

    /**
     * Folds the retired cave/night thresholds into the effective-light thresholds that replaced
     * them, so an existing config keeps behaving as its owner intended.
     *
     * <p>The old model asked two questions — "is sky light at or below {@code caveSkylightMax}?"
     * and "is {@code getSkyDarken()} at or above {@code nightDarknessMin}?" — and the new one asks
     * a single "how much light reaches the player?". The mapping follows from what each threshold
     * meant in light-level terms:
     *
     * <ul>
     *   <li>{@code caveSkylightMax} was already a light level at or below which to boost fully,
     *       which is exactly {@code dynamicDarkLightLevel}.</li>
     *   <li>{@code nightDarknessMin} counted <em>downward</em> from full daylight, so a threshold
     *       of 3 meant "start boosting once light drops to 12" — hence {@code 15 - min}.</li>
     * </ul>
     */
    private static void migrateThresholds(ConfigData data) {
        if (data.dynamicCaveSkylightMax == null && data.dynamicNightDarknessMin == null) {
            return;
        }
        if (data.dynamicCaveSkylightMax != null) {
            data.dynamicDarkLightLevel = data.dynamicCaveSkylightMax;
        }
        if (data.dynamicNightDarknessMin != null) {
            data.dynamicBrightLightLevel = DarknessCurve.MAX_LIGHT - data.dynamicNightDarknessMin;
        }
        data.dynamicCaveSkylightMax = null;
        data.dynamicNightDarknessMin = null;
        GammaMod.LOGGER.info("[Gamma Plus] Migrated Dynamic Lighting thresholds to the light-level model: dark<={}, bright>={}",
                data.dynamicDarkLightLevel, data.dynamicBrightLightLevel);
    }

    /**
     * Load config from disk. Creates defaults if file doesn't exist.
     *
     * <p>Settings written under the old {@code lumencraft.json} name are carried over the
     * first time this runs, so renaming the file doesn't silently reset anyone's setup.
     */
    public static void load() {
        ConfigMigration.migrate(LEGACY_CONFIG_PATH, CONFIG_PATH);

        if (!Files.exists(CONFIG_PATH)) {
            GammaMod.LOGGER.info("[Gamma Plus] No config found, using defaults.");
            save(); // Create default file
            return;
        }

        boolean migrated = false;
        try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
            ConfigData loaded = GSON.fromJson(reader, ConfigData.class);
            if (loaded != null) {
                migrated = loaded.dynamicCaveSkylightMax != null || loaded.dynamicNightDarknessMin != null;
                migrateThresholds(loaded);
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

        if (migrated) {
            save(); // Rewrite immediately so the retired keys stop lingering in the file.
        }
    }

    /**
     * Queue a save without blocking the caller.
     *
     * <p>For saves triggered during gameplay — the G/N/L keybinds — where a synchronous write on
     * the render thread can cost a frame if the disk is busy. Repeated toggles coalesce into a
     * single write, and the single-threaded executor keeps writes from interleaving.
     *
     * <p>Deliberate saves from a settings screen still call {@link #save()} directly: the player
     * is in a menu, so timing does not matter, and a synchronous write is one less way to lose
     * the change.
     */
    public static void saveAsync() {
        if (!savePending.compareAndSet(false, true)) {
            return; // A write is already queued; it will pick up this change too.
        }
        try {
            SAVE_EXECUTOR.execute(() -> {
                savePending.set(false);
                save();
            });
        } catch (RejectedExecutionException e) {
            savePending.set(false);
            save(); // Executor is shutting down — write on this thread rather than lose it.
        }
    }

    /**
     * Blocks briefly until any queued save has been written. Called when the client stops, so a
     * toggle made moments before quitting is not lost with the daemon thread.
     */
    public static void flushPendingSave() {
        try {
            // The executor is single-threaded and FIFO, so an empty task completing means every
            // save queued before it has already run.
            SAVE_EXECUTOR.submit(() -> { }).get(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException e) {
            GammaMod.LOGGER.warn("[Gamma Plus] Timed out flushing the config save.", e);
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
