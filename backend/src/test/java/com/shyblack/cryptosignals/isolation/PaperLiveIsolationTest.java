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
 * Structural regression test — enforces the paper/live isolation invariant.
 *
 * The paper trading package must NEVER depend on any live-trading package,
 * exchange adapter, or exchange credential decryption. If someone
 * accidentally wires paper execution through the live pipeline (or vice-
 * versa), this test fails at compile-check time rather than in production.
 *
 * We assert two things:
 *  1. No file under {@code service/paper/**} imports anything from
 *     {@code service.live}, {@code exchange}, or references
 *     {@code ExchangeTradingAdapter}.
 *  2. No file under {@code service/live/**} imports anything from
 *     {@code service.paper}.
 */
class PaperLiveIsolationTest {

	private static final Path BACKEND_SRC = Paths.get("src", "main", "java",
			"com", "shyblack", "cryptosignals");

	@Test
	void paperPackageMustNotImportLiveOrExchange() throws IOException {
		Path paper = BACKEND_SRC.resolve("service").resolve("paper");
		assertThat(paper.toFile()).exists();
		try (Stream<Path> files = Files.walk(paper)) {
			List<String> offenders = files
					.filter(Files::isRegularFile)
					.filter(p -> p.toString().endsWith(".java"))
					.filter(p -> containsAny(p,
							"com.shyblack.cryptosignals.service.live",
							"com.shyblack.cryptosignals.exchange.",
							"ExchangeTradingAdapter"))
					.map(Path::toString)
					.toList();
			assertThat(offenders)
					.as("Paper trading must not import live-trading or exchange types")
					.isEmpty();
		}
	}

	@Test
	void livePackageMustNotImportPaper() throws IOException {
		Path live = BACKEND_SRC.resolve("service").resolve("live");
		assertThat(live.toFile()).exists();
		try (Stream<Path> files = Files.walk(live)) {
			List<String> offenders = files
					.filter(Files::isRegularFile)
					.filter(p -> p.toString().endsWith(".java"))
					.filter(p -> containsAny(p, "com.shyblack.cryptosignals.service.paper"))
					.map(Path::toString)
					.toList();
			assertThat(offenders)
					.as("Live trading must not import paper-trading types")
					.isEmpty();
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
