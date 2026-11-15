# Keeping TweakSwitch working

Read this before changing code or releasing. Every rule here comes from something that broke, or nearly did. Where a rule has a story, the story is given, so the rule can be judged instead of followed blindly.

How to build and test is in [README.md](README.md). What's planned is in [ROADMAP.md](ROADMAP.md).

## The short version

1. Never compare a key or mouse button to a bare number.
2. A change isn't done until the in-game test passes on all six Minecraft versions.
3. Test what the player does with the input the player uses: a real click, not a shortcut around it.
4. Don't edit files while a test run is going.
5. A release needs its five jars attached and a "Which jar do I need?" heading in its notes.
6. Never upload the same jar to CurseForge twice.
7. Say in the README and the Modrinth description whenever the mod starts going online for something new.
8. Only "kyle" and "Kyle-15" appear in anything public.

## One codebase, six Minecraft versions

TweakSwitch supports 1.21.9, 1.21.10, 1.21.11, 26.1, 26.2 and 26.3 from one set of sources, built once per version family. The versions differ in ways the compiler doesn't always catch.

- **Input numbers differ between versions.** Minecraft 26.3 renumbered its keys and mouse buttons: its left mouse button is 1 and its right is 3, where every earlier version has 0 and 1, and the key numbers changed too. Always write `InputConstants.MOUSE_BUTTON_LEFT`, `InputConstants.KEY_DELETE` and so on. Each jar is compiled against its own version, so the constant is always right; a bare number is right on some versions and wrong on others.
  - *What happened:* 1.5.0 checked for "button 1" to mean right click. On 26.3 that is the left button, so every left click on a switch opened the right-click menu and no switch could be flipped with the mouse. It compiled and passed the test on every version. Fixed in 1.5.1.
- **Calls whose names differ go in `src/compat/<version>/Compat.java`,** and drawing goes through `src/render/<family>/`. Shared code never calls a version-specific method directly. If a new game call is needed, check it exists under the same name on all six before using it in shared code; if not, add it to every `Compat.java`.
- **1.21.9 and 1.21.10 call `Identifier` by its old name, `ResourceLocation`.** The build renames it in a generated copy of the sources. The rename is by whole word, so don't use `Identifier` as a variable or method name.
- **1.21.x runs on Java 21, 26.x on Java 25.** Shared code can't use anything newer than Java 21. The mixin file's Java level follows each version (`JAVA_${java}`); a level above the running Java stops the game at start-up, which is what broke the first 1.21 jars.
- **Mixins name game classes and methods by string** (`CreateWorldScreen$MoreTab`, `getDataPackSelectionSettings`, `tryApplyNewDataPacks`, the `uiState` field). The compiler can't check those. Only the in-game test, which opens Create World on each version, proves they still match.
- **A screen's `init()` runs once.** The size and color settings are read when a screen is built, so changing them means opening a new screen (`SettingsScreen.reopen`, `TweakSwitchScreen.rebuilt`). Calling `init()` again adds every widget twice.

### Adding a Minecraft version

All of these, or the version is half-supported:

1. `versions/<mc>.properties`, with `publish_versions` if it gets its own jar.
2. `src/compat/<mc>/Compat.java`, and a `src/render/` family if drawing changed.
3. The version list in `scripts/build-all.sh` and the matrix in `.github/workflows/build.yml`.
4. README, the Modrinth description, and the "Which jar do I need?" table in the release notes.
5. Print the new version's `InputConstants` and compare them with the last version's. Assume anything can have been renumbered or renamed.
6. Run the in-game test on it.

## Testing

- **Six versions, every time.** `caffeinate -i ./gradlew runClientGameTest -Pmc=<version>` for 1.21.9, 1.21.10, 1.21.11, 26.1, 26.2 and 26.3. Passing on 26.3 says nothing about 1.21.10, and the reverse.
- **Test through the front door.** If a feature is used by clicking, the test clicks, with a real mouse press at a real position (`clickAt` in the test). Calling the method the click is supposed to reach, or pressing a button by reflection, checks the method and not the click. That is exactly how the 26.3 left-click bug got out.
- **Look at the screenshots.** The test can pass while a label is cut off, a menu isn't drawn, or a panel covers the thing it describes. All three happened while building 1.5.0 and only the pictures showed it. Check the narrow ones too (`tweakswitch-small-*`); 427 GUI pixels wide is the default window, 320 the narrowest.
- **Don't edit files during a test run, and run one at a time.** Each version compiles the sources as they are at that moment. A file saved mid-compile once produced a run with no test in it that still reported success, with 0 screenshots in 12 seconds. A pass with no screenshots is not a pass.
- **Keep the Mac awake** (`caffeinate -i`). A sleeping Mac turns a test into a timeout.
- **The in-game test uses live websites.** Results differ per game version (a pack that exists for 26.3 may not for 1.21.9), titles can be longer than the 32 characters the search box holds, and a site can blip. Write checks that ask the site what's true instead of assuming it, and re-run once before believing a network failure.
- **The test's own button click only finds buttons outside the list, and matches by label.** For a button inside the list, use `pressListButton`.
- **Unit tests (`./gradlew test`) cover what doesn't need the game:** the Vanilla Tweaks and Modrinth readers, retry delays, version comparison, presets and exports. When a site's reply format changes, fix the reader and the saved sample in `src/test/resources` together.

## Releasing

Releasing is one step for the user ("push and release") and three uploads behind it: GitHub, Modrinth and CurseForge.

1. Set `mod_version` in `gradle.properties`.
2. All six in-game tests pass on the final code. If anything changed after a version passed, that version runs again.
3. `./scripts/build-all.sh` builds the five jars into `build/release/`.
4. Commit, push, then create the GitHub release **with the five jars attached in the same command**. Publishing the release starts the uploads, and they send the jars attached to it.
5. The release notes need the heading `### Which jar do I need?`. Everything above it becomes the changelog on Modrinth and CurseForge; everything from it down stays on GitHub.
6. Watch the Publish run and read its output: one line per jar per site.

Things that go wrong:

- **CurseForge can't say what's already uploaded,** so the upload script can't skip duplicates there. Never re-run an upload for a jar CurseForge already has. To retry one jar or one site, run the workflow by hand with `only` and `sites` set. Modrinth skips versions it already has.
- **Run by hand, the workflow is a dry run unless the box is unticked.** A dry run checks the tokens and game versions and uploads nothing. Use it first whenever something about the upload changed.
- **The tokens are GitHub secrets** (`MODRINTH_TOKEN`, `CURSEFORGE_TOKEN`). Only the user creates and pastes them. The Modrinth token expires; when uploads start failing with "401", it needs renewing.
- **Descriptions and gallery pictures are not uploaded.** They're changed by hand on each site, from `modrinth-upload/`.
- **Pushing a file under `.github/workflows/` needs the `workflow` permission** on the GitHub login. Without it GitHub refuses the whole push.
- **Keep a version number once it's public.** A fix gets a new number (1.5.1), never a quiet swap of the jars, so nobody has two different files with one name.
- **Nothing is pushed or released without the user saying so.**

## Going online

- **Every request names the project,** through `WebClient` (`Kyle-15/TweakSwitch/<version>`), as Modrinth's rules ask. Don't open connections any other way.
- **No account, name or other personal information is ever sent.**
- **New network use is a promise to update.** The README's "Network use" section and the Modrinth description both list exactly when the mod goes online and what it sends. Add to both in the same change. Anything that happens without the player opening a screen (the start-up checks) needs its own off switch in Settings.
- **Never wait on the network on the render thread.** Web calls block; run them on a background thread and come back with `minecraft.execute`.
- **Sites say no sometimes.** Lists go through `ListCache` and pictures through `ImageCache`, so a saved copy shows when a site is down. A failed load shows a plain-words message and a Try Again button, never a raw error.
- **Vanilla Tweaks' downloader is used with their permission.** Their packs are never bundled in the mod, and every downloaded pack keeps "Vanilla Tweaks · vanillatweaks.net" in its description. Don't remove either.

## The player's files

- **`settings.json` outlives versions.** A new setting needs a default that suits someone upgrading, and a changed default needs a one-time step in `Settings.get()` (see `version`).
- **`downloads.json` entries can lack newer fields.** Anything added to `PackIndex.Entry` must work when it's missing.
- **File names from outside are not trusted.** Names from an imported list, a Modrinth file, or a zip are cut down to a plain name before they touch the disk, so they can't point outside the folder.
- **Delete is permanent,** so it always asks first. On Windows a pack that's switched on can't be deleted until resources have reloaded without it; `DELETE_LATER` handles that. It can't be tested on a Mac, so change it with care.
- **A pack's zip can be damaged.** Minecraft returns nothing for it. Show it (the "Damaged files" group) instead of letting it vanish.

## Text and layout

- **All on-screen text is in `en_us.json`.** Check that a key exists; a missing one shows as the raw key in-game, and nothing fails.
- **Counts need both forms** ("1 pack", "3 packs"), or wording that avoids the plural.
- **The bottom bar is full.** Seven buttons fit the default window with a few pixels to spare. Adding one, or lengthening a label, cuts another off; the widths in `TweakSwitchScreen.addFooter` were measured, not guessed.
- **Anything drawn over the screen** (the preview panel, the right-click menu) must still be drawn when nothing is hovered. The menu once sat behind an early `return` and never appeared.

## Privacy

- Only **kyle** and **Kyle-15** appear in anything public: commits, code, release notes, the sites.
- Commits are made as `kyle <182523273+Kyle-15@users.noreply.github.com>`.
- Before every commit, search the staged changes for the Mac's user name and for any email address. Before every release, search the jars too.
- No donation or payout links. No AI-generated pictures. The AI-content disclosure on Modrinth stays ticked.
