package com.gammaplus;

import com.gammaplus.compat.IrisCompat;
import com.gammaplus.config.ConfigScreens;
import com.gammaplus.config.GammaModConfig;
import com.gammaplus.dynamic.DynamicLightingState;
import com.gammaplus.dynamic.EnvironmentProbe;
import com.gammaplus.update.UpdateChecker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.Version;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.net.URI;
import net.minecraft.resources.Identifier;

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
    private static KeyMapping openSettingsKey;

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

    /**
     * The exact effect instance we applied, or {@code null} when we don't own one.
     *
     * <p>Ownership is tracked by identity rather than by inspecting the instance, because
     * duration alone cannot tell our effect apart from a legitimate infinite one — a player
     * who ran {@code /effect give @s night_vision infinite} has an effect that looks exactly
     * like ours. {@code LivingEntity.addEffect} stores the instance we hand it verbatim when
     * no effect of that type is present, so the reference stays valid.
     */
    private static MobEffectInstance fakeNvInstance = null;

    /**
     * Ticks to wait after joining before showing the update notice, so it lands after the join
     * spam (server MOTD, welcome messages) rather than being scrolled away by it.
     */
    private static final int NOTICE_DELAY_TICKS = 60;

    /** Ticks since the player joined, or {@code -1} when not waiting to post a notice. */
    private static int ticksSinceJoin = -1;

    @Override
    public void onInitializeClient() {
        // Load config from disk
        GammaModConfig.load();

        // Register keymappings
        gammaToggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.gammaplus.toggle_gamma",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_G,
                CATEGORY
        ));

        nightVisionToggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.gammaplus.toggle_nightvision",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_N,
                CATEGORY
        ));

        dynamicLightingToggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.gammaplus.toggle_dynamic",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_L,
                CATEGORY
        ));

        // K sits beside the L used for Dynamic Lighting and is unbound in vanilla. Not O, which
        // looks like the obvious choice for "Options" but is already vanilla's Social Interactions
        // ("key.friends") binding.
        openSettingsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.gammaplus.open_settings",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_K,
                CATEGORY
        ));

        // Register client tick handler
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);

        // Keybind toggles save off-thread, so make sure a toggle made just before quitting
        // still reaches disk.
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> GammaModConfig.flushPendingSave());

        // Ask about updates once, in the background, while the player is still at the menu — so
        // the answer is usually ready by the time they load a world.
        UpdateChecker.startAsync();
        // Start the clock unconditionally rather than only when a result is already in: on a slow
        // connection the check can still be in flight when the world finishes loading, and a
        // notice that arrives a second late is far better than one silently dropped.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ticksSinceJoin = 0);

        GammaMod.LOGGER.info("[Gamma Plus] Client initialized — Keybindings registered (G=Gamma, N=NightVision, L=DynamicLighting, K=Settings)");
    }

    private void onClientTick(Minecraft client) {
        if (client.player == null || client.level == null) {
            // Not in a world. Drop the smoother state so the next world doesn't start at the
            // brightness the last one ended on, and forget the effect reference — it belonged
            // to a player entity that no longer exists.
            dynamicState.reset();
            fakeNvInstance = null;
            ticksSinceJoin = -1;
            return;
        }

        tickUpdateNotice(client);

        // Handle keybind presses
        handleKeybinds(client);

        // Maintain fake Night Vision effect for Iris shader compatibility
        maintainFakeNightVision(client);

        // Recompute the Dynamic Lighting target for this tick.
        // (The smoother itself advances in the Mixin's per-frame loop.)
        updateDynamicTarget(client);
    }

    /**
     * Shows the update notice once the post-join delay has elapsed.
     *
     * <p>Delivered from the tick loop rather than straight from the join event for two reasons:
     * the check may still have been in flight when the player joined, and chat sent during the
     * join handler tends to be buried by the server's own welcome messages.
     */
    private void tickUpdateNotice(Minecraft client) {
        if (ticksSinceJoin < 0) return;
        if (++ticksSinceJoin < NOTICE_DELAY_TICKS) return;

        // Past the delay, keep looking each tick until the check produces an answer — two volatile
        // reads, and it stops the moment there is nothing left to say.
        if (!UpdateChecker.hasPendingNotice()) {
            if (UpdateChecker.isFinished()) ticksSinceJoin = -1; // Up to date; stop watching.
            return;
        }
        ticksSinceJoin = -1;
        if (!UpdateChecker.markAnnounced()) return;

        String latest = UpdateChecker.getNewerVersion();
        Version current = UpdateChecker.currentVersion();
        String url = UpdateChecker.getProjectUrl();

        Component link = Component.literal(url).withStyle(style -> style
                .withColor(ChatFormatting.AQUA)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent.OpenUrl(URI.create(url))));

        client.gui.hud.getChat().addClientSystemMessage(
                Component.literal("[Gamma Plus] ").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal("Version " + latest + " is available")
                                .withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(" (you have "
                                        + (current == null ? "?" : current.getFriendlyString()) + ") ")
                                .withStyle(ChatFormatting.GRAY))
                        .append(link));
    }

    private void handleKeybinds(Minecraft client) {
        // Open the settings screen. Passing the current screen (null while playing) as the parent
        // means closing the settings returns straight to the game.
        while (openSettingsKey.consumeClick()) {
            client.setScreenAndShow(ConfigScreens.create(client.gui.screen()));
            return; // A screen is now open; leave the remaining toggles for the next tick.
        }

        // Toggle gamma
        while (gammaToggleKey.consumeClick()) {
            boolean newState = !GammaModConfig.isGammaEnabled();
            GammaModConfig.setGammaEnabled(newState);
            GammaModConfig.saveAsync();

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
            GammaModConfig.saveAsync();

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
            GammaModConfig.saveAsync();

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
     * never clobber a real NV effect (potion/beacon/command) the player actually has.
     */
    private void maintainFakeNightVision(Minecraft client) {
        // Manual NV, or Dynamic Lighting actively driving NV under shaders, both need the effect.
        boolean wantsNV = GammaModConfig.isNightVisionEnabled()
                || (GammaModConfig.isDynamicLightingEnabled() && IrisCompat.areShadersActive());

        MobEffectInstance existing = client.player.getEffect(MobEffects.NIGHT_VISION);

        // Our instance is no longer the active one — the server stripped it, or a real potion
        // replaced it. Drop the stale reference so we stop claiming ownership of whatever is
        // there now.
        if (fakeNvInstance != null && existing != fakeNvInstance) {
            fakeNvInstance = null;
        }

        if (wantsNV) {
            // Apply only when there's no NV at all. An infinite-duration fake never needs
            // refreshing (vanilla doesn't tick it down), so the old "running low" check is gone.
            // We deliberately do NOT interfere with an NV effect the player got elsewhere.
            if (existing == null) {
                // No particles, no icon — invisible to the player except for the brightness.
                MobEffectInstance fakeNV = new MobEffectInstance(
                        MobEffects.NIGHT_VISION,
                        FAKE_NV_DURATION,
                        0,       // amplifier
                        false,   // ambient
                        false,   // showParticles
                        false    // showIcon
                );
                if (client.player.addEffect(fakeNV)) {
                    fakeNvInstance = fakeNV;
                }
            }
        } else if (fakeNvInstance != null) {
            // NV disabled — remove our fake effect. Anything we don't own is left alone.
            client.player.removeEffect(MobEffects.NIGHT_VISION);
            fakeNvInstance = null;
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
     * Checks whether the given instance is the fake Night Vision effect we applied.
     *
     * <p>Compared by identity: an infinite duration is not a usable marker, because
     * {@code /effect give <player> night_vision infinite} produces an effect indistinguishable
     * from ours by any of its fields. Treating that as ours meant we deleted it and drove its
     * brightness from our own sliders.
     */
    public static boolean isFakeNightVision(MobEffectInstance instance) {
        return instance != null && instance == fakeNvInstance;
    }

    /**
     * The intensity our fake Night Vision effect should render at.
     *
     * <p>Clamped to 0.0–1.0 because this feeds the lightmap's {@code NightVisionFactor} blend
     * uniform. Dynamic Lighting only contributes under shaders — that is the case where the
     * brightness path is a no-op, and it's also the only case where the smoother's target is
     * capped to 1.0 rather than sharing gamma's 0–15 range.
     */
    public static float getEffectiveNightVisionIntensity() {
        float intensity = GammaModConfig.isNightVisionEnabled()
                ? (float) GammaModConfig.getNightVisionIntensity()
                : 0.0f;
        if (GammaModConfig.isDynamicLightingEnabled() && IrisCompat.areShadersActive()) {
            intensity = Math.max(intensity, dynamicState.getValue());
        }
        return Math.max(0.0f, Math.min(1.0f, intensity));
    }
}
