package com.tweakswitch;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;

/** Choices the player makes in TweakSwitch's screens, kept in tweakswitch/settings.json between sessions. */
public final class Settings {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	/** Bumped when a default changes, so a file written by an older TweakSwitch is brought up to date once. */
	private static final int CURRENT_VERSION = 2;
	private static Settings instance;

	public int version = CURRENT_VERSION;
	/** Whether the hover preview panel shows; tap Shift or click "Preview" to flip it. */
	public boolean previews = true;
	/** How the TweakSwitch screen groups packs: a {@link TweakSwitchScreen.GroupBy} name. */
	public String groupBy = "CHANGES";
	/** Which tab the TweakSwitch screen shows: a {@link TweakSwitchScreen.Tab} name. */
	public String tab = "IMPORTED";
	/** Whether downloaded resource packs come back switched on (applied with the TweakSwitch screen's Done). */
	public boolean switchOnDownloads = true;
	/** How switches are ordered inside a group: a {@link TweakSwitchScreen.SortBy} name. */
	public String sortBy = "NAME";
	/** File names of packs pinned to the top of their tab. */
	public List<String> pinned = new ArrayList<>();
	/** "COMPACT" or "NORMAL" (taller rows and buttons). */
	public String uiSize = "COMPACT";
	/** "SMALL", "NORMAL" or "LARGE": how wide the hover preview panel is. */
	public String previewSize = "NORMAL";
	/** The color of TweakSwitch's own screens: a {@link com.tweakswitch.ui.Theme.Accent} name. */
	public String accent = "GREEN";
	/** Whether switching on a Vanilla Tweaks pack that clashes with one that's already on asks what to do. */
	public boolean conflictWarnings = true;
	/** Whether a start-up check looks for newer versions of your Modrinth packs (at most once a day). */
	public boolean updateReminder = true;
	/** Whether a start-up check looks for a newer TweakSwitch (at most once a day). */
	public boolean modUpdateCheck = true;
	/** When the start-up check last ran, in milliseconds since 1970. */
	public long lastStartupCheck;
	/** The newest TweakSwitch version the start-up check has seen, shown as a note until you have it. */
	public String knownModVersion = "";
	/** How many pack updates the last start-up check found, shown on the Updates button. */
	public int knownPackUpdates;
	/** The preset applied last, so the "next preset" key knows where it is. */
	public String lastPreset = "";

	private static Path file() {
		return TweakSwitch.packsDir().getParent().resolve("settings.json");
	}

	public static Settings get() {
		if (instance == null) {
			try {
				if (Files.exists(file())) {
					JsonObject json = JsonParser.parseString(Files.readString(file())).getAsJsonObject();
					instance = GSON.fromJson(json, Settings.class);
					if (instance != null && !json.has("version")) {
						// Written before previews were on by default: switched on once, and the player's later choice sticks
						instance.previews = true;
						instance.version = CURRENT_VERSION;
					}
				}
			} catch (Exception e) {
				LOGGER.warn("Failed to read {}", file(), e);
			}
			if (instance == null) {
				instance = new Settings();
			}
			if (instance.pinned == null) {
				instance.pinned = new ArrayList<>();
			}
		}
		return instance;
	}

	public void save() {
		try {
			Files.createDirectories(file().getParent());
			Files.writeString(file(), GSON.toJson(this));
		} catch (Exception e) {
			LOGGER.warn("Failed to write {}", file(), e);
		}
	}
}
