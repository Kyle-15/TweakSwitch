# TweakSwitch roadmap

Ideas for future versions, roughly in the order they'd help most. Tick them off as they ship.

## Next up

- [ ] **Sharper previews.** Hover previews look pixelated because:
  - pictures are shrunk in whole steps (a 957 px preview drops to 319 px, animations to 240 px) and then stretched back up to ~300+ screen pixels;
  - the textures use Minecraft's "nearest pixel" scaling, so stretching makes them blocky;
  - Modrinth search only gives 350 px thumbnails.

  Fix: decode each picture to the size it's actually shown at (panel width × GUI scale) with smooth resizing, use smooth texture filtering, and fetch Modrinth's full-size gallery images. Maybe also a bigger preview while holding Shift.
- [ ] **Pack order.** Choose which pack wins when two change the same thing, with up/down arrows or drag-to-reorder on the switch cards.
- [ ] **Conflict warnings.** Warn when switching on two Vanilla Tweaks packs that clash (e.g. two crosshairs) and offer to turn the other one off.
- [ ] **Vanilla Tweaks credit.** Keep "Vanilla Tweaks · vanillatweaks.net" in downloaded packs' descriptions (the mod currently replaces it), and ask Vanilla Tweaks for permission for the downloader.

## Features

- [ ] **Presets.** Save your switches as named sets ("PvP", "Building") and swap between them in one click, optionally with a keybind.
- [ ] **Split combined Vanilla Tweaks zips.** Read the pack list inside a zip from their website and download each tweak as its own switch.
- [ ] **Update reminder.** A small popup at startup when packs have updates.
- [ ] **Delete packs from the screen.** Right-click a card to remove a pack.
- [ ] **Modrinth filters.** Filter by category (GUI, Blocks, 16x…) and hide packs that need mods.
- [ ] **Settings page.** Previews on/off, panel size, theme and keybind in one place, opened from Mod Menu.
- [ ] **Previews on your switches.** Hover a switch to see the same pictures as in the picker.

## Bigger projects

- [ ] **More Minecraft versions.** 26.1 and 26.2 need only a handful of renamed methods (measured), one jar per version. 1.21.x is a much bigger port.
- [ ] **Vanilla Tweaks data packs and crafting tweaks.** Per-world gameplay tweaks, installed into each world's folder.
- [ ] **Translations** into other languages.

## Done

- [x] On/off switches, categories, Mod Menu (1.0)
- [x] Vanilla Tweaks and Modrinth downloaders, previews, mod warnings, updates (1.0)
- [x] New themed look (1.1.0)
- [x] Works with Fabric Loader 0.19.5 (1.1.1)
