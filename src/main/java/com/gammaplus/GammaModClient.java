package com.gammaplus;

import com.gammaplus.compat.IrisCompat;
import com.gammaplus.config.GammaModConfig;
import com.gammaplus.dynamic.DynamicLightingState;
import com.gammaplus.dynamic.EnvironmentProbe;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * GammaModClient — Client-side entrypoint.
 * Registers keymappings, tick handler, and manages the fake Night Vision effect for Iris compatibility.
 */
@Environment(EnvType.CLIENT)
public class GammaModClient implements ClientModInitializer {

    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("gammaplus", "controls")
    );

    private static KeyMapping gammaToggleKey;
    private static KeyMapping nightVisionToggleKey;
    private static KeyMapping dynamicLightingToggleKey;

    /** Shared smoother instance — the Mixin reads this every frame. */
    private static final DynamicLightingState dynamicState = new DynamicLightingState();
    public static DynamicLightingState getDynamicState() { return dynamicState; }

    /**
     * Duration for our fake NV effect. We use vanilla's own infinite sentinel (-1) so that
     * {@code isInfiniteDuration()} recognises it and vanilla never counts it down. This is
     * critical: with {@code Integer.MAX_VALUE} the duration ticked down each tick, and after
     * ~1s our marker check failed, so toggling NV off left the effect stuck on the player.
     */
    private static final int FAKE_NV_DURATION = MobEffectInstance.INFINITE_DURATION;

    @Override
    public void onInitializeClient() {
        // Load config from disk
        GammaModConfig.load();

        // Register keymappings
        gammaToggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.lumencraft.toggle_gamma",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                CATEGORY
        ));

        nightVisionToggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.lumencraft.toggle_nightvision",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_N,
                CATEGORY
        ));

        dynamicLightingToggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.lumencraft.toggle_dynamic",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_L,
                CATEGORY
        ));

        // Register client tick handler
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);

        GammaMod.LOGGER.info("[Gamma Plus] Client initialized — Keybindings registered (G=Gamma, N=NightVision, L=DynamicLighting)");
    }

    private void onClientTick(Minecraft client) {
        if (client.player == null) return;

        // Handle keybind presses
        handleKeybinds(client);

        // Maintain fake Night Vision effect for Iris shader compatibility
        maintainFakeNightVision(client);

        // Recompute the Dynamic Lighting target for this tick.
        // (The smoother itself advances in the Mixin's per-frame loop.)
        updateDynamicTarget(client);
    }

    private void handleKeybinds(Minecraft client) {
        // Toggle gamma
        while (gammaToggleKey.consumeClick()) {
            boolean newState = !GammaModConfig.isGammaEnabled();
            GammaModConfig.setGammaEnabled(newState);
            GammaModConfig.save();

            String status = newState ? "ON" : "OFF";
            ChatFormatting color = newState ? ChatFormatting.GREEN : ChatFormatting.RED;

            if (newState && IrisCompat.areShadersActive()) {
                // Warn that gamma doesn't work with shaders
                client.gui.hud.setOverlayMessage(
                        Component.literal("§6[Gamma Plus] §fGamma §a" + status + " §7(limited with shaders — use Night Vision)"),
                        false
                );
            } else {
                client.gui.hud.setOverlayMessage(
                        Component.literal("§6[Gamma Plus] §fGamma: " + color + status + " §7(" + (int)(GammaModConfig.getGammaLevel() * 100) + "%)"),
                        false
                );
            }
        }

        // Toggle night vision
        while (nightVisionToggleKey.consumeClick()) {
            boolean newState = !GammaModConfig.isNightVisionEnabled();
            GammaModConfig.setNightVisionEnabled(newState);
            GammaModConfig.save();

            String status = newState ? "ON" : "OFF";
            ChatFormatting color = newState ? ChatFormatting.GREEN : ChatFormatting.RED;

            client.gui.hud.setOverlayMessage(
                    Component.literal("§6[Gamma Plus] §fNight Vision: " + color + status + " §7(" + (int)(GammaModConfig.getNightVisionIntensity() * 100) + "%)"),
                    false
            );
        }

        // Toggle dynamic lighting
        while (dynamicLightingToggleKey.consumeClick()) {
            boolean newState = !GammaModConfig.isDynamicLightingEnabled();
            GammaModConfig.setDynamicLightingEnabled(newState);
            GammaModConfig.save();

            String status = newState ? "ON" : "OFF";
            ChatFormatting color = newState ? ChatFormatting.GREEN : ChatFormatting.RED;

            if (newState && IrisCompat.areShadersActive()) {
                // Under shaders, brightness is a no-op — Dynamic Lighting drives Night Vision instead.
                client.gui.hud.setOverlayMessage(
                        Component.literal("§6[Gamma Plus] §fDynamic Lighting §a" + status + " §7(driving Night Vision under shaders)"),
                        false
                );
            } else {
                client.gui.hud.setOverlayMessage(
                        Component.literal("§6[Gamma Plus] §fDynamic Lighting: " + color + status),
                        false
                );
            }
        }
    }

    /**
     * Maintains a fake client-side Night Vision effect when NV is enabled.
     * This is critical for Iris compatibility — Iris reads the player's status effects
     * and sets the nightVision shader uniform accordingly.
     *
     * <p>This is self-healing against server effect-sync packets in multiplayer: if the
     * server strips our client-only effect, {@code existing} becomes {@code null} and we
     * re-add it on the very next client tick (worst case: a single-frame flicker). We
     * never clobber a real NV effect (potion/beacon) the player actually has.
     */
    private void maintainFakeNightVision(Minecraft client) {
        if (client.player == null) return;

        // Manual NV, or Dynamic Lighting actively driving NV under shaders, both need the effect.
        boolean wantsNV = GammaModConfig.isNightVisionEnabled()
                || (GammaModConfig.isDynamicLightingEnabled() && IrisCompat.areShadersActive());

        if (wantsNV) {
            // Check if player already has night vision (real or our fake)
            MobEffectInstance existing = client.player.getEffect(MobEffects.NIGHT_VISION);

            // Apply only when there's no NV at all. An infinite-duration fake never needs
            // refreshing (vanilla doesn't tick it down), so the old "running low" check is gone.
            // We deliberately do NOT interfere with a real NV effect (potion/beacon).
            boolean needsApply = existing == null;

            if (needsApply) {
                // No particles, no icon — invisible to the player except for the brightness.
                // Infinite duration is also our unique marker (see isFakeNightVision).
                MobEffectInstance fakeNV = new MobEffectInstance(
                        MobEffects.NIGHT_VISION,
                        FAKE_NV_DURATION,
                        0,       // amplifier
                        false,   // ambient
                        false,   // showParticles
                        false    // showIcon
                );
                client.player.addEffect(fakeNV);
            }
        } else {
            // NV disabled — remove our fake effect if present
            MobEffectInstance existing = client.player.getEffect(MobEffects.NIGHT_VISION);
            if (existing != null && isFakeNightVision(existing)) {
                client.player.removeEffect(MobEffects.NIGHT_VISION);
            }
        }
    }

    /**
     * Sets the Dynamic Lighting smoother's target based on the current environment.
     * Called once per client tick. When disabled, target is 0 so the value fades out gracefully.
     */
    private void updateDynamicTarget(Minecraft client) {
        if (!GammaModConfig.isDynamicLightingEnabled()
                || client.level == null || client.player == null) {
            dynamicState.setTarget(0.0f);
            return;
        }
        float darkness = EnvironmentProbe.getDarkness(client.level, client.player);
        float target = (float) (GammaModConfig.getDynamicNormalLevel() + 
                (GammaModConfig.getDynamicHighLevel() - GammaModConfig.getDynamicNormalLevel()) * darkness);
        if (IrisCompat.areShadersActive()) {
            target = Math.min(1.0f, target);
        }
        dynamicState.setTarget(target);
    }

    /**
     * Checks if the given MobEffectInstance is our fake Night Vision effect
     * by checking for our unique duration marker.
     */
    /**
     * Checks if the given MobEffectInstance is our fake Night Vision effect.
     *
     * <p>We use vanilla's infinite duration (-1) as the marker, so this is simply
     * {@link MobEffectInstance#isInfiniteDuration()}. Real NV from potions/beacons has a
     * finite countdown duration, so this never mis-identifies a real effect as ours.
     */
    private static boolean isFakeNightVision(MobEffectInstance instance) {
        return instance.isInfiniteDuration();
    }
}
