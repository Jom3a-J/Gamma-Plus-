package com.gammaplus.config;

import com.gammaplus.compat.IrisCompat;
import com.gammaplus.dynamic.DarknessCurve;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * GammaModConfigScreen — the built-in settings screen, with no third-party dependencies.
 *
 * <p>Built on vanilla's own options framework ({@link OptionsSubScreen} + {@link OptionInstance}
 * + {@code OptionsList}), the same machinery behind Video Settings. That buys a scrolling entry
 * list, the standard title/Done header-footer layout, resize handling, tooltips, and controller
 * and narrator support — none of which a hand-positioned screen gets for free. It is also why
 * this screen cannot repeat the old bug where the buttons fell off the bottom at high GUI
 * scales: the list scrolls instead of overflowing.
 *
 * <p>Sliders are integer-valued because vanilla's slider value sets are: levels use 10% steps
 * (0–150 ⇒ 0.0–15.0), night vision uses 1% steps, and the transition rate uses tenths. Every
 * write goes through a {@link GammaModConfig} setter, which re-clamps, so a bad value cannot
 * reach the renderer.
 *
 * <p>Following the vanilla options convention, edits apply live and are persisted when the
 * screen closes — there is no separate cancel. {@link ClothConfigScreen} is offered instead
 * when Cloth Config happens to be installed; see {@link ConfigScreens}.
 */
public class GammaModConfigScreen extends OptionsSubScreen {

    /** Brightness levels are edited in 10% steps, so 0.0–15.0 becomes a 0–150 slider. */
    private static final int LEVEL_STEP_MAX = (int) Math.round(GammaModConfig.GAMMA_MAX * 10);
    private static final int RATE_TENTHS_MIN = (int) Math.round(GammaModConfig.RATE_MIN * 10);
    private static final int RATE_TENTHS_MAX = (int) Math.round(GammaModConfig.RATE_MAX * 10);

    public GammaModConfigScreen(Screen parent) {
        super(parent, Minecraft.getInstance().options, Component.translatable("title.gammaplus.config"));
    }

    @Override
    protected void addOptions() {
        // === Gamma Boost ===
        this.list.addHeader(Component.translatable("category.gammaplus.gamma"));
        if (IrisCompat.areShadersActive()) {
            // Gamma is bypassed while a shader pack renders, so say so rather than letting the
            // slider look broken.
            this.list.addHeader(Component.translatable("text.gammaplus.gamma.shader_warning")
                    .withStyle(ChatFormatting.GOLD));
        }
        this.list.addSmall(gammaEnabled(), gammaLevel());

        // === Night Vision ===
        this.list.addHeader(Component.translatable("category.gammaplus.nightvision"));
        if (IrisCompat.isIrisInstalled()) {
            this.list.addHeader(Component.translatable("text.gammaplus.nightvision.shader_note")
                    .withStyle(ChatFormatting.GREEN));
        }
        this.list.addSmall(nightVisionEnabled(), nightVisionIntensity());

        // === Dynamic Lighting ===
        this.list.addHeader(Component.translatable("category.gammaplus.dynamic"));
        if (IrisCompat.areShadersActive()) {
            this.list.addHeader(Component.translatable("text.gammaplus.dynamic.shader_note")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        this.list.addSmall(dynamicEnabled(), dynamicHighLevel());
        this.list.addSmall(dynamicNormalLevel(), dynamicRate());
        this.list.addSmall(dynamicDarkLight(), dynamicBrightLight());
        this.list.addSmall(dynamicBlockLightInfluence());

        // === General ===
        this.list.addHeader(Component.translatable("category.gammaplus.general"));
        this.list.addSmall(updateCheckEnabled());
    }

    @Override
    public void removed() {
        // Vanilla options convention: edits apply live, and the file is written on the way out.
        GammaModConfig.save();
        super.removed();
    }

    // === Gamma Boost ===

    private static OptionInstance<Boolean> gammaEnabled() {
        return OptionInstance.createBoolean(
                "option.gammaplus.gamma_enabled",
                tooltip("option.gammaplus.gamma_enabled.tooltip"),
                GammaModConfig.isGammaEnabled(),
                GammaModConfig::setGammaEnabled);
    }

    private static OptionInstance<Integer> gammaLevel() {
        return new OptionInstance<>(
                "option.gammaplus.gamma_level",
                tooltip("option.gammaplus.gamma_level.tooltip"),
                GammaModConfigScreen::percentLabel,
                new OptionInstance.IntRange(0, LEVEL_STEP_MAX),
                toLevelStep(GammaModConfig.getGammaLevel()),
                value -> GammaModConfig.setGammaLevel(fromLevelStep(value)));
    }

    // === Night Vision ===

    private static OptionInstance<Boolean> nightVisionEnabled() {
        return OptionInstance.createBoolean(
                "option.gammaplus.nv_enabled",
                tooltip("option.gammaplus.nv_enabled.tooltip"),
                GammaModConfig.isNightVisionEnabled(),
                GammaModConfig::setNightVisionEnabled);
    }

    private static OptionInstance<Integer> nightVisionIntensity() {
        // Floored: below this an "enabled" night vision produces no visible change.
        int floorPercent = (int) Math.round(GammaModConfig.getNightVisionFloor() * 100);
        return new OptionInstance<>(
                "option.gammaplus.nv_intensity",
                tooltip("option.gammaplus.nv_intensity.tooltip"),
                (caption, value) -> Options.genericValueLabel(caption, Component.literal(value + "%")),
                new OptionInstance.IntRange(floorPercent, 100),
                (int) Math.round(GammaModConfig.getNightVisionIntensity() * 100),
                value -> GammaModConfig.setNightVisionIntensity(value / 100.0));
    }

    // === Dynamic Lighting ===

    private static OptionInstance<Boolean> dynamicEnabled() {
        return OptionInstance.createBoolean(
                "option.gammaplus.dynamic_enabled",
                tooltip("option.gammaplus.dynamic_enabled.tooltip"),
                GammaModConfig.isDynamicLightingEnabled(),
                GammaModConfig::setDynamicLightingEnabled);
    }

    private static OptionInstance<Integer> dynamicHighLevel() {
        return new OptionInstance<>(
                "option.gammaplus.dynamic_high",
                tooltip("option.gammaplus.dynamic_high.tooltip"),
                GammaModConfigScreen::percentLabel,
                new OptionInstance.IntRange(0, LEVEL_STEP_MAX),
                toLevelStep(GammaModConfig.getDynamicHighLevel()),
                value -> GammaModConfig.setDynamicHighLevel(fromLevelStep(value)));
    }

    private static OptionInstance<Integer> dynamicNormalLevel() {
        return new OptionInstance<>(
                "option.gammaplus.dynamic_normal",
                tooltip("option.gammaplus.dynamic_normal.tooltip"),
                GammaModConfigScreen::percentLabel,
                new OptionInstance.IntRange(0, LEVEL_STEP_MAX),
                toLevelStep(GammaModConfig.getDynamicNormalLevel()),
                value -> GammaModConfig.setDynamicNormalLevel(fromLevelStep(value)));
    }

    private static OptionInstance<Integer> dynamicRate() {
        return new OptionInstance<>(
                "option.gammaplus.dynamic_rate",
                tooltip("option.gammaplus.dynamic_rate.tooltip"),
                // Locale.ROOT: the default locale renders "2.0" as "٢٫٠" on an Arabic-locale
                // machine, which is not what a numeric setting should show.
                (caption, value) -> Options.genericValueLabel(
                        caption, Component.literal(String.format(Locale.ROOT, "%.1f", value / 10.0))),
                new OptionInstance.IntRange(RATE_TENTHS_MIN, RATE_TENTHS_MAX),
                (int) Math.round(GammaModConfig.getDynamicTransitionRate() * 10),
                value -> GammaModConfig.setDynamicTransitionRate(value / 10.0));
    }

    private static OptionInstance<Integer> dynamicDarkLight() {
        return new OptionInstance<>(
                "option.gammaplus.dynamic_dark_light",
                tooltip("option.gammaplus.dynamic_dark_light.tooltip"),
                Options::genericValueLabel,
                new OptionInstance.IntRange(0, DarknessCurve.MAX_LIGHT - 1),
                GammaModConfig.getDynamicDarkLightLevel(),
                GammaModConfig::setDynamicDarkLightLevel);
    }

    private static OptionInstance<Integer> dynamicBrightLight() {
        return new OptionInstance<>(
                "option.gammaplus.dynamic_bright_light",
                tooltip("option.gammaplus.dynamic_bright_light.tooltip"),
                Options::genericValueLabel,
                new OptionInstance.IntRange(1, DarknessCurve.MAX_LIGHT),
                GammaModConfig.getDynamicBrightLightLevel(),
                GammaModConfig::setDynamicBrightLightLevel);
    }

    private static OptionInstance<Integer> dynamicBlockLightInfluence() {
        return new OptionInstance<>(
                "option.gammaplus.dynamic_torch_influence",
                tooltip("option.gammaplus.dynamic_torch_influence.tooltip"),
                (caption, value) -> Options.genericValueLabel(caption, Component.literal(value + "%")),
                new OptionInstance.IntRange(0, 100),
                (int) Math.round(GammaModConfig.getDynamicBlockLightInfluence() * 100),
                value -> GammaModConfig.setDynamicBlockLightInfluence(value / 100.0));
    }

    private static OptionInstance<Boolean> updateCheckEnabled() {
        return OptionInstance.createBoolean(
                "option.gammaplus.update_check",
                tooltip("option.gammaplus.update_check.tooltip"),
                GammaModConfig.isUpdateCheckEnabled(),
                GammaModConfig::setUpdateCheckEnabled);
    }

    // === Helpers ===

    private static <T> OptionInstance.TooltipSupplier<T> tooltip(String translationKey) {
        return OptionInstance.cachedConstantTooltip(Component.translatable(translationKey));
    }

    /** Slider label for a 10%-step brightness level: step 150 reads as "1500%". */
    private static Component percentLabel(Component caption, int step) {
        return Options.genericValueLabel(caption, Component.literal(step * 10 + "%"));
    }

    private static int toLevelStep(double level) {
        return (int) Math.round(level * 10);
    }

    private static double fromLevelStep(int step) {
        return step / 10.0;
    }
}
