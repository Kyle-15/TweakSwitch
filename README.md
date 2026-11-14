# TweakSwitch

A Fabric mod for Minecraft 1.21.9 to 1.21.11 and 26.1 to 26.3 that turns resource packs into simple on/off switches, and downloads packs for you from Vanilla Tweaks and Modrinth.

- **Tabs:** packs you added yourself (**Imported**) and packs downloaded from **Vanilla Tweaks** or **Modrinth** each have their own tab, so it's clear where each came from.
- **Switches:** every pack gets an ON/OFF switch. Group them by source, by what they change (Particles, GUI, Blocks…, with sub-groups like "Particles · Dripping"), by on/off, or A–Z, and flip a whole group with the switch on its header. Changes apply with one reload when you click Done.
- **Search inside packs:** search matches names, descriptions and the files inside each pack, so "flame" finds a pack that changes the flame particle.
- **Vanilla Tweaks:** browse all their resource packs in-game and download each one as its own switch, instead of one combined zip.
- **Modrinth:** search Modrinth's resource packs for your Minecraft version, sorted by downloads, newest and more, and narrowed with **Filters** (style, what it changes, resolution, or hiding packs made for mods).
- **Previews:** hover a pack to see what it does. Website packs show their pictures (animations play, Modrinth galleries step through), and any other pack shows the textures inside it, so you can tell what it does even without a proper name. Previews are on to begin with; tap **Shift** or click **⇧ Preview** to turn them off or on.
- **Presets:** save the packs that are on as a named set ("PvP", "Building") and switch between sets in one click, or with a key.
- **Pack order:** when two packs change the same thing, choose which one wins.
- **Sort, pin and delete:** sort switches by name, newest or on-first, pin favorites to the top, and delete a pack with a right-click.
- **Clash warnings:** switching on a Vanilla Tweaks pack that clashes with one that's already on (two crosshairs, say) asks which to keep.
- **Export and import:** save your pack list and switch states to a small file. A friend who drops it on their TweakSwitch screen gets the same switches, and any Vanilla Tweaks or Modrinth packs they don't have are downloaded.
- **Split combined Vanilla Tweaks zips:** drop a zip from the Vanilla Tweaks website on the screen and TweakSwitch offers to download each tweak inside as its own switch.
- **Mod warnings:** packs that need mods show a warning before anything downloads. **Download Mods** saves the Fabric version of each one (and any mods it needs) to your mods folder; they work once you restart Minecraft. Or open each mod's page yourself.
- **Data packs for new worlds:** on the Create World screen's **More** tab, **Download Data Packs...** opens a separate window with three tabs: Vanilla Tweaks **data packs**, Vanilla Tweaks **crafting tweaks**, and **Modrinth**'s data packs. Picked ones download into the new world and switch on when you click Done. They're kept apart from resource packs because they change gameplay, not looks.
- **Updates:** check every pack you have for newer versions and update them in one click. A reminder at start-up says when packs have updates, and a note shows when a newer TweakSwitch is out.
- **Saved pictures and lists:** icons, previews, animations and the pack lists are kept on disk, so screens open straight away, the websites are asked less often, and the last list still shows when you're offline.
- **When something goes wrong:** a failed load says why in plain words and has a **Try Again** button. A damaged download shows in red with a button to download it again, instead of going missing.
- **Settings:** the **⚙** button (or Mod Menu) opens one page for previews, UI size (Compact or Normal), color, and the start-up checks.
- **Keyboard and screen readers:** Tab reaches every switch, tile and card (a white ring shows where you are), Enter flips it, and the narrator reads its name and state.

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for your Minecraft version (loader 0.19.5 or newer).
2. Download these into your `.minecraft/mods` folder:
   - **TweakSwitch:** from this repo's [Releases](../../releases) page, the jar ending in your version: `+1.21.10` (for 1.21.9 and 1.21.10), `+1.21.11`, `+26.1` (for 26.1 to 26.1.2), `+26.2` or `+26.3`
   - **[Fabric API](https://modrinth.com/mod/fabric-api)** for your version (required)
   - **[Mod Menu](https://modrinth.com/mod/modmenu)** for your version (optional, adds TweakSwitch to the mods list)
3. Launch Minecraft with the Fabric profile.

## Use

- Press **K** in-game (change it in Controls → TweakSwitch), or click **TweakSwitch** in the top-left of the Resource Packs screen.
- **+ Vanilla Tweaks / + Modrinth:** click packs to pick them, then **Download**. They show up as switches on their own tab, already switched on; click **Done** to apply. Turn **Switch On** off in the downloader if you'd rather choose yourself.
- **Shift** or **⇧ Preview:** turns hover previews off or on.
- **Right-click a switch** for its menu: pin it to the top, delete it, split a combined Vanilla Tweaks zip, or open the folder. With a switch focused by keyboard, **P** pins and **Delete** deletes.
- **Sort** (beside the tabs) and **Group** (beside the search box) change how the switches are laid out.
- **Presets:** type a name, click **Save Current**, and click a preset later to switch back to it. **Export** is on the same screen; to import, drop the exported file on the screen. Set a key for "Switch to Next Preset" under Controls to swap presets while playing.
- **Order:** lists the packs that are on, highest priority first. Move a pack with ▲ and ▼ (mouse only for now).
- **Updates:** check your packs for newer versions.
- **Data packs:** when creating a world, open the **More** tab and click **Download Data Packs...**.
- **⚙ Settings → Open Folder:** your packs live in `.minecraft/tweakswitch/packs`. You can also drag any resource pack `.zip` onto the TweakSwitch screen; those go on the **Imported** tab.

## Build from source

Needs Java 25.

```bash
./scripts/build-all.sh
```

That builds a jar for every supported Minecraft version into `build/release/`. For just one version, run `./gradlew build -Pmc=26.2` (default `26.3`); the jar lands in `build/libs/`.

Each version's dependencies are in `versions/<mc>.properties`. The few game calls whose names differ between versions are in `src/compat/<version>/`, and the drawing calls in `src/render/<family>/`. 1.21.x builds against Java 21 with Mojang's mappings; 26.x needs Java 25.

Two kinds of tests:

- `./gradlew test -Pmc=26.3` runs the unit tests (the Vanilla Tweaks and Modrinth readers, retry delays, presets, exports). They need no internet and run as part of every build. GitHub Actions builds every version and runs them on each push (`.github/workflows/build.yml`).
- `./gradlew runClientGameTest -Pmc=26.2` runs the in-game screenshot test on that version. It downloads real packs, so it needs internet, and it only runs on your own machine.

## Network use

TweakSwitch goes online when you open its download or update screens, when you ask it to download something again (a damaged pack, or packs named in an imported list), and for two start-up checks you can switch off:

- **Vanilla Tweaks** (vanillatweaks.net): loads the resource pack, data pack and crafting tweak lists and pictures, and asks the site to build each pack you download.
- **Modrinth** (api.modrinth.com, cdn.modrinth.com): searches, previews and downloads, including data packs, and mods when you click Download Mods. The Updates screen sends the SHA-1 hashes of the pack files in your TweakSwitch folder so Modrinth can say which have newer versions.
- **Start-up checks**, at most once a day, each with its own switch under ⚙ Settings:
  - *Pack Update Reminder* sends the same SHA-1 hashes to Modrinth and tells you if any packs have updates.
  - *New Version Note* asks GitHub (api.github.com) for TweakSwitch's newest release number.

If a site answers that it's busy or that you're asking too often, TweakSwitch waits as long as the site says (20 seconds at most) and tries again, up to three times.

Pictures are saved in `.minecraft/tweakswitch/cache/images` (up to 256 MB, oldest deleted first) and pack lists in `.minecraft/tweakswitch/cache/lists`; delete either folder any time.

No account, name or other personal information is sent.

## License

MIT, see [LICENSE](LICENSE). Bundled libraries are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Credits

- Packs from [Vanilla Tweaks](https://vanillatweaks.net/) are downloaded straight from their site and not included in this mod. Credit for those packs goes to Vanilla Tweaks. The in-game downloader is used with their permission.
- Modrinth search and downloads use the public [Modrinth API](https://docs.modrinth.com/).
- WebP image support by [TwelveMonkeys ImageIO](https://github.com/haraldk/TwelveMonkeys).
