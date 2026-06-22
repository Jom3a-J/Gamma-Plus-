package com.gammaplus.config;

import com.gammaplus.compat.IrisCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

/**
 * Gamma PlusConfigScreen — In-game settings screen with sliders and toggles.
 * Accessible via Mod Menu or can be opened programmatically.
 */
public class GammaModConfigScreen extends Screen {

    private final Screen parent;

    // Widget references for dynamic updates (sliders only, toggles aren't accessed dynamically)
    private GammaSliderWidget gammaSlider;
    private NVIntensitySliderWidget nvSlider;
    private DynamicHighSliderWidget dynamicHighSlider;
    private DynamicRateSliderWidget dynamicRateSlider;

    // Temporary config values (applied on save)
    private boolean tempGammaEnabled;
    private double tempGammaLevel;
    private boolean tempNVEnabled;
    private double tempNVIntensity;
    private boolean tempDynamicEnabled;
    private double tempDynamicHigh;
    private double tempDynamicRate;

    // Original values to restore if Cancel/Esc is pressed
    private final boolean originalGammaEnabled;
    private final double originalGammaLevel;
    private final boolean originalNVEnabled;
    private final double originalNVIntensity;
    private final boolean originalDynamicEnabled;
    private final double originalDynamicHigh;
    private final double originalDynamicRate;

    private boolean saved = false;

    public GammaModConfigScreen(Screen parent) {
        super(Component.literal("Gamma Plus Settings"));
        this.parent = parent;

        // Copy current config to temp values and original values
        this.tempGammaEnabled = GammaModConfig.isGammaEnabled();
        this.tempGammaLevel = GammaModConfig.getGammaLevel();
        this.tempNVEnabled = GammaModConfig.isNightVisionEnabled();
        this.tempNVIntensity = GammaModConfig.getNightVisionIntensity();

        this.tempDynamicEnabled = GammaModConfig.isDynamicLightingEnabled();
        this.tempDynamicHigh = GammaModConfig.getDynamicHighLevel();
        this.tempDynamicRate = GammaModConfig.getDynamicTransitionRate();

        this.originalGammaEnabled = this.tempGammaEnabled;
        this.originalGammaLevel = this.tempGammaLevel;
        this.originalNVEnabled = this.tempNVEnabled;
        this.originalNVIntensity = this.tempNVIntensity;
        this.originalDynamicEnabled = this.tempDynamicEnabled;
        this.originalDynamicHigh = this.tempDynamicHigh;
        this.originalDynamicRate = this.tempDynamicRate;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int startY = this.height / 4;
        int buttonWidth = 200;
        int buttonHeight = 20;
        int spacing = 26;

        // === GAMMA SECTION ===
        addToggleButton(
                centerX - buttonWidth / 2, startY, buttonWidth, buttonHeight,
                this::getGammaToggleText,
                () -> tempGammaEnabled,
                val -> {
                    tempGammaEnabled = val;
                    GammaModConfig.setGammaEnabled(val);
                }
        );

        // Gamma level slider (100% to 1500%)
        gammaSlider = this.addRenderableWidget(new GammaSliderWidget(
                centerX - buttonWidth / 2, startY + spacing,
                buttonWidth, buttonHeight,
                tempGammaLevel
        ));

        // === NIGHT VISION SECTION ===
        addToggleButton(
                centerX - buttonWidth / 2, startY + spacing * 3, buttonWidth, buttonHeight,
                this::getNVToggleText,
                () -> tempNVEnabled,
                val -> {
                    tempNVEnabled = val;
                    GammaModConfig.setNightVisionEnabled(val);
                }
        );

        // Night vision intensity slider (0% to 100%)
        nvSlider = this.addRenderableWidget(new NVIntensitySliderWidget(
                centerX - buttonWidth / 2, startY + spacing * 4,
                buttonWidth, buttonHeight,
                tempNVIntensity
        ));

        // === DYNAMIC LIGHTING SECTION ===
        addToggleButton(
                centerX - buttonWidth / 2, startY + spacing * 6, buttonWidth, buttonHeight,
                this::getDynamicToggleText,
                () -> tempDynamicEnabled,
                val -> {
                    tempDynamicEnabled = val;
                    GammaModConfig.setDynamicLightingEnabled(val);
                }
        );

        // Dynamic Lighting: High Level slider (0%–100%)
        dynamicHighSlider = this.addRenderableWidget(new DynamicHighSliderWidget(
                centerX - buttonWidth / 2, startY + spacing * 7,
                buttonWidth, buttonHeight,
                tempDynamicHigh
        ));

        // Dynamic Lighting: Transition Speed slider
        dynamicRateSlider = this.addRenderableWidget(new DynamicRateSliderWidget(
                centerX - buttonWidth / 2, startY + spacing * 8,
                buttonWidth, buttonHeight,
                tempDynamicRate
        ));

        // === ACTION BUTTONS ===
        addActionButton(centerX - 100, startY + spacing * 10, 95, buttonHeight,
                Component.literal("Save & Close").withStyle(ChatFormatting.GREEN),
                this::saveAndClose);

        addActionButton(centerX + 5, startY + spacing * 10, 95, buttonHeight,
                Component.literal("Cancel").withStyle(ChatFormatting.RED),
                this::onClose);
    }

    private Button addToggleButton(int x, int y, int width, int height,
                                    java.util.function.Supplier<Component> textSupplier,
                                    java.util.function.BooleanSupplier valueGetter,
                                    java.util.function.Consumer<Boolean> valueSetter) {
        Button button = Button.builder(
                textSupplier.get(),
                btn -> {
                    boolean newValue = !valueGetter.getAsBoolean();
                    valueSetter.accept(newValue);
                    btn.setMessage(textSupplier.get());
                }
        ).bounds(x, y, width, height).build();
        return this.addRenderableWidget(button);
    }

    private Button addActionButton(int x, int y, int width, int height, Component text, Runnable action) {
        Button button = Button.builder(text, btn -> action.run())
                .bounds(x, y, width, height).build();
        return this.addRenderableWidget(button);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int centerX = this.width / 2;
        int startY = this.height / 4;
        int spacing = 26;

        int x1 = centerX - 120;
        int x2 = centerX + 120;
        int y1 = startY - 40;
        int y2 = startY + 290;

        // Draw modern dark panel background
        graphics.fill(x1, y1, x2, y2, 0xD5111115);

        // Draw elegant thin border
        int borderColor = 0x33FFFFFF;
        graphics.fill(x1, y1, x2, y1 + 1, borderColor); // Top
        graphics.fill(x1, y2 - 1, x2, y2, borderColor); // Bottom
        graphics.fill(x1, y1, x1 + 1, y2, borderColor); // Left
        graphics.fill(x2 - 1, y1, x2, y2, borderColor); // Right

        // Header Divider
        graphics.fill(centerX - 100, startY - 18, centerX + 100, startY - 17, 0x44FFFFFF);

        // Bottom Divider
        graphics.fill(centerX - 100, startY + 251, centerX + 100, startY + 252, 0x22FFFFFF);

        // Title
        graphics.centeredText(this.font,
                Component.literal("⚡ Gamma Plus Settings").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                centerX, startY - 32, 0xFFFFFFFF);

        // Section headers
        graphics.centeredText(this.font,
                Component.literal("Gamma Boost").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD),
                centerX, startY - 12, 0xFFFFFFFF);

        graphics.centeredText(this.font,
                Component.literal("Night Vision").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD),
                centerX, startY + spacing * 3 - 12, 0xFFFFFFFF);

        // Iris shader warning
        if (IrisCompat.areShadersActive()) {
            graphics.centeredText(this.font,
                    Component.literal("⚠ Gamma inactive with active shaders")
                            .withStyle(ChatFormatting.GOLD),
                    centerX, startY + spacing * 2 + 4, 0xFFFFAA00);
        }

        // Iris compatibility note for NV
        if (IrisCompat.isIrisInstalled()) {
            graphics.centeredText(this.font,
                    Component.literal("✓ Shaders supported for Night Vision")
                            .withStyle(ChatFormatting.GREEN),
                    centerX, startY + spacing * 5 + 4, 0xFF55FF55);
        }

        // Dynamic Lighting section header
        graphics.centeredText(this.font,
                Component.literal("Dynamic Lighting").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD),
                centerX, startY + spacing * 6 - 12, 0xFFFFFFFF);

        // Dynamic Lighting shader note
        if (IrisCompat.areShadersActive() && tempDynamicEnabled) {
            graphics.centeredText(this.font,
                    Component.literal("↳ driving Night Vision under shaders")
                            .withStyle(ChatFormatting.LIGHT_PURPLE),
                    centerX, startY + spacing * 9 + 4, 0xFFFF55FF);
        }

        graphics.nextStratum();
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private void saveAndClose() {
        saved = true;
        // The sliders apply live via applyValue(), so the temp fields already hold the
        // latest values — we read them from the slider here for parity and then persist.
        GammaModConfig.setGammaEnabled(tempGammaEnabled);
        GammaModConfig.setGammaLevel(gammaSlider.getLevelValue());
        GammaModConfig.setNightVisionEnabled(tempNVEnabled);
        GammaModConfig.setNightVisionIntensity(nvSlider.getIntensityValue());
        GammaModConfig.setDynamicLightingEnabled(tempDynamicEnabled);
        GammaModConfig.setDynamicHighLevel(dynamicHighSlider.getHighValue());
        GammaModConfig.setDynamicTransitionRate(dynamicRateSlider.getRateValue());
        GammaModConfig.save();
        onClose();
    }

    @Override
    public void onClose() {
        if (!saved) {
            // Revert changes on cancel/escape
            GammaModConfig.setGammaEnabled(originalGammaEnabled);
            GammaModConfig.setGammaLevel(originalGammaLevel);
            GammaModConfig.setNightVisionEnabled(originalNVEnabled);
            GammaModConfig.setNightVisionIntensity(originalNVIntensity);
            GammaModConfig.setDynamicLightingEnabled(originalDynamicEnabled);
            GammaModConfig.setDynamicHighLevel(originalDynamicHigh);
            GammaModConfig.setDynamicTransitionRate(originalDynamicRate);
        }
        Minecraft.getInstance().gui.setScreen(parent);
    }

    // === Helper methods for button text ===

    private Component getGammaToggleText() {
        return Component.literal("Gamma Boost: ").withStyle(ChatFormatting.GRAY)
                .append(tempGammaEnabled
                        ? Component.literal("ENABLED").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                        : Component.literal("DISABLED").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
    }

    private Component getNVToggleText() {
        return Component.literal("Night Vision: ").withStyle(ChatFormatting.GRAY)
                .append(tempNVEnabled
                        ? Component.literal("ENABLED").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                        : Component.literal("DISABLED").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
    }

    private Component getDynamicToggleText() {
        return Component.literal("Dynamic Lighting: ").withStyle(ChatFormatting.GRAY)
                .append(tempDynamicEnabled
                        ? Component.literal("ENABLED").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                        : Component.literal("DISABLED").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
    }

    // =========================================================================
    //  Custom Slider Widgets
    // =========================================================================

    /**
     * Gamma level slider — maps the slider's 0.0–1.0 travel to the 0.0–15.0 level range,
     * shown to the user as 0%–1500%. Above 100% (= 1.0) the value over-brightens past the
     * lightmap shader's clamp, since the shader applies the mix after clamping.
     */
    private class GammaSliderWidget extends AbstractSliderButton {
        public GammaSliderWidget(int x, int y, int width, int height, double initialLevel) {
            super(x, y, width, height, Component.empty(), toSlider(initialLevel, GammaModConfig.getGammaMax()));
            updateMessage();
        }

        /** Convert a level (0.0–max) to slider position (0.0–1.0). */
        private static double toSlider(double level, double max) {
            return Math.max(0.0, Math.min(1.0, level / max));
        }

        /** Convert slider position (0.0–1.0) to level (0.0–max). */
        private double fromSlider() {
            return this.value * GammaModConfig.getGammaMax();
        }

        /** Get the gamma level value (0.0 to max, i.e. 0.0–15.0) */
        public double getLevelValue() {
            return fromSlider();
        }

        @Override
        protected void updateMessage() {
            int percent = (int)(getLevelValue() * 100);
            this.setMessage(Component.literal("Gamma Level: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(percent + "%").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)));
        }

        @Override
        protected void applyValue() {
            tempGammaLevel = getLevelValue();
            GammaModConfig.setGammaLevel(tempGammaLevel);
        }
    }

    /**
     * Night Vision intensity slider — maps the slider's 0.0–1.0 travel to the
     * NV_FLOOR–1.0 intensity range (so "enabled" always means visibly active).
     */
    private class NVIntensitySliderWidget extends AbstractSliderButton {
        private final double floor = GammaModConfig.getNightVisionFloor();

        public NVIntensitySliderWidget(int x, int y, int width, int height, double initialIntensity) {
            super(x, y, width, height, Component.empty(), toSlider(initialIntensity, GammaModConfig.getNightVisionFloor()));
            updateMessage();
        }

        /** Convert an intensity value (floor–1.0) to slider position (0.0–1.0). */
        private static double toSlider(double intensity, double floor) {
            return (intensity - floor) / (1.0 - floor);
        }

        /** Convert slider position (0.0–1.0) to intensity value (floor–1.0). */
        private double fromSlider() {
            return floor + this.value * (1.0 - floor);
        }

        /** Get the NV intensity value (floor to 1.0) */
        public double getIntensityValue() {
            return fromSlider();
        }

        @Override
        protected void updateMessage() {
            int percent = (int)(getIntensityValue() * 100);
            this.setMessage(Component.literal("NV Intensity: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(percent + "%").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)));
        }

        @Override
        protected void applyValue() {
            tempNVIntensity = getIntensityValue();
            GammaModConfig.setNightVisionIntensity(tempNVIntensity);
        }
    }

    /**
     * Dynamic Lighting "High Level" slider — brightness reached in caves/night.
     * Maps the slider's 0.0–1.0 travel to dynamicHighLevel 0.0–15.0, shown as 0%–1500%,
     * matching the manual gamma slider's range.
     */
    private class DynamicHighSliderWidget extends AbstractSliderButton {
        public DynamicHighSliderWidget(int x, int y, int width, int height, double initialHigh) {
            super(x, y, width, height, Component.empty(), toSlider(initialHigh, GammaModConfig.getGammaMax()));
            updateMessage();
        }

        private static double toSlider(double level, double max) {
            return Math.max(0.0, Math.min(1.0, level / max));
        }

        private double fromSlider() {
            return this.value * GammaModConfig.getGammaMax();
        }

        public double getHighValue() {
            return fromSlider();
        }

        @Override
        protected void updateMessage() {
            int percent = (int)(getHighValue() * 100);
            this.setMessage(Component.literal("DL High Level: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(percent + "%").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)));
        }

        @Override
        protected void applyValue() {
            tempDynamicHigh = getHighValue();
            GammaModConfig.setDynamicHighLevel(tempDynamicHigh);
        }
    }

    /**
     * Dynamic Lighting "Transition Speed" slider — maps slider 0.0–1.0 to rate 0.5–10.0.
     */
    private class DynamicRateSliderWidget extends AbstractSliderButton {
        private static final double RATE_MIN = 0.5;
        private static final double RATE_MAX = 10.0;

        public DynamicRateSliderWidget(int x, int y, int width, int height, double initialRate) {
            super(x, y, width, height, Component.empty(), toSlider(initialRate));
            updateMessage();
        }

        private static double toSlider(double rate) {
            return (Math.max(RATE_MIN, Math.min(RATE_MAX, rate)) - RATE_MIN) / (RATE_MAX - RATE_MIN);
        }

        private double fromSlider() {
            return RATE_MIN + this.value * (RATE_MAX - RATE_MIN);
        }

        public double getRateValue() {
            return fromSlider();
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.literal("DL Speed: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(String.format("%.1f", fromSlider())).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)));
        }

        @Override
        protected void applyValue() {
            tempDynamicRate = getRateValue();
            GammaModConfig.setDynamicTransitionRate(tempDynamicRate);
        }
    }
}
