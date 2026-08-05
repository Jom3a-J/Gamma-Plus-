package com.gammaplus.dynamic;

import com.gammaplus.config.GammaModConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LightLayer;

/**
 * EnvironmentProbe — reads how dark it is around the player, for Dynamic Lighting.
 *
 * <p>Answers "cave or not" and "night or day" with one measurement rather than two tests: the
 * effective light level at the player's eyes, combining block light with sky light reduced by the
 * time of day. See {@link DarknessCurve} for why that is both simpler and more accurate.
 *
 * <p>Stateless; reads thresholds live from {@link GammaModConfig} so config changes take effect
 * immediately without a restart.
 */
public final class EnvironmentProbe {

    /** Cache a mutable BlockPos per thread to avoid object allocations in hot render/tick paths. */
    private static final ThreadLocal<BlockPos.MutableBlockPos> MUTABLE_POS = ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);

    private EnvironmentProbe() {}

    /**
     * How dark the player's surroundings are, {@code 0.0} (bright) to {@code 1.0} (pitch dark).
     *
     * <p>Dimensions without sky light need no special case: their sky light is zero everywhere, so
     * the reading falls through to block light alone. That is more accurate than the blanket
     * "always fully dark" this used to apply — standing beside Nether lava now correctly reads as
     * lit.
     */
    public static float getDarkness(ClientLevel level, Player player) {
        if (level == null || player == null) return 0.0f;

        BlockPos.MutableBlockPos mutablePos = MUTABLE_POS.get();
        mutablePos.set(player.getX(), player.getEyeY(), player.getZ());

        float light = sampleEffectiveLight(level, mutablePos, skyDarken(level),
                (float) GammaModConfig.getDynamicBlockLightInfluence());
        return DarknessCurve.darkness(light,
                GammaModConfig.getDynamicDarkLightLevel(),
                GammaModConfig.getDynamicBrightLightLevel());
    }

    /**
     * How far the sky is currently darkened, 0–15, as a fraction rather than a whole number.
     *
     * <p>{@code Level.getSkyDarken()} truncates this to an int, which turns dusk into eleven
     * visible steps. The underlying attribute is a float and is what vanilla itself rounds, so
     * reading it directly gives a continuous sunset. Falls back to the rounded value if the
     * attribute is ever unavailable.
     */
    private static float skyDarken(ClientLevel level) {
        try {
            Float skyLightLevel = level.environmentAttributes()
                    .getDimensionValue(EnvironmentAttributes.SKY_LIGHT_LEVEL);
            if (skyLightLevel != null) {
                return DarknessCurve.MAX_LIGHT - skyLightLevel;
            }
        } catch (Throwable ignored) {
            // Attribute system unavailable or reshaped — fall through to the int.
        }
        return level.getSkyDarken();
    }

    /**
     * Effective light around the player, taking the brightest of the eye position and its four
     * horizontal neighbours.
     *
     * <p>The maximum, not the mean. Light stored inside a solid block is zero, so averaging made
     * the mod think a corridor was darker than it was simply because the player stood near a
     * wall. Taking the brightest sample is also what vanilla does for its own local-brightness
     * checks, and it keeps a doorway or a torch just out of reach from being averaged away.
     */
    private static float sampleEffectiveLight(ClientLevel level, BlockPos.MutableBlockPos pos,
                                              float skyDarken, float blockInfluence) {
        int centerX = pos.getX();
        int centerY = pos.getY();
        int centerZ = pos.getZ();

        float best = effectiveLightAt(level, pos, skyDarken, blockInfluence);
        best = Math.max(best, effectiveLightAt(level, pos.set(centerX, centerY, centerZ - 1), skyDarken, blockInfluence));
        best = Math.max(best, effectiveLightAt(level, pos.set(centerX, centerY, centerZ + 1), skyDarken, blockInfluence));
        best = Math.max(best, effectiveLightAt(level, pos.set(centerX + 1, centerY, centerZ), skyDarken, blockInfluence));
        best = Math.max(best, effectiveLightAt(level, pos.set(centerX - 1, centerY, centerZ), skyDarken, blockInfluence));

        // Restore the centre position to avoid side effects for the caller.
        pos.set(centerX, centerY, centerZ);
        return best;
    }

    private static float effectiveLightAt(ClientLevel level, BlockPos pos, float skyDarken, float blockInfluence) {
        return DarknessCurve.effectiveLight(
                level.getBrightness(LightLayer.BLOCK, pos),
                level.getBrightness(LightLayer.SKY, pos),
                skyDarken,
                blockInfluence);
    }
}
