# TweakSwitch

A Fabric mod for Minecraft 26.1 to 26.3 that turns resource packs into simple on/off switches, and downloads packs for you from Vanilla Tweaks and Modrinth.

- **Tabs:** packs you added yourself (**Imported**) and packs downloaded from **Vanilla Tweaks** or **Modrinth** each have their own tab, so it's clear where each came from.
- **Switches:** every pack gets an ON/OFF switch. Group them by source, by what they change (Particles, GUI, Blocks…, with sub-groups like "Particles · Dripping"), by on/off, or A–Z, and flip a whole group with the switch on its header. Changes apply with one reload when you click Done.
- **Search inside packs:** search matches names, descriptions and the files inside each pack, so "flame" finds a pack that changes the flame particle.
- **Vanilla Tweaks:** browse all their resource packs in-game and download each one as its own switch, instead of one combined zip.
- **Modrinth:** search Modrinth's resource packs for your Minecraft version, sorted by downloads, newest and more.
- **Previews:** tap **Shift** to turn previews on, then hover a pack. Website packs show their pictures (animations play, Modrinth galleries step through), and any other pack shows the textures inside it, so you can tell what it does even without a proper name.
- **Mod warnings:** packs that need mods show a warning, with a link to each mod's download page before anything downloads.
- **Updates:** check every pack you have for newer versions and update them in one click.

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for your Minecraft version (loader 0.19.5 or newer).
2. Download these into your `.minecraft/mods` folder:
   - **TweakSwitch:** from this repo's [Releases](../../releases) page, the jar ending in your version: `+26.1` (for 26.1 to 26.1.2), `+26.2` or `+26.3`
   - **[Fabric API](https://modrinth.com/mod/fabric-api)** for your version (required)
   - **[Mod Menu](https://modrinth.com/mod/modmenu)** for your version (optional, adds TweakSwitch to the mods list)
3. Launch Minecraft with the Fabric profile.

## Use

- Press **K** in-game (change it in Controls → TweakSwitch), or click **TweakSwitch** in the top-left of the Resource Packs screen.
- **+ Vanilla Tweaks / + Modrinth:** click packs to pick them, then **Download**. They show up as switches on their own tab.
- **Shift:** tap it to turn hover previews on or off.
- **Updates:** check your packs for newer versions.
- **Open Folder:** your packs live in `.minecraft/tweakswitch/packs`. You can also drag any resource pack `.zip` onto the TweakSwitch screen; those go on the **Imported** tab.

## Build from source

Needs Java 25.

```bash
./scripts/build-all.sh
```

That builds a jar for every supported Minecraft version into `build/release/`. For just one version, run `./gradlew build -Pmc=26.2` (default `26.3`); the jar lands in `build/libs/`.

Each version's dependencies are in `versions/<mc>.properties`, and the few game calls whose names differ between versions are in `src/compat/<version>/`. `./gradlew runClientGameTest -Pmc=26.2` runs the in-game screenshot test on that version (needs internet).

## Network use

TweakSwitch only goes online when you open its download or update screens:

- **Vanilla Tweaks** (vanillatweaks.net): loads the pack list and previews, and asks the site to build each pack you download.
- **Modrinth** (api.modrinth.com, cdn.modrinth.com): searches, previews and downloads. The Updates screen sends the SHA-1 hashes of the pack files in your TweakSwitch folder so Modrinth can say which have newer versions.

No account, name or other personal information is sent.

## License

MIT, see [LICENSE](LICENSE). Bundled libraries are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Credits

- Packs from [Vanilla Tweaks](https://vanillatweaks.net/) are downloaded straight from their site and not included in this mod. Credit for those packs goes to Vanilla Tweaks.
- Modrinth search and downloads use the public [Modrinth API](https://docs.modrinth.com/).
- WebP image support by [TwelveMonkeys ImageIO](https://github.com/haraldk/TwelveMonkeys).
