package com.tweakswitch;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.tweakswitch.compat.Compat;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The two checks TweakSwitch makes on its own, at most once a day, when the title screen first shows: whether a newer
 * TweakSwitch is out, and whether any of your Modrinth packs have updates. Each can be switched off in Settings.
 */
public final class StartupCheck {
	private static final Logger LOGGER = LogUtils.getLogger();
	/** Where a newer TweakSwitch is downloaded from. */
	public static final URI DOWNLOAD_PAGE = URI.create("https://github.com/Kyle-15/TweakSwitch/releases/latest");
	private static final String LATEST_RELEASE = "https://api.github.com/repos/Kyle-15/TweakSwitch/releases/latest";
	private static final long INTERVAL_MS = 20L * 60 * 60 * 1000;
	private static boolean started;

	private StartupCheck() {
	}

	/** The running TweakSwitch version without its Minecraft suffix: "1.5.0" for "1.5.0+26.3". */
	public static String currentVersion() {
		return FabricLoader.getInstance().getModContainer(TweakSwitch.MOD_ID).map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("0");
	}

	/** A TweakSwitch version newer than the running one, if the check has seen one and the note isn't switched off. */
	public static @Nullable String newerVersion() {
		Settings settings = Settings.get();
		return settings.modUpdateCheck && isNewer(settings.knownModVersion, currentVersion()) ? settings.knownModVersion : null;
	}

	/** Whether version {@code candidate} ("v1.5.1", "1.5.1+26.3") is newer than {@code current}, comparing the numbers. */
	public static boolean isNewer(final @Nullable String candidate, final String current) {
		if (candidate == null || candidate.isBlank()) {
			return false;
		}
		int[] a = numbers(candidate);
		int[] b = numbers(current);
		if (a.length == 0) {
			return false;
		}
		for (int i = 0; i < Math.max(a.length, b.length); i++) {
			int left = i < a.length ? a[i] : 0;
			int right = i < b.length ? b[i] : 0;
			if (left != right) {
				return left > right;
			}
		}
		return false;
	}

	/** "v1.5.1+26.3" → {1, 5, 1}: the dotted numbers before any "+" or "-" suffix. */
	static int[] numbers(final String version) {
		String core = version.strip();
		if (core.startsWith("v") || core.startsWith("V")) {
			core = core.substring(1);
		}
		core = core.split("[+\\-\\s]", 2)[0];
		List<Integer> parts = new ArrayList<>();
		for (String part : core.split("\\.")) {
			try {
				parts.add(Integer.parseInt(part));
			} catch (NumberFormatException e) {
				return new int[0];
			}
		}
		return parts.stream().mapToInt(Integer::intValue).toArray();
	}

	/** Runs the checks in the background if they're due. Safe to call every time the title screen opens. */
	public static void run(final Minecraft minecraft) {
		Settings settings = Settings.get();
		if (started || !settings.updateReminder && !settings.modUpdateCheck) {
			return;
		}
		started = true;
		long now = System.currentTimeMillis();
		if (now - settings.lastStartupCheck < INTERVAL_MS) {
			return;
		}
		settings.lastStartupCheck = now;
		settings.save();
		Thread thread = new Thread(() -> check(minecraft, settings.modUpdateCheck, settings.updateReminder), "TweakSwitch start-up check");
		thread.setDaemon(true);
		thread.start();
	}

	/** Does the checks now, whatever the time since the last one. Blocks; shows a toast for each thing found. */
	public static void check(final Minecraft minecraft, final boolean modVersion, final boolean packUpdates) {
		String latest = null;
		if (modVersion) {
			try {
				JsonObject release = JsonParser.parseString(WebClient.getString(URI.create(LATEST_RELEASE))).getAsJsonObject();
				latest = release.get("tag_name").getAsString();
			} catch (Exception e) {
				LOGGER.debug("Couldn't check for a newer TweakSwitch", e);
			}
		}
		int updates = -1;
		if (packUpdates) {
			try {
				updates = countPackUpdates();
			} catch (Exception e) {
				LOGGER.debug("Couldn't check packs for updates", e);
			}
		}
		String found = latest;
		int count = updates;
		minecraft.execute(() -> {
			Settings settings = Settings.get();
			if (found != null) {
				settings.knownModVersion = found.startsWith("v") ? found.substring(1) : found;
			}
			if (count >= 0) {
				settings.knownPackUpdates = count;
			}
			settings.save();
			if (count > 0) {
				SystemToast.addOrUpdate(Compat.toasts(minecraft), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
					Component.translatable(count == 1 ? "tweakswitch.reminder.one" : "tweakswitch.reminder.many", count), Component.translatable("tweakswitch.reminder.hint"));
			} else if (newerVersion() != null) {
				SystemToast.addOrUpdate(Compat.toasts(minecraft), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
					Component.translatable("tweakswitch.modUpdate", newerVersion()), Component.translatable("tweakswitch.modUpdate.hint"));
			}
		});
	}

	/** How many pack zips in the TweakSwitch folder have a newer version on Modrinth for this Minecraft version. */
	public static int countPackUpdates() throws Exception {
		List<String> hashes = new ArrayList<>();
		Path folder = TweakSwitch.packsDir();
		if (!Files.isDirectory(folder)) {
			return 0;
		}
		try (Stream<Path> files = Files.list(folder)) {
			for (Path zip : files.filter(file -> Files.isRegularFile(file) && file.getFileName().toString().endsWith(".zip")).toList()) {
				hashes.add(Modrinth.sha1(zip));
			}
		}
		if (hashes.isEmpty()) {
			return 0;
		}
		Modrinth.UpdateCheck check = Modrinth.checkUpdates(hashes);
		int updates = 0;
		for (String hash : hashes) {
			Modrinth.Version latest = check.latest().get(hash);
			if (check.current().containsKey(hash) && latest != null && !latest.sha1().equalsIgnoreCase(hash)) {
				updates++;
			}
		}
		return updates;
	}
}
