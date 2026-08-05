package com.gammaplus.mixin;

import com.gammaplus.GammaModClient;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * GameRendererMixin — makes the fake Night Vision effect render at our configured intensity
 * instead of vanilla's fixed (and end-of-duration pulsing) scale.
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Inject(method = "nightVisionScale", at = @At("HEAD"), cancellable = true, require = 1)
    private static void gammaplus_getNightVisionScale(LivingEntity entity, float tickDelta, CallbackInfoReturnable<Float> cir) {
        if (entity == null) return;

        // Only override the effect we applied ourselves. A potion, beacon, or an effect from
        // /effect — including an infinite one — keeps vanilla's scale untouched.
        if (GammaModClient.isFakeNightVision(entity.getEffect(MobEffects.NIGHT_VISION))) {
            cir.setReturnValue(GammaModClient.getEffectiveNightVisionIntensity());
        }
    }
}
