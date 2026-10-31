package com.tweakswitch;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;

/** Choices the player makes in TweakSwitch's screens, kept in tweakswitch/settings.json between sessions. */
public final class Settings {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static Settings instance;

	/** Whether the hover preview panel shows; tap Shift to flip it. Off by default so it stays out of the way. */
	public boolean previews = false;
	/** How the TweakSwitch screen groups packs: a {@link TweakSwitchScreen.GroupBy} name. */
	public String groupBy = "SOURCE";

	private static Path file() {
		return TweakSwitch.packsDir().getParent().resolve("settings.json");
	}

	public static Settings get() {
		if (instance == null) {
			try {
				instance = Files.exists(file()) ? GSON.fromJson(Files.readString(file()), Settings.class) : null;
			} catch (Exception e) {
				LOGGER.warn("Failed to read {}", file(), e);
			}
			if (instance == null) {
				instance = new Settings();
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
