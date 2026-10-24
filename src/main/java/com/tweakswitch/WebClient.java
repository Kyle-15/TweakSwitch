package com.tweakswitch;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import net.fabricmc.loader.api.FabricLoader;

/** One HTTP client for every site TweakSwitch talks to, so they all see the same User-Agent. Calls block. */
public final class WebClient {
	/** Names the project and where to reach its author, as Modrinth's API asks of apps. */
	public static final String USER_AGENT = "Kyle-15/TweakSwitch/" + FabricLoader.getInstance().getModContainer(TweakSwitch.MOD_ID).map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("dev")
		+ " (https://github.com/Kyle-15/TweakSwitch)";
	private static final HttpClient HTTP = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(15))
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();

	private WebClient() {
	}

	public static HttpRequest.Builder request(final URI uri) {
		return HttpRequest.newBuilder(uri).header("User-Agent", USER_AGENT).timeout(Duration.ofSeconds(60));
	}

	public static String getString(final URI uri) throws IOException, InterruptedException {
		return send(request(uri).GET(), HttpResponse.BodyHandlers.ofString());
	}

	public static String postForm(final URI uri, final String form) throws IOException, InterruptedException {
		return send(request(uri).header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form)), HttpResponse.BodyHandlers.ofString());
	}

	public static String postJson(final URI uri, final String json) throws IOException, InterruptedException {
		return send(request(uri).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json)), HttpResponse.BodyHandlers.ofString());
	}

	public static byte[] getBytes(final URI uri) throws IOException, InterruptedException {
		return send(request(uri).GET(), HttpResponse.BodyHandlers.ofByteArray());
	}

	/** Saves the response to {@code file}, replacing anything already there. */
	public static void download(final URI uri, final Path file) throws IOException, InterruptedException {
		// ofFile(file) alone doesn't truncate, which would leave the tail of a longer old file behind
		send(request(uri).GET(), HttpResponse.BodyHandlers.ofFile(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING));
	}

	private static <T> T send(final HttpRequest.Builder request, final HttpResponse.BodyHandler<T> handler) throws IOException, InterruptedException {
		HttpResponse<T> response = HTTP.send(request.build(), handler);
		if (response.statusCode() != 200) {
			throw new IOException("HTTP " + response.statusCode() + " for " + response.uri());
		}
		return response.body();
	}
}
