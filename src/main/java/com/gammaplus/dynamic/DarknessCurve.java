package com.gammaplus.dynamic;

/**
 * DarknessCurve — the pure math behind the Dynamic Lighting darkness score.
 *
 * <p>Deliberately free of Minecraft types: {@link EnvironmentProbe} handles talking to the world
 * and hands the raw light values here. That split is what lets this logic be unit-tested without
 * a running client, which matters because these curves are the part most likely to be tuned.
 *
 * <p>The model is a single question — <em>how much light actually reaches the player?</em> — rather
 * than separate "am I in a cave" and "is it night" tests. One number answers both, and answers
 * them better: an unlit cave scores 0, a torch-lit cave scores high, midday scores 15, and
 * midnight on the surface lands in between. It also handles cases the split model got wrong,
 * such as a fully lit underground base, which used to receive the maximum boost.
 */
public final class DarknessCurve {

    private DarknessCurve() {}

    /** Maximum light level Minecraft reports, for both the block and sky layers. */
    public static final int MAX_LIGHT = 15;

    /**
     * The light level the player can actually see by, matching vanilla's raw-brightness rule.
     *
     * <p>Sky light is stored time-independently — it stays 15 under open sky at midnight — so it
     * only becomes meaningful once {@code skyDarken} is subtracted. Block light needs no such
     * adjustment: a torch is as bright at midnight as at noon. The brighter of the two wins.
     *
     * @param blockLight block-layer light, 0–15
     * @param skyLight   sky-layer light, 0–15
     * @param skyDarken  how far the sky is currently darkened by time of day and weather, 0–15
     * @return effective light, 0–15
     */
    public static float effectiveLight(float blockLight, float skyLight, float skyDarken) {
        float fromSky = Math.max(0.0f, skyLight - skyDarken);
        return clamp(Math.max(blockLight, fromSky), 0.0f, MAX_LIGHT);
    }

    /**
     * Converts an effective light level into a darkness factor.
     *
     * @param effectiveLight from {@link #effectiveLight}, 0–15
     * @param darkLevel      light at or below which the player counts as fully in the dark
     * @param brightLevel    light at or above which no boost is wanted
     * @return darkness, {@code 0.0} (leave alone) to {@code 1.0} (boost fully)
     */
    public static float darkness(float effectiveLight, float darkLevel, float brightLevel) {
        if (effectiveLight <= darkLevel) {
            return 1.0f;
        }
        if (effectiveLight >= brightLevel) {
            return 0.0f;
        }
        // A malformed config (dark >= bright) would divide by zero or invert the ramp; the two
        // branches above already cover every value in that case, so this is only reached when
        // brightLevel is genuinely greater.
        float linear = (brightLevel - effectiveLight) / (brightLevel - darkLevel);

        // Squared to approximate the non-linear response of human vision (Weber–Fechner): mild
        // dimness barely registers, while real darkness ramps up quickly. Halfway between the
        // thresholds gives 25% boost rather than 50%.
        return clamp(linear * linear, 0.0f, 1.0f);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
