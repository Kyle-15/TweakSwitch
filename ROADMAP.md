# TweakSwitch roadmap

Ideas for future versions, roughly in the order they'd help most. Tick them off as they ship.

## Next up

- [ ] **Minecraft 1.21.1 to 1.21.8.** Older still: the screen and drawing code changed again before 1.21.9.
- [ ] **26.4** once it's released: add `versions/26.4.properties` and, if needed, a `src/compat/26.4/` file.
- [ ] **Data packs for existing worlds.** The data pack window only opens from Create World so far. World generation packs need a warning, since they only affect new chunks.
- [ ] **Updates for data packs** already added to a world. The Updates screen only covers resource packs.
- [ ] **Translations** into other languages.

## Smaller things

- [ ] **Reorder by dragging**, and with the keyboard. The Order screen's ▲ and ▼ need the mouse.
- [ ] **Show which packs overlap** on the Order screen, file by file, instead of just what each pack changes most.
- [ ] **Move deleted packs to the system trash** instead of deleting them for good.
- [ ] **Run the in-game test on GitHub** too. Only the build and the unit tests run there; the in-game test needs a screen and downloads real packs.
- [ ] **Link the "new version" note to Modrinth** once the project page is public. It opens the GitHub release for now.

## Done

- [x] **Previews on by default,** so the hover info (what a pack does, what it clashes with) shows without knowing about Shift. Anyone upgrading gets them switched on once; turning them off after that sticks (1.5.0)
- [x] **Clickable "⇧ Preview" button** in the header, as well as Shift (1.5.0)
- [x] **"Tap Shift for pack info" line** while previews are off (1.5.0)
- [x] **UI size option:** Compact or Normal, plus preview size and a choice of colors (1.5.0)
- [x] **Settings page** behind the ⚙ button and Mod Menu (1.5.0)
- [x] **Pack order:** an Order screen with ▲ and ▼ to choose which pack wins (1.5.0)
- [x] **Clash warnings** when switching on two Vanilla Tweaks packs that clash, with an offer to turn the other off (1.5.0)
- [x] **Vanilla Tweaks credit** kept at the end of every downloaded pack's description (1.5.0)
- [x] **Presets:** named sets of switches, one click to swap, and a key to step through them (1.5.0)
- [x] **Split combined Vanilla Tweaks zips** into one switch per tweak, from the list inside the zip (1.5.0)
- [x] **Update reminder** at start-up, once a day at most, with a switch to turn it off (1.5.0)
- [x] **Delete packs from the screen** with a right-click (1.5.0)
- [x] **Modrinth filters:** by tag, and hiding packs made for mods (1.5.0)
- [x] **Sort and pin switches:** by name, newest or on-first, with pinned packs on top (1.5.0)
- [x] **Export / import switches** as a small file; missing Vanilla Tweaks and Modrinth packs are downloaded on import (1.5.0)
- [x] **Modrinth data packs** as a third tab of the Create World data pack window (1.5.0)
- [x] **Offline and error handling:** plain-words messages and a Try Again button (1.5.0)
- [x] **Pack lists saved on disk,** like pictures, so screens open at once and still show offline (1.5.0)
- [x] **Back off when rate-limited:** waits as long as the site asks, then tries again (1.5.0)
- [x] **Survive broken packs:** a damaged zip shows in red with a button to download it again (1.5.0)
- [x] **Keyboard and narrator support** for the switches, tiles and cards: Tab, Enter, a focus ring, and spoken names with their state (1.5.0)
- [x] **Automatic builds on GitHub** for every version, with the jars attached to releases (1.5.0)
- [x] **Unit tests** for the Vanilla Tweaks and Modrinth readers and the other logic that doesn't need the game (1.5.0)
- [x] **Check for TweakSwitch updates** and show a "new version" note. It asks GitHub, since the Modrinth page isn't public yet (1.5.0)
- [x] **Previews on your switches:** hover a switch to see the same pictures as in the picker (already there since 1.2.0)
- [x] **Vanilla Tweaks data packs and crafting tweaks** in their own window on the Create World screen's More tab, switched on for the new world (1.4.0)
- [x] **Download Mods:** packs that need mods can fetch them from Modrinth into the mods folder, with a note to restart (1.4.0)
- [x] **Pictures saved on disk** for a week, up to 256 MB (1.4.0)
- [x] **Switch On:** downloaded packs come back switched on, with a toggle in the downloaders (1.4.0)
- [x] **Previews for packs you already have** in the Vanilla Tweaks and Modrinth pickers (1.4.0)
- [x] **Minecraft 1.21.9, 1.21.10 and 1.21.11:** same features as on 26.x, all passing the in-game test (1.3.0)
- [x] **Minecraft 26.1, 26.2 and 26.3:** one jar per version family from one codebase, all passing the in-game test (1.2.1)
- [x] **Sharper previews.** Pictures are decoded at the size they're shown on screen, resized smoothly, drawn with smooth scaling, and Modrinth uses full-size gallery pictures (1.2.0)
- [x] **Smaller UI.** One-row header (title, search, buttons), 16 px buttons, 22 px switch cards, two-line Modrinth cards, smaller Vanilla Tweaks tiles (1.2.0)
- [x] **Shift previews** on the TweakSwitch, Vanilla Tweaks and Modrinth screens: tap Shift to turn the preview panel on or off (1.2.0)
- [x] **See what any pack does:** previews show the textures inside a pack, plus "Changes: Particles (12)…" (1.2.0)
- [x] **Group switches** by source, by what they change, by on/off, or A–Z (1.2.0)
- [x] **Tabs for where packs came from:** Imported, Vanilla Tweaks and Modrinth each have their own tab with a count, so your own packs and downloads don't mix (1.2.0)
- [x] **Sub-groups** in "Changes" mode for packs about the same thing ("Particles · Dripping") (1.2.0)
- [x] **Whole-group switch** on each group header turns every pack in it on or off (1.2.0)
- [x] **Texture icons** for packs that share one logo or have none, so split-up packs are easy to tell apart (1.2.0)
- [x] **Particle definition previews:** packs that only point to textures show those textures, found in your other packs or the game's own (1.2.0)
- [x] **Search inside packs:** file names count, so "flame" finds a pack that changes the flame particle (1.2.0)

- [x] On/off switches, categories, Mod Menu (1.0)
- [x] Vanilla Tweaks and Modrinth downloaders, previews, mod warnings, updates (1.0)
- [x] New themed look (1.1.0)
- [x] Works with Fabric Loader 0.19.5 (1.1.1)
