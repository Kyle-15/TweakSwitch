package com.tweakswitch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The Modrinth readers, against cut-down copies of its API replies. */
class ModrinthTest {
	private static Modrinth.Page page() throws IOException {
		try (InputStream stream = ModrinthTest.class.getResourceAsStream("/modrinth-search.json")) {
			return Modrinth.parseSearch(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
		}
	}

	@Test
	void readsHitsAndTheTotal() throws IOException {
		Modrinth.Page page = page();
		assertEquals(5488, page.totalHits());
		assertEquals(List.of("Fresh Animations", "Terralith"), page.projects().stream().map(Modrinth.Project::title).toList());
		assertEquals(51568648L, page.projects().get(0).downloads());
		assertEquals("FreshLX", page.projects().get(0).author());
	}

	@Test
	void featuredPictureComesFirstWithoutRepeating() throws IOException {
		assertEquals(List.of("https://cdn.modrinth.com/b.png", "https://cdn.modrinth.com/a.png"), page().projects().get(0).images());
	}

	@Test
	void tagsLeaveOutLoaders() throws IOException {
		assertEquals(List.of("entities", "vanilla-like"), page().projects().get(0).categories());
		assertEquals(List.of("worldgen"), page().projects().get(1).categories());
	}

	@Test
	void missingFieldsDontBreakAHit() throws IOException {
		Modrinth.Project terralith = page().projects().get(1);
		assertEquals("", terralith.description());
		assertNull(terralith.iconUrl());
		assertEquals(List.of(), terralith.images());
		assertEquals(0, terralith.downloads());
	}

	@Test
	void fileNamesAreSafe() throws IOException {
		Modrinth.Project project = new Modrinth.Project("id", "slug", "Fresh Animations: Extensions", "", "", 0, List.of(), null, List.of());
		assertEquals("MR - Fresh Animations_ Extensions.zip", project.fileName());
	}

	@Test
	void searchFiltersByTypeVersionAndTags() {
		assertEquals("[[\"project_type:resourcepack\"],[\"versions:26.3\"]]", Modrinth.facets(Modrinth.Type.RESOURCE_PACKS, "26.3", List.of(), false));
		assertEquals("[[\"project_type:datapack\"],[\"versions:1.21.10\"],[\"categories:magic\"],[\"categories:worldgen\"]]",
			Modrinth.facets(Modrinth.Type.DATA_PACKS, "1.21.10", List.of("worldgen", "magic"), false));
	}

	@Test
	void hidingModdedPacksOnlyAppliesToResourcePacks() {
		assertTrue(Modrinth.facets(Modrinth.Type.RESOURCE_PACKS, "26.3", List.of("16x"), true).endsWith(",[\"categories:16x\"],[\"categories!=modded\"]]"));
		assertEquals("[[\"project_type:datapack\"],[\"versions:26.3\"]]", Modrinth.facets(Modrinth.Type.DATA_PACKS, "26.3", List.of(), true));
	}

	@Test
	void searchAddressCarriesTheQueryAndPage() {
		String url = Modrinth.searchUrl(Modrinth.Type.DATA_PACKS, "26.3", " vein miner ", Modrinth.Sort.DOWNLOADS, 30, List.of(), false);
		assertTrue(url.startsWith("https://api.modrinth.com/v2/search?limit=30&offset=30&index=downloads&facets="), url);
		assertTrue(url.endsWith("&query=vein+miner"), url);
		assertTrue(!Modrinth.searchUrl(Modrinth.Type.DATA_PACKS, "26.3", "  ", Modrinth.Sort.NEWEST, 0, List.of(), false).contains("query="));
	}

	@Test
	void versionsUseThePrimaryFileAndSkipDependenciesWithoutAProject() {
		String json = """
			{"id":"v1","project_id":"p1","version_number":"2.5.13",
			 "files":[
			   {"url":"https://cdn/x-sources.zip","filename":"x-sources.zip","primary":false,"hashes":{"sha1":"aaa"}},
			   {"url":"https://cdn/x.zip","filename":"x.zip","primary":true,"hashes":{"sha1":"bbb"}}],
			 "dependencies":[
			   {"project_id":"dep1","dependency_type":"required"},
			   {"project_id":null,"dependency_type":"embedded"},
			   {"project_id":"dep2","dependency_type":"optional"}]}""";
		Modrinth.Version version = Modrinth.parseVersion(JsonParser.parseString(json).getAsJsonObject());
		assertEquals("2.5.13", version.number());
		assertEquals("x.zip", version.fileName());
		assertEquals("bbb", version.sha1());
		assertEquals(List.of(new Modrinth.Dependency("dep1", "required"), new Modrinth.Dependency("dep2", "optional")), version.dependencies());
	}

	@Test
	void everyTypeHasFilterTags() {
		assertTrue(Modrinth.Type.RESOURCE_PACKS.categories().contains("16x"));
		assertTrue(Modrinth.Type.DATA_PACKS.categories().contains("worldgen"));
		assertTrue(!Modrinth.Type.RESOURCE_PACKS.categories().contains(Modrinth.MODDED));
	}
}
