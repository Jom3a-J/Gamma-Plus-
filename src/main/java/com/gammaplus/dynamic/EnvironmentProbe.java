package com.gammaplus.dynamic;

import com.gammaplus.config.GammaModConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LightLayer;

/**
 * EnvironmentProbe — pure environment queries for Dynamic Lighting.
 *
 * <p>{@link #isCave(ClientLevel, Player)}: sky light at the player's feet is at or below the
 * configured threshold ⇒ no open sky above (real caves, tunnels, thick overhangs).
 * Naturally correct in the Nether/End where sky light is always 0.
 *
 * <p>{@link #isNight(ClientLevel)}: {@code level.getSkyDarken()} (0=day .. 4=full dark, also
 * accounts for thunderstorms) is at or above the configured threshold.
 *
 * <p>Stateless; reads thresholds live from {@link GammaModConfig} so config changes take effect
 * immediately without a restart.
 */
public final class EnvironmentProbe {

    /** Cache a mutable BlockPos per thread to avoid object allocations in hot render/tick paths. */
    private static final ThreadLocal<BlockPos.MutableBlockPos> MUTABLE_POS = ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);

    private EnvironmentProbe() {}

    /** True if the player is underground / under cover (sky light ≤ threshold). */
    public static boolean isCave(ClientLevel level, Player player) {
        if (level == null || player == null) return false;
        BlockPos.MutableBlockPos mutablePos = MUTABLE_POS.get();
        mutablePos.set(player.getX(), player.getEyeY(), player.getZ());
        int skyLight = getAverageSkyLight(level, mutablePos);
        return skyLight <= GammaModConfig.getDynamicCaveSkylightMax();
    }

    /** True if it is night (or storm-darkened) at the player's location. */
    public static boolean isNight(ClientLevel level) {
        if (level == null) return false;
        return level.getSkyDarken() >= GammaModConfig.getDynamicNightDarknessMin();
    }

    /**
     * Calculates the ambient darkness factor at the player's position,
     * continuously scaled between 0.0 (fully bright surface day) and 1.0 (fully dark cave/night).
     *
     * <p>Spatially averages sky light over a 5-block cross to filter out doorway/arch anomalies
     * and produce a natural, progressive transition at cave entrances.
     */
    public static float getDarkness(ClientLevel level, Player player) {
        if (level == null || player == null) return 0.0f;

        // In dimensions without sky light (Nether, End), we are always in "dark mode"
        if (!level.dimensionType().hasSkyLight()) {
            return 1.0f;
        }

        BlockPos.MutableBlockPos mutablePos = MUTABLE_POS.get();
        mutablePos.set(player.getX(), player.getEyeY(), player.getZ());

        // 1. Cave Mechanic: darkness determined by spatial average sky light and local visibility
        int avgSkyLight = getAverageSkyLight(level, mutablePos);
        int caveMax = GammaModConfig.getDynamicCaveSkylightMax();
        float caveDarkness = calculateCaveDarkness(avgSkyLight, caveMax);

        // 2. Surface Mechanic: darkness determined strictly by night/storm levels
        int skyDarken = level.getSkyDarken();
        int nightMin = GammaModConfig.getDynamicNightDarknessMin();
        float nightDarkness = calculateSurfaceNightDarkness(skyDarken, nightMin);

        // Combine using maximum (equivalent to cave OR night)
        float blendedDarkness = Math.max(caveDarkness, nightDarkness);

        // Apply a quadratic curve (x^2) to match human non-linear light perception (Weber-Fechner law).
        // This suppresses the boost under mild shade (light level 12 becomes 4% boost)
        // while allowing a smooth, natural acceleration into full darkness.
        return blendedDarkness * blendedDarkness;
    }

    private static float calculateCaveDarkness(int avgSkyLight, int caveMax) {
        if (avgSkyLight <= caveMax) {
            return 1.0f;
        }
        if (avgSkyLight >= 15) {
            return 0.0f;
        }
        // Linearly interpolate between the cave threshold (1.0 darkness) and full open sky (0.0 darkness)
        return (15.0f - avgSkyLight) / (15.0f - caveMax);
    }

    private static float calculateSurfaceNightDarkness(int skyDarken, int nightMin) {
        if (nightMin <= 0) {
            return 1.0f;
        }
        if (skyDarken >= nightMin) {
            return 1.0f;
        }
        // Linearly interpolate between day (0.0 darkness) and full night threshold (1.0 darkness)
        return (float) skyDarken / nightMin;
    }

    /**
     * Spatially averages the sky light over a 5-block horizontal cross centered on the player.
     * Prevents single-block structures or doorways from causing sudden snaps.
     */
    private static int getAverageSkyLight(ClientLevel level, BlockPos.MutableBlockPos mutablePos) {
        int centerX = mutablePos.getX();
        int centerY = mutablePos.getY();
        int centerZ = mutablePos.getZ();

        int sum = level.getBrightness(LightLayer.SKY, mutablePos);
        sum += level.getBrightness(LightLayer.SKY, mutablePos.set(centerX, centerY, centerZ - 1)); // north
        sum += level.getBrightness(LightLayer.SKY, mutablePos.set(centerX, centerY, centerZ + 1)); // south
        sum += level.getBrightness(LightLayer.SKY, mutablePos.set(centerX + 1, centerY, centerZ)); // east
        sum += level.getBrightness(LightLayer.SKY, mutablePos.set(centerX - 1, centerY, centerZ)); // west

        // Restore center position to avoid side effects for caller
        mutablePos.set(centerX, centerY, centerZ);

        return sum / 5;
    }

}
