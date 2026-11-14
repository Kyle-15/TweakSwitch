package com.tweakswitch;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.nio.channels.UnresolvedAddressException;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** Turns failed web requests into messages a player can act on, and decides how long to wait before trying again. */
public final class WebErrors {
	/** The longest TweakSwitch waits before retrying, however long the site asks for. */
	static final long MAX_RETRY_DELAY_MS = 20_000;

	private WebErrors() {
	}

	/** A reply other than "200 OK". */
	public static final class HttpStatusException extends IOException {
		private final int status;
		private final String host;

		public HttpStatusException(final int status, final URI uri) {
			super("HTTP " + status + " for " + uri);
			this.status = status;
			this.host = uri.getHost() != null ? uri.getHost() : uri.toString();
		}

		public int status() {
			return this.status;
		}

		public String host() {
			return this.host;
		}
	}

	/** Whether asking again a little later is likely to work: rate limits and the gateway errors busy sites give. */
	public static boolean retryable(final int status) {
		return status == 429 || status == 502 || status == 503 || status == 504;
	}

	/**
	 * How long to wait before attempt {@code attempt + 1}. Sites say how long with "Retry-After" (seconds) or, on Modrinth,
	 * "X-Ratelimit-Reset" (seconds until the limit resets); without either it's a few seconds, longer each time.
	 */
	public static long retryDelayMs(final int status, final Optional<String> retryAfter, final Optional<String> rateLimitReset, final int attempt) {
		Long asked = seconds(retryAfter);
		if (asked == null && status == 429) {
			asked = seconds(rateLimitReset);
		}
		long fallback = (status == 429 ? 5_000L : 1_000L) * Math.max(1, attempt);
		long delay = asked != null ? asked * 1000 + 250 : fallback;
		return Math.max(250, Math.min(MAX_RETRY_DELAY_MS, delay));
	}

	private static @Nullable Long seconds(final Optional<String> header) {
		try {
			return header.isPresent() ? Math.max(0L, (long) Math.ceil(Double.parseDouble(header.get().trim()))) : null;
		} catch (NumberFormatException e) {
			// "Retry-After" can also be a date; too rare to be worth parsing, the fallback delay is used
			return null;
		}
	}

	/** Which message fits the failure: one of the "tweakswitch.error.*" translation keys. */
	public static String messageKey(final @Nullable Throwable error) {
		for (Throwable cause = error; cause != null; cause = cause.getCause()) {
			if (cause instanceof HttpStatusException http) {
				if (http.status() == 429) {
					return "tweakswitch.error.rateLimited";
				}
				return http.status() == 404 ? "tweakswitch.error.notFound" : http.status() >= 500 ? "tweakswitch.error.siteDown" : "tweakswitch.error.refused";
			}
			if (cause instanceof UnknownHostException || cause instanceof ConnectException || cause instanceof UnresolvedAddressException) {
				return "tweakswitch.error.offline";
			}
			if (cause instanceof HttpTimeoutException || cause instanceof java.net.SocketTimeoutException) {
				return "tweakswitch.error.timeout";
			}
			if (cause == cause.getCause()) {
				break;
			}
		}
		return "tweakswitch.error.other";
	}

	/** "No internet connection. Check it and try again." and the like, with the site or the raw error filled in. */
	public static Component describe(final @Nullable Throwable error) {
		String key = messageKey(error);
		String detail = "";
		for (Throwable cause = error; cause != null; cause = cause.getCause()) {
			if (cause instanceof HttpStatusException http) {
				detail = http.host() + " (HTTP " + http.status() + ")";
				break;
			}
			if (cause == cause.getCause()) {
				break;
			}
		}
		if (detail.isEmpty() && error != null) {
			detail = error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
		}
		return Component.translatable(key, detail);
	}
}
