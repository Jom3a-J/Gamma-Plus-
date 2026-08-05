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
* **Unified Light Measurement:** Instead of asking "am I in a cave?" and "is it night?" as two separate tests, a single reading answers both — the light *actually reaching you*, combining block light with sky light adjusted for the time of day. A cave reads dark because it has no sky access at any hour, which is the real distinction; a torch-lit cave correctly reads as lit.
* **Five-Point Cross Probe:** Samples the player's eye position and its four horizontal neighbours (North, South, East, West), taking the **brightest** of them. Light stored inside a solid block is zero, so taking the maximum stops standing beside a wall from registering as a cave.
* **Continuous Sunset:** Reads the floating-point sky-light attribute that vanilla rounds off to produce `getSkyDarken()`, so dusk ramps smoothly rather than stepping through eleven discrete levels.
* **Quadratic Darkness Curve:** Matches human visual perception (logarithmic light response) to smoothly blend brightness in intermediate zones like forest canopies, thunderstorms, and cave mouths.
* **Adjustable Torch Influence:** Placed light dims the boost proportionally rather than cancelling it outright — tunable all the way from *ignore torches entirely* to *a single torch switches the boost off*.

### 4. Critically Damped Spring Smoothing
* **No Sudden Jumps:** Brightness transitions are calculated using the exact closed-form analytical solution of a critically damped spring, ensuring perfectly smooth ease-in and ease-out curves:
  $$x(t) = x_{\text{target}} + (A + B \times t) \times e^{-\omega t}$$
* **Asymmetric Visual Adaptation:**
  * **Dark Adaptation (Bright → Dark):** Replicating human biology, spring speed scales down to $60\%$ when entering dark areas, allowing brightness to build up gradually over 4–5 seconds.
  * **Light Adaptation (Dark → Bright):** Scales up to $150\%$ when exiting caves, clearing brightness rapidly to prevent screen bleaching.
* **Adjustable Speed:** The natural frequency ($\omega$) of the spring is adjustable live via the **Transition Speed** slider.

### 5. Iris Shaders Compatibility
* **Dynamic Shader Uniform Integration:** When shader packs are enabled, traditional lightmap modification has no effect. Gamma Plus hooks into `GameRenderer.nightVisionScale` using reflection to dynamically scale the `nightVision` uniform sent to the GPU.
* **Shader-Driven Transitions:** Both manual night vision and dynamic lighting scale smoothly under shaders, bringing cinematic transitions to high-end resource packs.

### 6. Update Notifications
* **Quiet Heads-Up:** Checks Modrinth once per session and, shortly after you join a world or server, mentions in chat if a newer version exists — with a clickable link. Waits a moment after joining so it lands after the server's own welcome messages instead of being scrolled away.
* **Never In The Way:** The request runs on a background thread with hard timeouts, and every failure path — offline, rate-limited, malformed response — simply shows nothing. It can neither stall a frame nor produce an error.
* **Nothing About You:** The request carries only a User-Agent naming the mod and its version. No player, world, or server information is sent. Turn it off entirely with **Check for Updates** in the settings.

### 7. Allocation-Free Performance
* **Zero Garbage Collection Pressure:** Recycled `ThreadLocal` mutable block positions (`MutableBlockPos`) are cached and reused on the main rendering tick loop, making the environment light probe path completely allocation-free.
* **Throttled reflection:** Checks for active Iris shaders are throttled to once every 200ms (instead of every frame), and the result is shared by every call site rather than cached per-caller.
* **Zero Idle Cost:** The lightmap is only rebuilt and re-uploaded while a feature is actually driving it, so an install with every toggle switched off costs nothing per frame.
* **Off-Thread Config Writes:** Hotkey toggles persist on a background thread and coalesce, so rapid switching never stalls a frame on disk I/O.

---

## 🎮 Keybindings & Controls
Gamma Plus registers custom controls in the standard Minecraft keybind settings under the **Gamma Plus** category:

| Hotkey | Action | Default Key |
|:---:|---|:---:|
| **Toggle Gamma** | Turn fullbright (Gamma Boost) on or off | `G` |
| **Toggle Night Vision** | Turn custom-intensity night vision on or off | `N` |
| **Toggle Dynamic** | Turn ambient cave/night dynamic lighting on or off | `L` |
| **Open Settings** | Open the Gamma Plus settings screen without leaving the game | `K` |

*Note: All keybindings can be fully customized in **Options > Controls > Key Binds**.*

---

## ⚙️ Configuration Options
Press **`K`** in-game — or use **Mod Menu** — to open the settings screen and adjust the following:

### Core Settings
* **Gamma Level (0% - 1500%):** The target multiplier for standard fullbright (defaults to `1500%`).
* **Night Vision Intensity (10% - 100%):** The shader weight and brightness limit when night vision is active (defaults to `100%`). Floored at 10% so an enabled effect is always visibly doing something.

### Dynamic Lighting (DL) Settings
* **Dark Level (0% - 1500%):** Target brightness in pitch-black conditions (defaults to `1500%`).
* **Bright Level (0% - 1500%):** Target brightness in fully lit conditions (defaults to `0%`, leaving vanilla brightness untouched).
* **Transition Speed (0.5 - 10.0):** Natural spring frequency ($\omega$). Higher values make light adjustments snappy; lower values create a cinematic fade (defaults to `2.0`).
* **Full Boost Below Light (0 - 14):** Light level at or below which the boost reaches full strength (defaults to `4`). An unlit cave sits at `0`; open ground at midnight sits at about `4`.
* **No Boost Above Light (1 - 15):** Light level at or above which no boost is applied (defaults to `12`). Midday is `15`.
* **Torch Influence (0% - 100%):** How much torches and other placed light count toward "it is bright here" (defaults to `50%`). At `0%` placed light is ignored entirely and only sky access and time of day matter; at `100%` a single torch cancels the boost outright.

### General
* **Check for Updates:** Whether to ask Modrinth once per session about newer versions (defaults to `on`). Turning it off stops the mod making any outbound network request at all.

Settings are stored in `config/gammaplus.json`. Configs written by an earlier build are migrated automatically on first load, so upgrading never resets your setup.

> **Tip:** At the defaults, an unlit cave and open ground at midnight both receive the full boost. To make caves brighter than open night, lower **Full Boost Below Light** to `0` — a cave stays at 100% while midnight drops to roughly 44%.

---

## 📦 Mod Dependencies & Setup

### 1. Required Mods
* **Fabric API:** Place the **Fabric API** jar in your `.minecraft/mods/` folder. This is required to register keybindings and process game-tick events.
* *Note: Unlike many other mods, Gamma Plus **does NOT require Cloth Config** to run. The settings screen is built on vanilla's own options framework, keeping the mod lightweight and dependency-free.*

### 2. Optional Mods
* **Cloth Config API:** If installed, Gamma Plus automatically uses a richer tabbed settings screen with per-setting reset arrows and an explicit save/cancel. If absent, the built-in screen is used instead — nothing is lost.
* **Mod Menu:** Adds a config button beside Gamma Plus in the mod list. Entirely optional, since the **`K`** hotkey opens the same screen in-game.


### 3. Setup Steps:
1. Place `GammaPlus-1.0.0.jar` and the latest **Fabric API** (and optionally **Cloth Config** and **Mod Menu**) inside your `.minecraft/mods/` folder.
2. Launch your Minecraft client.
3. Press **`K`** in-game to open the settings, or use `G` / `N` / `L` to toggle features directly.

---

## 📄 License & Credits
* Licensed under the **MIT License**.
* Developed and optimized by **Jom3a**.
