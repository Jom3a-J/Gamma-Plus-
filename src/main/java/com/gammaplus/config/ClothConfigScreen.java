package com.gammaplus.config;

import com.gammaplus.compat.IrisCompat;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * ClothConfigScreen — the richer settings screen, used only when Cloth Config is installed.
 *
 * <p><b>Every reference to this class must sit behind a Cloth-is-loaded check.</b> It is the
 * only class in the mod that touches {@code me.shedaniel} types, which is what keeps
 * {@link GammaModConfigScreen} usable — and the mod loadable — without Cloth on the classpath.
 * {@link ConfigScreens} is the single guarded entry point.
 *
 * <p>One tab per feature. Cloth owns the layout, scrolling and the reset-to-default arrows, so
 * every entry declares its real default from {@link GammaModConfig}.
 *
 * <p>Values are held as integers because Cloth only ships integer sliders: levels are stored as
 * percent (0–1500 ⇒ 0.0–15.0) and the transition rate as tenths (5–100 ⇒ 0.5–10.0). The save
 * consumers convert back and go through the {@code GammaModConfig} setters, which re-clamp, so
 * a bad value can never reach the renderer.
 *
 * <p>Cloth applies entries only when the user confirms — cancelling or pressing Escape leaves
 * the live config untouched, so no manual revert bookkeeping is needed.
 */
public final class ClothConfigScreen {

    private static final int PERCENT_MAX = (int) Math.round(GammaModConfig.GAMMA_MAX * 100);
    private static final int RATE_TENTHS_MIN = (int) Math.round(GammaModConfig.RATE_MIN * 10);
    private static final int RATE_TENTHS_MAX = (int) Math.round(GammaModConfig.RATE_MAX * 10);

    private ClothConfigScreen() {}

    public static Screen create(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("title.gammaplus.config"))
                .setSavingRunnable(GammaModConfig::save);

        ConfigEntryBuilder entries = builder.entryBuilder();

        buildGammaCategory(builder, entries);
        buildNightVisionCategory(builder, entries);
        buildDynamicCategory(builder, entries);

        return builder.build();
    }

    private static void buildGammaCategory(ConfigBuilder builder, ConfigEntryBuilder entries) {
        ConfigCategory category = builder.getOrCreateCategory(
                Component.translatable("category.gammaplus.gamma"));

        // Gamma is bypassed while a shader pack is rendering, so say so up front rather than
        // letting the slider look broken.
        if (IrisCompat.areShadersActive()) {
            category.addEntry(entries.startTextDescription(
                    Component.translatable("text.gammaplus.gamma.shader_warning")
                            .withStyle(ChatFormatting.GOLD)).build());
        }

        category.addEntry(entries.startBooleanToggle(
                        Component.translatable("option.gammaplus.gamma_enabled"),
                        GammaModConfig.isGammaEnabled())
                .setDefaultValue(GammaModConfig.DEFAULT_GAMMA_ENABLED)
                .setTooltip(Component.translatable("option.gammaplus.gamma_enabled.tooltip"))
                .setSaveConsumer(GammaModConfig::setGammaEnabled)
                .build());

        category.addEntry(entries.startIntSlider(
                        Component.translatable("option.gammaplus.gamma_level"),
                        toPercent(GammaModConfig.getGammaLevel()), 0, PERCENT_MAX)
                .setDefaultValue(toPercent(GammaModConfig.DEFAULT_GAMMA_LEVEL))
                .setTextGetter(ClothConfigScreen::percentText)
                .setTooltip(Component.translatable("option.gammaplus.gamma_level.tooltip"))
                .setSaveConsumer(value -> GammaModConfig.setGammaLevel(fromPercent(value)))
                .build());
    }

    private static void buildNightVisionCategory(ConfigBuilder builder, ConfigEntryBuilder entries) {
        ConfigCategory category = builder.getOrCreateCategory(
                Component.translatable("category.gammaplus.nightvision"));

        if (IrisCompat.isIrisInstalled()) {
            category.addEntry(entries.startTextDescription(
                    Component.translatable("text.gammaplus.nightvision.shader_note")
                            .withStyle(ChatFormatting.GREEN)).build());
        }

        category.addEntry(entries.startBooleanToggle(
                        Component.translatable("option.gammaplus.nv_enabled"),
                        GammaModConfig.isNightVisionEnabled())
                .setDefaultValue(GammaModConfig.DEFAULT_NV_ENABLED)
                .setTooltip(Component.translatable("option.gammaplus.nv_enabled.tooltip"))
                .setSaveConsumer(GammaModConfig::setNightVisionEnabled)
                .build());

        // Floored: below this an "enabled" night vision produces no visible change.
        int nvFloorPercent = toPercent(GammaModConfig.getNightVisionFloor());
        category.addEntry(entries.startIntSlider(
                        Component.translatable("option.gammaplus.nv_intensity"),
                        toPercent(GammaModConfig.getNightVisionIntensity()), nvFloorPercent, 100)
                .setDefaultValue(toPercent(GammaModConfig.DEFAULT_NV_INTENSITY))
                .setTextGetter(ClothConfigScreen::percentText)
                .setTooltip(Component.translatable("option.gammaplus.nv_intensity.tooltip"))
                .setSaveConsumer(value -> GammaModConfig.setNightVisionIntensity(fromPercent(value)))
                .build());
    }

    private static void buildDynamicCategory(ConfigBuilder builder, ConfigEntryBuilder entries) {
        ConfigCategory category = builder.getOrCreateCategory(
                Component.translatable("category.gammaplus.dynamic"));

        if (IrisCompat.areShadersActive()) {
            category.addEntry(entries.startTextDescription(
                    Component.translatable("text.gammaplus.dynamic.shader_note")
                            .withStyle(ChatFormatting.LIGHT_PURPLE)).build());
        }

        category.addEntry(entries.startBooleanToggle(
                        Component.translatable("option.gammaplus.dynamic_enabled"),
                        GammaModConfig.isDynamicLightingEnabled())
                .setDefaultValue(GammaModConfig.DEFAULT_DYNAMIC_ENABLED)
                .setTooltip(Component.translatable("option.gammaplus.dynamic_enabled.tooltip"))
                .setSaveConsumer(GammaModConfig::setDynamicLightingEnabled)
                .build());

        category.addEntry(entries.startIntSlider(
                        Component.translatable("option.gammaplus.dynamic_high"),
                        toPercent(GammaModConfig.getDynamicHighLevel()), 0, PERCENT_MAX)
                .setDefaultValue(toPercent(GammaModConfig.DEFAULT_DYNAMIC_HIGH))
                .setTextGetter(ClothConfigScreen::percentText)
                .setTooltip(Component.translatable("option.gammaplus.dynamic_high.tooltip"))
                .setSaveConsumer(value -> GammaModConfig.setDynamicHighLevel(fromPercent(value)))
                .build());

        category.addEntry(entries.startIntSlider(
                        Component.translatable("option.gammaplus.dynamic_normal"),
                        toPercent(GammaModConfig.getDynamicNormalLevel()), 0, PERCENT_MAX)
                .setDefaultValue(toPercent(GammaModConfig.DEFAULT_DYNAMIC_NORMAL))
                .setTextGetter(ClothConfigScreen::percentText)
                .setTooltip(Component.translatable("option.gammaplus.dynamic_normal.tooltip"))
                .setSaveConsumer(value -> GammaModConfig.setDynamicNormalLevel(fromPercent(value)))
                .build());

        category.addEntry(entries.startIntSlider(
                        Component.translatable("option.gammaplus.dynamic_rate"),
                        toTenths(GammaModConfig.getDynamicTransitionRate()),
                        RATE_TENTHS_MIN, RATE_TENTHS_MAX)
                .setDefaultValue(toTenths(GammaModConfig.DEFAULT_DYNAMIC_RATE))
                // Locale.ROOT: the default locale renders "2.0" as "٢٫٠" on an Arabic-locale
                // machine, which is not what a numeric setting should show.
                .setTextGetter(value -> Component.literal(String.format(Locale.ROOT, "%.1f", value / 10.0)))
                .setTooltip(Component.translatable("option.gammaplus.dynamic_rate.tooltip"))
                .setSaveConsumer(value -> GammaModConfig.setDynamicTransitionRate(value / 10.0))
                .build());

        category.addEntry(entries.startIntSlider(
                        Component.translatable("option.gammaplus.dynamic_cave"),
                        GammaModConfig.getDynamicCaveSkylightMax(), 0, 15)
                .setDefaultValue(GammaModConfig.DEFAULT_DYNAMIC_CAVE_SKYLIGHT_MAX)
                .setTooltip(Component.translatable("option.gammaplus.dynamic_cave.tooltip"))
                .setSaveConsumer(GammaModConfig::setDynamicCaveSkylightMax)
                .build());

        category.addEntry(entries.startIntSlider(
                        Component.translatable("option.gammaplus.dynamic_night"),
                        GammaModConfig.getDynamicNightDarknessMin(), 0, 4)
                .setDefaultValue(GammaModConfig.DEFAULT_DYNAMIC_NIGHT_DARKNESS_MIN)
                .setTooltip(Component.translatable("option.gammaplus.dynamic_night.tooltip"))
                .setSaveConsumer(GammaModConfig::setDynamicNightDarknessMin)
                .build());
    }

    private static int toPercent(double level) {
        return (int) Math.round(level * 100);
    }

    private static double fromPercent(int percent) {
        return percent / 100.0;
    }

    private static int toTenths(double rate) {
        return (int) Math.round(rate * 10);
    }

    private static Component percentText(int percent) {
        return Component.literal(percent + "%");
    }
}
