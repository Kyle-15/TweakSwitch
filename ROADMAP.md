# TweakSwitch roadmap

Ideas for future versions, roughly in the order they'd help most. Tick them off as they ship.

## Next up

- [ ] **Sharper previews.** Hover previews look pixelated because:
  - pictures are shrunk in whole steps (a 957 px preview drops to 319 px, animations to 240 px) and then stretched back up to ~300+ screen pixels;
  - the textures use Minecraft's "nearest pixel" scaling, so stretching makes them blocky;
  - Modrinth search only gives 350 px thumbnails.

  Fix: decode each picture to the size it's actually shown at (panel width × GUI scale) with smooth resizing, use smooth texture filtering, and fetch Modrinth's full-size gallery images. Maybe also a bigger preview while holding Shift.
- [ ] **Smaller UI.** Everything feels a bit too big. Current sizes (in GUI pixels) and targets:

  | Part | Now | Target |
  |---|---|---|
  | Bottom-bar buttons | 20 tall | 16 |
  | Switch cards (home) | 30 tall, 22 px icon | 22, 16 px icon, description in tooltip only |
  | Modrinth cards | 46 tall, 34 px icon | 34, 24 px icon, tags on the same line as downloads |
  | Vanilla Tweaks tiles | 66 × 62, 28 px icon | 52 × 48, 20 px icon, one-line names |
  | Plain list rows | 25 tall | 20 |
  | Header (title + search) | 40 tall | 32, title and search side by side |

  Also a **UI size** option (Compact / Normal) so either look stays available.
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

- [ ] **Sort and pin switches.** Sort by name, recently added or "on first", and pin favorite packs to the top.
- [ ] **Export / import switches.** Save your pack list and switch states to a file to share with a friend or restore later.

## Reliability

- [ ] **Offline and error handling.** Clear messages and a Retry button when there's no internet or a site is down, instead of a raw error.
- [ ] **Cache lists and icons on disk.** Keep the Vanilla Tweaks pack list, icons and previews for a day so screens open instantly and make fewer requests.
- [ ] **Back off when rate-limited.** Slow down and retry if Modrinth answers "too many requests" (HTTP 429).
- [ ] **Survive broken packs.** If a downloaded zip is damaged, show it as broken on its card (with a re-download button) instead of silently skipping it.
- [ ] **Keyboard and narrator support** for the custom cards, tiles and switches (Tab to move, Enter to toggle, screen reader text).

## Releases and testing

- [ ] **Automatic builds on GitHub.** A GitHub Actions workflow that builds the mod and runs the tests on every push, and attaches the jar to releases.
- [ ] **Unit tests** for the Vanilla Tweaks and Modrinth parsers, so site changes are caught early.
- [ ] **Check for TweakSwitch updates** once it's on Modrinth, and show a small "new version available" note.
- [ ] **Keep the Modrinth page current:** new gallery screenshots whenever the look changes.

## Bigger projects

- [ ] **More Minecraft versions.** 26.1 and 26.2 need only a handful of renamed methods (measured), one jar per version. 1.21.x is a much bigger port.
- [ ] **Vanilla Tweaks data packs and crafting tweaks.** Per-world gameplay tweaks, installed into each world's folder.
- [ ] **Translations** into other languages.

## Done

- [x] On/off switches, categories, Mod Menu (1.0)
- [x] Vanilla Tweaks and Modrinth downloaders, previews, mod warnings, updates (1.0)
- [x] New themed look (1.1.0)
- [x] Works with Fabric Loader 0.19.5 (1.1.1)
