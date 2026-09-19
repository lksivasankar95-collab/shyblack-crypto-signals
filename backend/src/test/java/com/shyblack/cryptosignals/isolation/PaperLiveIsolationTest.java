package com.shyblack.cryptosignals.isolation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Structural regression tests — enforce paper / live (spot) / futures
 * isolation invariants at build time.
 *
 * Rules:
 *   - Paper trading (service.paper.*) may not import any exchange adapter
 *     or reach into service.live.* / service.futures.*.
 *   - Live SPOT trading (service.live.*) may not import service.paper.*
 *     nor service.futures.*.
 *   - Live FUTURES trading (service.futures.*) may not import service.paper.*
 *     nor service.live.*.
 *
 * If someone accidentally routes a paper signal through the exchange
 * adapter — or lets futures share Spot's execution service — this test
 * fails immediately.
 */
class PaperLiveIsolationTest {

	private static final Path BACKEND_SRC = Paths.get("src", "main", "java",
			"com", "shyblack", "cryptosignals");

	private static final String P_PAPER = "com.shyblack.cryptosignals.service.paper";
	private static final String P_LIVE = "com.shyblack.cryptosignals.service.live";
	private static final String P_FUTURES = "com.shyblack.cryptosignals.service.futures";
	private static final String P_EXCHANGE = "com.shyblack.cryptosignals.exchange.";

	@Test
	void paperPackageMustNotImportLiveOrFuturesOrExchange() throws IOException {
		List<String> offenders = scan("paper",
				P_LIVE, P_FUTURES, P_EXCHANGE,
				"ExchangeTradingAdapter", "FuturesExchangeAdapter");
		assertThat(offenders)
				.as("Paper trading must not import live/futures/exchange")
				.isEmpty();
	}

	@Test
	void livePackageMustNotImportPaperOrFutures() throws IOException {
		List<String> offenders = scan("live", P_PAPER, P_FUTURES);
		assertThat(offenders)
				.as("Live SPOT must not import paper or futures")
				.isEmpty();
	}

	@Test
	void futuresPackageMustNotImportPaperOrLive() throws IOException {
		List<String> offenders = scan("futures", P_PAPER, P_LIVE);
		assertThat(offenders)
				.as("Live FUTURES must not import paper or live-spot")
				.isEmpty();
	}

	private static List<String> scan(String subpackage, String... forbidden) throws IOException {
		Path dir = BACKEND_SRC.resolve("service").resolve(subpackage);
		assertThat(dir.toFile()).as("expected " + subpackage + " service package").exists();
		try (Stream<Path> files = Files.walk(dir)) {
			return files.filter(Files::isRegularFile)
					.filter(p -> p.toString().endsWith(".java"))
					.filter(p -> containsAny(p, forbidden))
					.map(Path::toString)
					.toList();
		}
	}

	private static boolean containsAny(Path file, String... needles) {
		try {
			String content = Files.readString(file);
			for (String n : needles) {
				if (content.contains(n)) return true;
			}
			return false;
		} catch (IOException ex) {
			throw new RuntimeException("Failed reading " + file, ex);
		}
	}
}
