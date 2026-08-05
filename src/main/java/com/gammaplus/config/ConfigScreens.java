package com.gammaplus.config;

import com.gammaplus.GammaMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;

/**
 * ConfigScreens — picks the settings screen to open.
 *
 * <p>Cloth Config is a soft dependency: when it is installed we use its richer screen (tabs,
 * per-entry reset arrows, explicit save/cancel), and otherwise we fall back to the built-in
 * {@link GammaModConfigScreen}, which needs nothing beyond vanilla.
 *
 * <p>The {@link ClothConfigScreen} reference lives inside a branch guarded by
 * {@link #CLOTH_AVAILABLE} so the JVM only ever resolves that class — and the {@code me.shedaniel}
 * types it imports — when Cloth really is present. Hoisting it out (to a field initialiser or a
 * method reference evaluated up front) would resolve it eagerly and throw
 * {@link NoClassDefFoundError} on every install without Cloth.
 */
public final class ConfigScreens {

    private static final boolean CLOTH_AVAILABLE =
            FabricLoader.getInstance().isModLoaded("cloth-config");

    private ConfigScreens() {}

    /** Mod Menu entry point: {@code ConfigScreens::create} is a {@code ConfigScreenFactory}. */
    public static Screen create(Screen parent) {
        if (CLOTH_AVAILABLE) {
            try {
                return ClothConfigScreen.create(parent);
            } catch (Throwable t) {
                // A Cloth major version could rename or drop the builder API. Losing the nicer
                // screen is not worth failing to open settings at all.
                GammaMod.LOGGER.warn("[Gamma Plus] Cloth Config screen unavailable — "
                        + "falling back to the built-in screen.", t);
            }
        }
        return new GammaModConfigScreen(parent);
    }
}
