package com.gammaplus.mixin;

import com.gammaplus.compat.IrisCompat;
import com.gammaplus.config.GammaModConfig;
import com.gammaplus.GammaModClient;
import com.gammaplus.dynamic.DynamicLightingState;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * LightmapRenderStateExtractorMixin — Modifies the lightmap render state.
 *
 * <p>This Mixin injects at the HEAD and TAIL of {@code extract()}.
 *
 * <p><b>HEAD</b> forces vanilla's private {@code needsUpdate} flag true every frame.
 * This is essential: vanilla's {@code extract()} early-returns when {@code needsUpdate}
 * is false, which means it does NOT recompute {@code renderState.brightness} from the
 * gamma option. If we only set {@code renderState.needsUpdate} (the public copy) at TAIL,
 * vanilla never sees it, and the boosted brightness we wrote gets frozen forever — even
 * after every toggle is turned off. By keeping vanilla's real flag true at HEAD, vanilla
 * recomputes a fresh baseline every frame, and our TAIL layer always stacks on top of a
 * current value. When all features are off, vanilla's baseline simply takes over.
 *
 * <p><b>TAIL</b> applies gamma boost, night vision intensity, and the Dynamic Lighting
 * smoother value on top of vanilla's freshly-computed state.
 */
@Mixin(LightmapRenderStateExtractor.class)
public abstract class LightmapRenderStateExtractorMixin {

    /** Vanilla's private "is the lightmap dirty" flag. Setting it true bypasses its early-return. */
    @Shadow private boolean needsUpdate;

    @Unique
    private static boolean wasActive = false;

    @Unique
    private static boolean cachedShadersActive = false;

    @Unique
    private static long lastShaderCheckMs = 0L;

    /** Iris state sits behind reflection, so cache it for 200ms instead of querying per frame. */
    @Unique
    private static boolean gammaplus$shadersActive() {
        long now = System.currentTimeMillis();
        if (now - lastShaderCheckMs > 200L) {
            lastShaderCheckMs = now;
            cachedShadersActive = IrisCompat.areShadersActive();
        }
        return cachedShadersActive;
    }

    /** True when a feature would write to the render state on this frame. */
    @Unique
    private static boolean gammaplus$isActive() {
        if (GammaModConfig.isNightVisionEnabled()) return true;
        if (GammaModConfig.isGammaEnabled() && !gammaplus$shadersActive()) return true;
        return GammaModConfig.isDynamicLightingEnabled()
                || GammaModClient.getDynamicState().getValue() > 0.0001f;
    }

    /**
     * HEAD injection: keep vanilla's needsUpdate true so extract()'s full body always runs.
     * Without this, a value we boosted at TAIL would be frozen in renderState forever once
     * vanilla's per-tick refresh lapses — manifesting as permanent fullbright with all
     * toggles off.
     *
     * <p>Only forced while we are actually driving the lightmap, plus the one frame after we
     * stop so vanilla's own value can take over. Forcing it unconditionally rebuilt and
     * re-uploaded the lightmap texture on every single frame even with every feature switched
     * off, making the mod cost frames just by being installed.
     */
    @Inject(method = "extract", at = @At("HEAD"), require = 1)
    private void gammaplus_forceRecompute(LightmapRenderState renderState, float partialTicks, CallbackInfo ci) {
        if (gammaplus$isActive() || wasActive) {
            needsUpdate = true;
        }
    }

    @Inject(method = "extract", at = @At("TAIL"), require = 1)
    private void gammaplus_applyLightmapBoost(LightmapRenderState renderState, float partialTicks, CallbackInfo ci) {
        boolean shadersActive = gammaplus$shadersActive();

        boolean gammaActive = GammaModConfig.isGammaEnabled() && !shadersActive;
        boolean nvActive = GammaModConfig.isNightVisionEnabled();

        // === Dynamic Lighting: advance the smoother only if active or fading out ===
        DynamicLightingState dynamic = GammaModClient.getDynamicState();
        float dynamicValue = dynamic.getValue();
        boolean dynamicOn = GammaModConfig.isDynamicLightingEnabled() || dynamicValue > 0.0001f;

        if (dynamicOn) {
            dynamic.update(GammaModConfig.getDynamicTransitionRate());
            dynamicValue = dynamic.getValue();
        }

        // Skip if no features are active, but ensure we force one clean update if disabling
        boolean anyFeatureActive = gammaActive || nvActive || dynamicOn;
        if (!anyFeatureActive) {
            if (wasActive) {
                renderState.needsUpdate = true;
                wasActive = false;
            }
            return;
        }
        wasActive = true;

        // --- Manual Night Vision ---
        if (nvActive) {
            // Night vision intensity is a 0.0–1.0 blend consumed by the lightmap shader's
            // mix(NightVisionFactor) uniform. max(), not assignment: a real potion already at
            // full strength must not be dimmed down to our slider value.
            renderState.nightVisionEffectIntensity = Math.max(
                    renderState.nightVisionEffectIntensity,
                    (float) GammaModConfig.getNightVisionIntensity());
        }

        // --- Dynamic Lighting: drive brightness (no shaders) or NV (shaders) ---
        if (dynamicOn) {
            if (!shadersActive) {
                // Brightness path — same max() semantics as manual gamma so the two layers never darken each other.
                renderState.brightness = Math.max(renderState.brightness, dynamicValue);
            } else {
                // Under Iris the brightness uniform is a no-op; ramp NV intensity instead.
                // The fake NV effect itself is kept present by GammaModClient.maintainFakeNightVision().
                // Clamped because the smoother shares gamma's 0–15 range and can still be above
                // 1.0 while decaying if shaders were switched on mid-ramp.
                renderState.nightVisionEffectIntensity = Math.max(
                        renderState.nightVisionEffectIntensity,
                        Math.min(1.0f, dynamicValue));
            }
        }

        // --- Manual Gamma Boost ---
        if (gammaActive) {
            float factor = (float) GammaModConfig.getGammaLevel();

            // brightness is a 0.0–1.0 blend factor consumed by the lightmap shader:
            //   mix(color, notGamma(color), BrightnessFactor)
            // notGamma() lifts the brightness of mid/dark tones without blowing out
            // already-bright ones, so raising this blend toward 1.0 gives a smooth
            // fullbright-like effect. We take the max so we never darken whatever
            // vanilla already set.
            renderState.brightness = Math.max(renderState.brightness, factor);
        }

        // We reach here only when at least one feature wrote to the state.
        renderState.needsUpdate = true;
    }
}
