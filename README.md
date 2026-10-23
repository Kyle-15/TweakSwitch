# TweakSwitch

A Fabric mod for Minecraft 26.3 that turns resource packs into simple on/off switches, and downloads packs for you from Vanilla Tweaks and Modrinth.

- **Switches:** every pack gets an ON/OFF button, grouped by category. Changes apply with one reload when you click Done.
- **Vanilla Tweaks:** browse all their resource packs in-game and download each one as its own switch, instead of one combined zip.
- **Modrinth:** search Modrinth's resource packs for your Minecraft version, sorted by downloads, newest and more.
- **Previews:** hover a pack to see its pictures. Animated previews play, and Modrinth galleries step through every picture.
- **Mod warnings:** packs that need mods show a warning, with a link to each mod's download page before anything downloads.
- **Updates:** check every pack you have for newer versions and update them in one click.

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for Minecraft **26.3** (loader 0.20.1 or newer).
2. Download these into your `.minecraft/mods` folder:
   - **TweakSwitch:** the `.jar` from this repo's [Releases](../../releases) page
   - **[Fabric API](https://modrinth.com/mod/fabric-api)** for 26.3 (required)
   - **[Mod Menu](https://modrinth.com/mod/modmenu)** for 26.3 (optional, adds TweakSwitch to the mods list)
3. Launch Minecraft with the Fabric profile.

## Use

- Press **K** in-game (change it in Controls → TweakSwitch), or click **TweakSwitch** in the top-left of the Resource Packs screen.
- **Vanilla Tweaks / Modrinth:** click packs to pick them (yellow ✚), then **Download**. They show up as switches.
- **Updates:** check your packs for newer versions.
- **Open Folder:** your packs live in `.minecraft/tweakswitch/packs`. You can also drag any resource pack `.zip` onto the TweakSwitch screen.

## Build from source

Needs Java 25.

```bash
./gradlew build
```

The mod jar ends up in `build/libs/`. `./gradlew runClientGameTest` runs the in-game screenshot test (needs internet).

## Credits

- Packs from [Vanilla Tweaks](https://vanillatweaks.net/) are downloaded straight from their site and not included in this mod. Credit for those packs goes to Vanilla Tweaks.
- Modrinth search and downloads use the public [Modrinth API](https://docs.modrinth.com/).
- WebP image support by [TwelveMonkeys ImageIO](https://github.com/haraldk/TwelveMonkeys).
