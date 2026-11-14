package com.tweakswitch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The small pieces of logic that don't need the game running: retries, error messages, versions, presets, exports. */
class SmallPartsTest {
	// ---- Waiting before a retry

	@Test
	void waitsAsLongAsTheSiteAsks() {
		assertEquals(3250, WebErrors.retryDelayMs(429, Optional.of("3"), Optional.empty(), 1));
		// Modrinth says how many seconds until its limit resets
		assertEquals(7250, WebErrors.retryDelayMs(429, Optional.empty(), Optional.of("7"), 1));
	}

	@Test
	void neverWaitsLongerThanTwentySeconds() {
		assertEquals(WebErrors.MAX_RETRY_DELAY_MS, WebErrors.retryDelayMs(429, Optional.of("600"), Optional.empty(), 1));
	}

	@Test
	void waitsLongerEachTimeWhenTheSiteDoesntSay() {
		assertEquals(5000, WebErrors.retryDelayMs(429, Optional.empty(), Optional.empty(), 1));
		assertEquals(10000, WebErrors.retryDelayMs(429, Optional.empty(), Optional.empty(), 2));
		assertEquals(1000, WebErrors.retryDelayMs(503, Optional.empty(), Optional.empty(), 1));
		// A date instead of seconds isn't understood, so the fallback applies
		assertEquals(1000, WebErrors.retryDelayMs(503, Optional.of("Wed, 21 Oct 2026 07:28:00 GMT"), Optional.empty(), 1));
	}

	@Test
	void onlyBusyAndRateLimitRepliesAreRetried() {
		assertTrue(WebErrors.retryable(429));
		assertTrue(WebErrors.retryable(503));
		assertFalse(WebErrors.retryable(404));
		assertFalse(WebErrors.retryable(500));
		assertFalse(WebErrors.retryable(200));
	}

	// ---- Error messages

	@Test
	void failuresGetAMessageThatFits() {
		URI uri = URI.create("https://api.modrinth.com/v2/search");
		assertEquals("tweakswitch.error.offline", WebErrors.messageKey(new ConnectException()));
		assertEquals("tweakswitch.error.offline", WebErrors.messageKey(new IOException(new java.net.UnknownHostException("vanillatweaks.net"))));
		assertEquals("tweakswitch.error.timeout", WebErrors.messageKey(new HttpTimeoutException("request timed out")));
		assertEquals("tweakswitch.error.rateLimited", WebErrors.messageKey(new WebErrors.HttpStatusException(429, uri)));
		assertEquals("tweakswitch.error.siteDown", WebErrors.messageKey(new WebErrors.HttpStatusException(503, uri)));
		assertEquals("tweakswitch.error.notFound", WebErrors.messageKey(new WebErrors.HttpStatusException(404, uri)));
		assertEquals("tweakswitch.error.refused", WebErrors.messageKey(new WebErrors.HttpStatusException(403, uri)));
		assertEquals("tweakswitch.error.other", WebErrors.messageKey(new IllegalStateException("odd")));
		assertEquals("tweakswitch.error.other", WebErrors.messageKey(null));
		assertEquals("api.modrinth.com", new WebErrors.HttpStatusException(503, uri).host());
	}

	// ---- Version numbers

	@Test
	void newerVersionsAreSpotted() {
		assertTrue(StartupCheck.isNewer("v1.5.1", "1.5.0+26.3"));
		assertTrue(StartupCheck.isNewer("1.10.0", "1.9.9"));
		assertTrue(StartupCheck.isNewer("2.0", "1.9.9+1.21.10"));
		assertTrue(StartupCheck.isNewer("1.5.0.1", "1.5.0"));
	}

	@Test
	void sameOrOlderVersionsArent() {
		assertFalse(StartupCheck.isNewer("v1.5.0", "1.5.0+26.3"));
		assertFalse(StartupCheck.isNewer("1.4.9", "1.5.0"));
		assertFalse(StartupCheck.isNewer("1.5", "1.5.0"));
		assertFalse(StartupCheck.isNewer("", "1.5.0"));
		assertFalse(StartupCheck.isNewer(null, "1.5.0"));
		assertFalse(StartupCheck.isNewer("nightly", "1.5.0"));
	}

	// ---- Presets and pack order

	@Test
	void aPresetReplacesOnlyTweakSwitchPacks() {
		List<String> selected = List.of("vanilla", "tweakswitch/a.zip", "file/other.zip", "tweakswitch/b.zip");
		List<String> available = List.of("vanilla", "file/other.zip", "tweakswitch/a.zip", "tweakswitch/b.zip", "tweakswitch/c.zip");
		// c then a, on top of everything else; b goes off; "gone.zip" no longer exists and is skipped
		assertEquals(List.of("vanilla", "file/other.zip", "tweakswitch/c.zip", "tweakswitch/a.zip"),
			Presets.selectionFor(selected, List.of("c.zip", "gone.zip", "a.zip"), available));
	}

	@Test
	void reorderingKeepsOtherPacksInPlace() {
		List<String> selected = List.of("vanilla", "tweakswitch/a.zip", "file/other.zip", "tweakswitch/b.zip", "tweakswitch/c.zip");
		assertEquals(List.of("vanilla", "tweakswitch/c.zip", "file/other.zip", "tweakswitch/a.zip", "tweakswitch/b.zip"),
			Presets.reorder(selected, List.of("tweakswitch/c.zip", "tweakswitch/a.zip", "tweakswitch/b.zip")));
	}

	@Test
	void reorderingIgnoresPacksThatArentOnAndKeepsUnnamedOnesLast() {
		List<String> selected = List.of("tweakswitch/a.zip", "tweakswitch/b.zip", "tweakswitch/c.zip");
		assertEquals(List.of("tweakswitch/c.zip", "tweakswitch/a.zip", "tweakswitch/b.zip"),
			Presets.reorder(selected, List.of("tweakswitch/off.zip", "tweakswitch/c.zip")));
	}

	@Test
	void presetsFileIsReadInOrder() {
		Map<String, List<String>> presets = Presets.parse("{\"PvP\":[\"a.zip\"],\"Building\":[\"b.zip\",\"c.zip\"]}");
		assertEquals(List.of("PvP", "Building"), List.copyOf(presets.keySet()));
		assertEquals(List.of("b.zip", "c.zip"), presets.get("Building"));
	}

	// ---- Export and import

	@Test
	void anExportReadsBackTheSame() {
		PackShare.Export export = new PackShare.Export("26.3", List.of(
			new PackShare.Shared("VT - Cherry Picking.zip", "Cherry Picking", "Aesthetic", "vanillatweaks", "CherryPicking", true),
			new PackShare.Shared("my-pack.zip", "my-pack", null, null, null, false)));
		PackShare.Export back = PackShare.parse(PackShare.toJson(export));
		assertEquals(export, back);
		assertTrue(back.packs().get(0).downloadable());
		assertFalse(back.packs().get(1).downloadable());
	}

	@Test
	void otherJsonFilesArentExports() {
		assertNull(PackShare.parse("{\"pack\":{\"description\":\"x\"}}"));
		assertNull(PackShare.parse("not json at all"));
		assertNull(PackShare.parse("[1,2,3]"));
	}

	@Test
	void importedFileNamesCantPointOutsideTheFolder() {
		PackShare.Export export = PackShare.parse("""
			{"tweakswitch_export":1,"minecraft":"26.3","packs":[
			  {"file":"../../options.txt","on":true},
			  {"file":"sub\\\\evil.zip","on":true},
			  {"file":"fine.zip","on":true},
			  {"on":true}]}""");
		assertNotNull(export);
		assertEquals(List.of("fine.zip"), export.packs().stream().map(PackShare.Shared::file).toList());
	}
}
