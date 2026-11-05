# TweakSwitch

A Fabric mod for Minecraft 1.21.9 to 1.21.11 and 26.1 to 26.3 that turns resource packs into simple on/off switches, and downloads packs for you from Vanilla Tweaks and Modrinth.

- **Tabs:** packs you added yourself (**Imported**) and packs downloaded from **Vanilla Tweaks** or **Modrinth** each have their own tab, so it's clear where each came from.
- **Switches:** every pack gets an ON/OFF switch. Group them by source, by what they change (Particles, GUI, Blocks…, with sub-groups like "Particles · Dripping"), by on/off, or A–Z, and flip a whole group with the switch on its header. Changes apply with one reload when you click Done.
- **Search inside packs:** search matches names, descriptions and the files inside each pack, so "flame" finds a pack that changes the flame particle.
- **Vanilla Tweaks:** browse all their resource packs in-game and download each one as its own switch, instead of one combined zip.
- **Modrinth:** search Modrinth's resource packs for your Minecraft version, sorted by downloads, newest and more.
- **Previews:** tap **Shift** to turn previews on, then hover a pack. Website packs show their pictures (animations play, Modrinth galleries step through), and any other pack shows the textures inside it, so you can tell what it does even without a proper name.
- **Mod warnings:** packs that need mods show a warning before anything downloads. **Download Mods** saves the Fabric version of each one (and any mods it needs) to your mods folder; they work once you restart Minecraft. Or open each mod's page yourself.
- **Data packs for new worlds:** on the Create World screen's **More** tab, **Vanilla Tweaks Data Packs...** opens a separate window for Vanilla Tweaks data packs and crafting tweaks. Picked ones download into the new world and switch on when you click Done. They're kept apart from resource packs because they change gameplay, not looks.
- **Updates:** check every pack you have for newer versions and update them in one click.
- **Saved pictures:** icons, previews and animations are kept on disk for a week, so screens fill in straight away and the websites are asked less often.

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
- **Shift:** tap it to turn hover previews on or off.
- **Updates:** check your packs for newer versions.
- **Data packs:** when creating a world, open the **More** tab and click **Vanilla Tweaks Data Packs...**.
- **Open Folder:** your packs live in `.minecraft/tweakswitch/packs`. You can also drag any resource pack `.zip` onto the TweakSwitch screen; those go on the **Imported** tab.

## Build from source

Needs Java 25.

```bash
./scripts/build-all.sh
```

That builds a jar for every supported Minecraft version into `build/release/`. For just one version, run `./gradlew build -Pmc=26.2` (default `26.3`); the jar lands in `build/libs/`.

Each version's dependencies are in `versions/<mc>.properties`. The few game calls whose names differ between versions are in `src/compat/<version>/`, and the drawing calls in `src/render/<family>/`. 1.21.x builds against Java 21 with Mojang's mappings; 26.x needs Java 25. `./gradlew runClientGameTest -Pmc=26.2` runs the in-game screenshot test on that version (needs internet).

## Network use

TweakSwitch only goes online when you open its download or update screens:

- **Vanilla Tweaks** (vanillatweaks.net): loads the resource pack, data pack and crafting tweak lists and pictures, and asks the site to build each pack you download.
- **Modrinth** (api.modrinth.com, cdn.modrinth.com): searches, previews and downloads, including mods when you click Download Mods. The Updates screen sends the SHA-1 hashes of the pack files in your TweakSwitch folder so Modrinth can say which have newer versions.

Pictures are saved in `.minecraft/tweakswitch/cache/images` (up to 256 MB, oldest deleted first); delete the folder any time.

No account, name or other personal information is sent.

## License

MIT, see [LICENSE](LICENSE). Bundled libraries are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Credits

- Packs from [Vanilla Tweaks](https://vanillatweaks.net/) are downloaded straight from their site and not included in this mod. Credit for those packs goes to Vanilla Tweaks. The in-game downloader is used with their permission.
- Modrinth search and downloads use the public [Modrinth API](https://docs.modrinth.com/).
- WebP image support by [TwelveMonkeys ImageIO](https://github.com/haraldk/TwelveMonkeys).
