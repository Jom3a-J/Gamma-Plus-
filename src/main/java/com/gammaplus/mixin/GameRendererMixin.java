package com.gammaplus.mixin;

import com.gammaplus.config.GammaModConfig;
import com.gammaplus.GammaModClient;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Inject(method = "nightVisionScale", at = @At("HEAD"), cancellable = true, require = 1)
    private static void gammaplus_getNightVisionScale(LivingEntity entity, float tickDelta, CallbackInfoReturnable<Float> cir) {
        if (entity != null) {
            MobEffectInstance effect = entity.getEffect(MobEffects.NIGHT_VISION);
            // Only override if the effect is our custom infinite-duration fake Night Vision
            if (effect != null && effect.isInfiniteDuration()) {
                float intensity = 0.0f;
                if (GammaModConfig.isNightVisionEnabled()) {
                    intensity = (float) GammaModConfig.getNightVisionIntensity();
                }
                if (GammaModConfig.isDynamicLightingEnabled()) {
                    intensity = Math.max(intensity, GammaModClient.getDynamicState().getValue());
                }
                cir.setReturnValue(intensity);
            }
        }
    }
}
