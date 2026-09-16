# Gamma Plus
> Advanced client-side brightness, night vision, and dynamic lighting for Fabric Minecraft 26.3 (Java 25).

---

## Introduction
Gamma Plus is a client-side utility mod that gives players absolute control over screen visibility, ambient illumination, and shader compatibility. Whether exploring dark caves, building at night, or using shader packs, Gamma Plus provides smooth, customizable illumination settings without modifying server state or requiring cheats.

---

## Key Features

* **Gamma Boost (Fullbright):** Boost screen brightness from 100% (Vanilla) up to 1500% (Max Visibility) with a single hotkey toggle.
* **Custom Night Vision:** Adjust night vision shader weight (0% to 100%) to preserve color balance instead of washing out the screen.
* **Dynamic Lighting Adaptation:** Measures the light actually reaching the player — block light plus sky light adjusted for the time of day — sampled across five points at eye level. A quadratic darkness curve keeps mild shade from triggering a boost while ramping smoothly into real darkness, adapting to caves, nightfall, thunderstorms and cave mouths alike.
* **Critically Damped Spring Transitions:** Smooths all light transitions using a physical critically damped spring model. Adaptation is biological: it scales down to 60% speed when adjusting to the dark (4-5 seconds transition) and scales up to 150% speed when stepping into bright areas to prevent blinding.
* **Iris Shaders Compatibility:** Detects when shader packs are active and dynamically scales the GPU's `nightVision` shader uniform, making dynamic lighting and custom night vision work flawlessly under shaders.
* **Allocation-Free Performance:** Caches and reuses reusable block positions in the rendering loop, causing zero garbage collection pressure or lag spikes. The lightmap is only rebuilt while a feature is actually driving it, so an idle install costs nothing.

---

## Controls and Keybindings
Gamma Plus registers standard keybindings in Minecraft's options menu:

* **Toggle Gamma:** Press **G** to turn fullbright on or off.
* **Toggle Night Vision:** Press **N** to turn custom-intensity night vision on or off.
* **Toggle Dynamic Lighting:** Press **L** to turn dynamic ambient adaptation on or off.
* **Open Settings:** Press **K** to open the settings screen without leaving the game.

*Note: Keybinds can be reconfigured in Options > Controls > Key Binds.*

---

## Configuration Settings
With **Mod Menu** installed, open the Gamma Plus screen to configure the following. Settings
are stored in `config/gammaplus.json`, and a config from the older `lumencraft.json` filename
is migrated automatically on first load.

**Gamma Boost tab**
* **Enable Gamma Boost:** Master toggle (also the G key).
* **Gamma Level:** Fullbright multiplier, 0% to 1500%.

**Night Vision tab**
* **Enable Night Vision:** Master toggle (also the N key).
* **Night Vision Intensity:** Night vision shader weight, 10% to 100%.

**Dynamic Lighting tab**
* **Enable Dynamic Lighting:** Master toggle (also the L key).
* **Dark Level:** Target brightness in pitch-black conditions (0% to 1500%).
* **Bright Level:** Baseline brightness in full daylight (0% to 1500%).
* **Transition Speed:** Spring speed, 0.5 to 10.0. Higher values are snappier.
* **Full Boost Below Light:** Light level at or below which the boost reaches full strength. An unlit cave is 0; open ground at midnight is about 4.
* **No Boost Above Light:** Light level at or above which no boost is applied. Midday is 15.
* **Torch Influence:** How much torches and other placed light reduce the boost. 0% ignores them entirely, so only sky access and time of day matter; 100% counts them in full, which makes a single torch cancel the boost outright.

---

## Dependencies and Setup

### Mod Dependencies
* **Fabric API:** Required. Place the Fabric API jar in your mods folder.
* **Mod Menu:** Recommended. Provides the in-game entry point to the settings screen. Without it the keybinds and config file still work.
* **Cloth Config API:** Optional. When installed, Gamma Plus uses a richer tabbed settings screen with per-setting reset arrows and explicit save/cancel. When absent, the built-in screen is used instead — it is written against vanilla's own options framework, so no extra mod is needed.

### Installation Steps
1. Download the latest release from [Modrinth](https://modrinth.com/mod/gamma-plus) and place it, with the latest **Fabric API**, in your `.minecraft/mods/` folder.
2. Launch your Minecraft client.

---

## License and Credits
* Licensed under the **MIT License**.
* Developed and optimized by **Jom3a**.
