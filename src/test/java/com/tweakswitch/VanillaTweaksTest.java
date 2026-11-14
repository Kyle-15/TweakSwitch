package com.tweakswitch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The Vanilla Tweaks readers, against a cut-down copy of the site's category file. A site change shows up here first. */
class VanillaTweaksTest {
	private static String resource(final String name) throws IOException {
		try (InputStream stream = VanillaTweaksTest.class.getResourceAsStream("/" + name)) {
			return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static List<VanillaTweaks.VtPack> packs() throws IOException {
		return VanillaTweaks.parsePacks(resource("rpcategories.json"), VanillaTweaks.Kind.RESOURCE_PACKS);
	}

	@Test
	void readsEveryPackIncludingNestedCategories() throws IOException {
		assertEquals(List.of("BlackNetherBricks", "CherryPicking", "PrideXpBar", "armor statues"), packs().stream().map(VanillaTweaks.VtPack::id).toList());
	}

	@Test
	void categoryPathsUseTheSitesSlugs() throws IOException {
		VanillaTweaks.VtPack pride = packs().get(2);
		// Every character that isn't a letter or digit becomes a dash, as the site's zip builder expects
		assertEquals("aesthetic.lgbtq--pride", pride.categoryPath());
		assertEquals("Aesthetic › LGBTQ+ Pride", pride.category());
	}

	@Test
	void descriptionsLoseTheirHtml() throws IOException {
		assertEquals("Makes nether bricks black.\nLooks great with blackstone & basalt.", packs().get(0).description());
	}

	@Test
	void keepsClashesWarningsAndPreviewType() throws IOException {
		List<VanillaTweaks.VtPack> packs = packs();
		assertEquals(List.of("OtherCake"), packs.get(1).incompatible());
		assertEquals("gif", packs.get(1).previewExtension());
		assertNull(packs.get(0).previewExtension());
		assertEquals("Needs OptiFine or a mod like it", packs.get(2).warning());
		assertNull(packs.get(0).warning());
	}

	@Test
	void fileNamesDropCharactersWindowsRefuses() throws IOException {
		assertEquals("VT - Armor_ Statues_.zip", packs().get(3).fileName());
	}

	@Test
	void descriptionsKeepTheVanillaTweaksCredit() {
		assertEquals("Cherries on cakes.\n" + VanillaTweaks.CREDIT, VanillaTweaks.describe("Cherries on cakes."));
		assertEquals(VanillaTweaks.CREDIT, VanillaTweaks.describe(""));
		assertTrue(VanillaTweaks.CREDIT.contains("vanillatweaks.net"));
	}

	@Test
	void readsTheTweaksInsideACombinedZip() {
		String list = "Vanilla Tweaks Resource Pack\nVersion: 1.21\nPacks:\n\tBushyLeaves\n\tCherryPicking\n\tBlackNetherBricks";
		assertEquals(List.of("BushyLeaves", "CherryPicking", "BlackNetherBricks"), VanillaTweaks.parseSelectedPacks(list));
	}

	@Test
	void combinedZipListsTolerateWindowsLineEndingsAndCategoryHeadings() {
		String list = "Vanilla Tweaks Resource Pack\r\nVersion: 1.21\r\nPacks:\r\n\r\nAesthetic:\r\n    CherryPicking\r\nTerrain:\r\n    BushyLeaves\r\n    CherryPicking\r\n";
		assertEquals(List.of("CherryPicking", "BushyLeaves"), VanillaTweaks.parseSelectedPacks(list));
	}

	@Test
	void aFileWithoutAPackListHasNoTweaks() {
		assertEquals(List.of(), VanillaTweaks.parseSelectedPacks("Just some notes\nNothing to see"));
	}
}
