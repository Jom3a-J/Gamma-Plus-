# Gamma Plus ⚡
> Advanced Client-Side Brightness, Night Vision & Dynamic Lighting Utility for Minecraft 26.2 (Fabric / Java 25)

---

## 📖 Introduction
**Gamma Plus** is a premium client-side utility mod that gives players absolute control over screen visibility, ambient illumination, and shader compatibility. Whether exploring deep cave systems, building during pitch-black nights, or using heavy shader packs, Gamma Plus provides smooth, customizable illumination settings without modifying server state or requiring cheats.

By combining traditional brightness modifications with physical transition models and deep shader-pipeline integrations, Gamma Plus provides a beautiful, natural, and lag-free visual experience.

---

## ⚡ Key Features

### 1. Gamma Boost (Fullbright)
* **Adjustable Limit:** Go beyond the standard vanilla brightness limits, boosting screen gamma from **100% (Vanilla)** up to **1500% (Max Visibility)**.
* **Direct Toggle:** Instantly toggle fullbright on/off in-game using hotkeys.

### 2. Custom Night Vision
* **Adjustable Weight:** Fine-tune night vision intensity (from **0% to 100%**) via slider to preserve color balance instead of washing out the screen.
* **Client-Side Safety:** Uses a custom client-only potion bridge that doesn't trigger server updates.

### 3. Dynamic Lighting (Cave & Surface Night Adaptation)
* **Continuous Probing:** Measures surrounding light using a 5-block spatial average cross-probe (Center, North, South, East, West) at the player's eye level, preventing boundary collisions on slabs or path blocks.
* **Quadratic Darkness Curve:** Matches human visual perception (logarithmic light response) to smoothly blend brightness in intermediate zones like forest canopies, thunderstorms, and cave mouths.
* **Night-Cave Transition Dip Guard:** Intelligently balances night darkness and cave depth so that entering a cave at night maintains a consistent, full-strength illumination curve with zero visual flickering.

### 4. Critically Damped Spring Smoothing
* **No Sudden Jumps:** Brightness transitions are calculated using the exact closed-form analytical solution of a critically damped spring, ensuring perfectly smooth ease-in and ease-out curves:
  $$x(t) = x_{\text{target}} + (A + B \times t) \times e^{-\omega t}$$
* **Asymmetric Visual Adaptation:**
  * **Dark Adaptation (Bright → Dark):** Replicating human biology, spring speed scales down to $60\%$ when entering dark areas, allowing brightness to build up gradually over 4–5 seconds.
  * **Light Adaptation (Dark → Bright):** Scales up to $150\%$ when exiting caves, clearing brightness rapidly to prevent screen bleaching.
* **Adjustable Speed:** The natural frequency ($\omega$) of the spring is adjustable live via the **DL Speed** slider.

### 5. Iris Shaders Compatibility
* **Dynamic Shader Uniform Integration:** When shader packs are enabled, traditional lightmap modification has no effect. Gamma Plus hooks into `GameRenderer.nightVisionScale` using reflection to dynamically scale the `nightVision` uniform sent to the GPU.
* **Shader-Driven Transitions:** Both manual night vision and dynamic lighting scale smoothly under shaders, bringing cinematic transitions to high-end resource packs.

### 6. Allocation-Free Performance
* **Zero Garbage Collection Pressure:** Recycled `ThreadLocal` mutable block positions (`MutableBlockPos`) are cached and reused on the main rendering tick loop, making the environment light probe path completely allocation-free.
* **Throttled reflection:** Checks for active Iris shaders are throttled to once every 200ms (instead of every frame), eliminating CPU overhead.

---

## 🎮 Keybindings & Controls
Gamma Plus registers custom controls in the standard Minecraft keybind settings under the **Gamma Plus** category:

| Hotkey | Action | Default Key |
|:---:|---|:---:|
| **Toggle Gamma** | Turn fullbright (Gamma Boost) on or off | `G` |
| **Toggle Night Vision** | Turn custom-intensity night vision on or off | `N` |
| **Toggle Dynamic** | Turn ambient cave/night dynamic lighting on or off | `L` |

*Note: All keybindings can be fully customized in **Options > Controls > Key Binds**.*

---

## ⚙️ Configuration Options
With **Mod Menu** installed, access the configuration screen to adjust the following variables:

### Core Settings
* **Gamma Level (1.0 - 15.0):** The target multiplier for standard fullbright (defaults to `15.0`).
* **Night Vision Intensity (0.0 - 1.0):** The shader weight and brightness limit when night vision is active (defaults to `1.0`).

### Dynamic Lighting (DL) Settings
* **DL Speed (0.5 - 10.0):** Natural spring frequency ($\omega$). Higher values make light adjustments snappy; lower values create a cinematic fade (defaults to `3.0`).
* **DL Night Darkness Min (0.0 - 1.0):** Adjusts how dark the surface must be to trigger nighttime dynamic lighting.
* **DL Cave Skylight Max (0.0 - 15.0):** The sky light threshold where cave darkness begins mapping.
* **DL Low Light Level (0.0 - 1.0):** Target brightness in pitch-black conditions (defaults to `1.0`).
* **DL High Light Level (0.0 - 1.0):** Target brightness in fully lit daytime conditions (defaults to `0.0`).

---

## 📦 Mod Dependencies & Setup

### 1. Required Mods
* **Fabric API:** Place the **Fabric API** jar in your `.minecraft/mods/` folder. This is required to register keybindings and process game-tick events.
* *Note: Unlike many other mods, Gamma Plus **does NOT require Cloth Config** to run. The config screen is built using vanilla Minecraft UI widgets, keeping the mod lightweight and dependency-free.*

### 2. Highly Recommended Mods
* **Mod Menu:** Required if you want to access the settings screen in-game and adjust config sliders (speed, thresholds, intensity levels).


### 3. Setup Steps:
1. Place `GammaPlus-1.0.0.jar` and the latest **Fabric API** (and optionally **Mod Menu**) inside your `.minecraft/mods/` folder.
2. Launch your Minecraft client.

---

## 📄 License & Credits
* Licensed under the **MIT License**.
* Developed and optimized by **Jom3a**.
