# Changelog

## 1.2.0

Updated for **Minecraft 26.3**. Every feature, setting, and keybind works exactly as it did on
26.2 — your existing `config/gammaplus.json` carries over untouched, so there is nothing to
reconfigure.

> **Note:** This build requires Minecraft 26.3. Stay on 1.1.0 if you are still playing 26.2.

### Changed

- **Updated to Minecraft 26.3.** Rebuilt against the 26.3 API; the lightmap and night-vision hooks
  the mod relies on were unchanged in this release, so brightness, Night Vision, and Dynamic
  Lighting behave identically.
- **Keybinds rewritten onto Minecraft's own key codes.** 26.3 replaces GLFW with SDL for windowing
  and input, so the `org.lwjgl.glfw.GLFW` constants the keybinds were registered with no longer
  exist. They now use Minecraft's own `InputConstants` codes, which are independent of whichever
  windowing backend the game ships. **G**, **N**, **L**, and **K** are unchanged, and any keys you
  rebound yourself are preserved.

### Internal

- Dependencies moved to their 26.3 builds: Fabric Loader 0.19.5, Fabric API 0.160.6, Cloth Config
  26.3.158, and Mod Menu 21.0.0-beta.1.
- Build toolchain updated to Fabric Loom 1.18.2, which requires Gradle 9.7 — the wrapper moves from
  Gradle 9.5.1 to 9.7.1.
- The full 46-test suite passes unchanged against 26.3.

### Requirements

Minecraft 26.3 · Fabric Loader 0.19.2+ · Fabric API · Java 25
Cloth Config and Mod Menu are optional.

## 1.1.0

A large correctness pass over Dynamic Lighting, a rebuilt settings screen, and a much smaller
download. Your existing settings carry over automatically — nothing to reconfigure.

### Added

- **Torch Influence** — placed light now dims the brightness boost instead of being ignored, so a
  lit-up base no longer gets full fullbright. Adjustable from *ignore torches entirely* (0%) to
  *a single torch switches the boost off* (100%), defaulting to 50%.
- **Settings hotkey** — press **K** in-game to open the settings screen directly. Mod Menu is no
  longer needed to reach it.
- **Update notifications** — a quiet chat message shortly after you join a world when a newer
  version is available, with a clickable link. Runs in the background, shows nothing if anything
  goes wrong, and sends no information about you, your world, or your server. Turn it off with
  **Check for Updates**.
- **Cloth Config support** — if Cloth Config happens to be installed, a richer tabbed settings
  screen with per-setting reset arrows is used automatically. It is *not* required; without it the
  built-in screen is used.
- **Four settings that had no UI** are now adjustable: Bright Level, Full Boost Below Light,
  No Boost Above Light, and the thresholds that replaced the old cave/night knobs.

### Changed

- **Dynamic Lighting now measures the light actually reaching you.** Previously it asked "is there
  sky above me?" and "is it night?" as two separate tests. It now takes a single reading combining
  torch light with sky light adjusted for the time of day. A cave is still dark because it has no
  sky access at any hour — but a torch-lit cave is correctly treated as lit.
- **Sunset is now a smooth ramp** rather than eleven visible steps.
- **The settings screen has been rebuilt** on Minecraft's own options framework. It scrolls, so it
  can no longer overflow the screen, and it supports tooltips, controller navigation, and the
  narrator.
- **The download is about 78% smaller** — 291 KB down to 64 KB — mostly by shipping the icon at a
  sensible resolution instead of 512×512.
- **The config file is now `gammaplus.json`** (previously `lumencraft.json`). An existing file is
  renamed automatically on first launch, so your settings survive the upgrade.

### Fixed

- Keybinds and their category showed raw text such as `key.lumencraft.toggle_gamma` in
  Options → Controls instead of readable names.
- Night vision obtained from a potion, a beacon, or `/effect` could be **deleted** by the mod, or
  have its brightness driven from the mod's own slider. The mod now only ever touches the effect it
  applied itself.
- A real night vision potion at full strength was dimmed down to the mod's configured intensity.
- The night vision brightness could be pushed far past its valid range when Dynamic Lighting was
  also enabled, badly overexposing the screen.
- **Save** and **Cancel** on the settings screen were cut off or entirely off-screen at GUI scale 3
  and 4 — meaning the screen could only be exited with Escape, discarding changes.
- The lightmap was rebuilt and re-uploaded **every frame even with every feature switched off**, so
  the mod cost performance simply by being installed.
- A torch-lit underground area received the maximum brightness boost, as if it were pitch dark.
- Standing next to a wall could register as being in a cave, because light stored inside solid
  blocks counted toward the average.
- Night detection reached full strength moments after sunset and stayed pinned until sunrise, so
  dusk was effectively a step change and midnight was indistinguishable from twilight.
- **Transition Speed** displayed its value in non-Latin digits on systems with a matching language
  setting.
- Toggling a feature with a hotkey wrote the config file on the render thread, which could cost a
  frame.

### Internal

- Added a unit test suite (46 tests) covering the transition smoother, the darkness model, and the
  config migration.
- Iris shader detection is now cached in one place and shared, instead of being re-checked by each
  caller.

### Requirements

Minecraft 26.2 · Fabric Loader 0.19.0+ · Fabric API · Java 25
Cloth Config and Mod Menu are optional.
