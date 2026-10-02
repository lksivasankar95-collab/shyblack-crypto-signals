package com.shyblack.cryptosignals.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The Portfolio API is driven by the account mode configured in Settings.
 *
 * <p>This is the contract that replaced a server-side default: with no {@code mode} parameter,
 * every Portfolio endpoint must serve the account the user actually chose. A live-selected user
 * must never be served the simulated account, which is the failure mode a hard-coded PAPER
 * default would introduce.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
		"app.live-trading.mode=MOCK",
		"app.futures-trading.mode=MOCK",
		"spring.task.scheduling.enabled=false"
})
class PortfolioSettingsDrivenModeApiTest {

	private static final String PASSWORD = "Str0ng!Passw0rd";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	private String token;

	@BeforeEach
	void signUpAndLogin() throws Exception {
		// The in-memory database uses DB_CLOSE_DELAY=-1, so it lives for the whole JVM and rows
		// survive between test classes. A nano-time suffix keeps every signup genuinely unique
		// rather than relying on a counter that another class could also have used.
		String email = "portfolio-settings-" + System.nanoTime() + "@example.test";
		mockMvc.perform(post("/api/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"%s","password":"%1$s","fullName":"Settings Driven Test"}
								""".formatted(email, PASSWORD)))
				.andExpect(status().isCreated());

		MvcResult login = mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"%s","password":"%1$s"}
								""".formatted(email, PASSWORD)))
				.andExpect(status().isOk())
				.andReturn();
		token = objectMapper.readTree(login.getResponse().getContentAsString()).get("accessToken").asText();
	}

	private JsonNode readJson(String path) throws Exception {
		MvcResult result = mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString());
	}

	/** Switches the account mode exactly as the Settings screen does. */
	private void setAccountModeInSettings(String accountType) throws Exception {
		mockMvc.perform(patch("/api/v1/settings")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"accountType\":\"%s\"}".formatted(accountType)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accountType").value(accountType));
	}

	// ------------------------------------------------ A. settings propagation

	@Nested
	@DisplayName("A. Settings is the single source of the account mode")
	class SettingsPropagation {

		@Test
		@DisplayName("a fresh account defaults to the simulated account because Settings says so")
		void defaultComesFromSettings() throws Exception {
			JsonNode body = readJson("/api/v1/portfolio");

			assertThat(body.get("accountMode").asText())
					.as("the echoed mode must match the account recorded in Settings")
					.isEqualTo("PAPER");
		}

		@Test
		@DisplayName("choosing the live account in Settings makes every Portfolio endpoint serve LIVE")
		void liveSelectionPropagatesToEveryEndpoint() throws Exception {
			setAccountModeInSettings("LIVE");

			// Overview, per-scope account, positions, holdings, open orders, closed positions,
			// history, transactions, funding and sync status must all follow the same account.
			assertThat(readJson("/api/v1/portfolio").get("accountMode").asText()).isEqualTo("LIVE");
			assertThat(readJson("/api/v1/portfolio/SPOT").get("accountMode").asText()).isEqualTo("LIVE");
			assertThat(readJson("/api/v1/portfolio/SPOT/positions").get("accountMode").asText()).isEqualTo("LIVE");
			assertThat(readJson("/api/v1/portfolio/SPOT/holdings").get("accountMode").asText()).isEqualTo("LIVE");
			assertThat(readJson("/api/v1/portfolio/SPOT/open-orders").get("accountMode").asText()).isEqualTo("LIVE");
			assertThat(readJson("/api/v1/portfolio/FUTURES/open-orders").get("accountMode").asText())
					.isEqualTo("LIVE");
			assertThat(readJson("/api/v1/portfolio/FUTURES/closed-positions").get("accountMode").asText())
					.isEqualTo("LIVE");
			assertThat(readJson("/api/v1/portfolio/FUTURES/history?type=ORDER").get("accountMode").asText())
					.isEqualTo("LIVE");
			assertThat(readJson("/api/v1/portfolio/FUTURES/transaction-history").get("accountMode").asText())
					.isEqualTo("LIVE");
			assertThat(readJson("/api/v1/portfolio/FUTURES/funding-fees").get("accountMode").asText())
					.isEqualTo("LIVE");
			assertThat(readJson("/api/v1/portfolio/SPOT/sync-status").get("accountMode").asText()).isEqualTo("LIVE");
		}

		@Test
		@DisplayName("switching back to the simulated account in Settings is reflected immediately")
		void switchingBackToPaperIsReflected() throws Exception {
			setAccountModeInSettings("LIVE");
			assertThat(readJson("/api/v1/portfolio/SPOT").get("accountMode").asText()).isEqualTo("LIVE");

			setAccountModeInSettings("PAPER");
			assertThat(readJson("/api/v1/portfolio/SPOT").get("accountMode").asText()).isEqualTo("PAPER");
		}

		@Test
		@DisplayName("a live-selected user is never served the simulated account")
		void liveSelectionNeverFallsBackToPaper() throws Exception {
			setAccountModeInSettings("LIVE");

			// No Binance account is connected, so the live scope must report that it is not
			// connected rather than quietly returning paper figures.
			JsonNode spot = readJson("/api/v1/portfolio/SPOT");
			assertThat(spot.get("accountMode").asText()).isEqualTo("LIVE");
			assertThat(spot.get("availability").asText())
					.as("an unreachable live account must say so, never substitute a simulated one")
					.isEqualTo("NOT_CONNECTED");
			assertThat(spot.get("equity").isNull())
					.as("no figure may be invented for an unreachable account")
					.isTrue();
		}

		@Test
		@DisplayName("an explicit mode still wins, so a diagnostic read can address a scope deliberately")
		void explicitModeStillWins() throws Exception {
			setAccountModeInSettings("LIVE");

			assertThat(readJson("/api/v1/portfolio/SPOT?mode=PAPER").get("accountMode").asText())
					.as("an explicit parameter is still honoured")
					.isEqualTo("PAPER");
		}
	}

	// ------------------------------------------------ B. new endpoint contract

	@Nested
	@DisplayName("B. the new Binance-style endpoints report their capability honestly")
	class NewEndpoints {

		@Test
		@DisplayName("spot closed positions report that spot has no position lifecycle")
		void spotClosedPositionsAreUnsupported() throws Exception {
			mockMvc.perform(get("/api/v1/portfolio/SPOT/closed-positions?mode=LIVE")
							.header("Authorization", "Bearer " + token))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.availability").value("UNSUPPORTED"))
					.andExpect(jsonPath("$.positions").isEmpty())
					.andExpect(jsonPath("$.statusMessage").isNotEmpty());
		}

		@Test
		@DisplayName("options is unsupported across every new endpoint")
		void optionsIsUnsupportedEverywhere() throws Exception {
			for (String path : new String[] {
					"/api/v1/portfolio/OPTIONS/open-orders?mode=LIVE",
					"/api/v1/portfolio/OPTIONS/closed-positions?mode=LIVE",
					"/api/v1/portfolio/OPTIONS/transaction-history?mode=LIVE",
					"/api/v1/portfolio/OPTIONS/funding-fees?mode=LIVE"
			}) {
				mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
						.andExpect(status().isOk())
						.andExpect(jsonPath("$.availability").value("UNSUPPORTED"));
			}
		}

		@Test
		@DisplayName("MAIN refuses the new endpoints rather than merging spot and futures")
		void mainRefusesTheNewEndpoints() throws Exception {
			for (String path : new String[] {
					"/api/v1/portfolio/MAIN/open-orders?mode=LIVE",
					"/api/v1/portfolio/MAIN/closed-positions?mode=LIVE",
					"/api/v1/portfolio/MAIN/transaction-history?mode=LIVE",
					"/api/v1/portfolio/MAIN/funding-fees?mode=LIVE"
			}) {
				MvcResult result = mockMvc.perform(
								get(path).header("Authorization", "Bearer " + token))
						.andExpect(status().isOk())
						.andReturn();
				JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
				String listField = path.contains("open-orders") ? "orders"
						: path.contains("closed-positions") ? "positions"
						: "entries";
				assertThat(body.get(listField).isEmpty())
						.as("%s must never merge two market scopes", path)
						.isTrue();
			}
		}

		@Test
		@DisplayName("paper open orders report that a simulated account works no order book")
		void paperOpenOrdersAreUnsupported() throws Exception {
			mockMvc.perform(get("/api/v1/portfolio/FUTURES/open-orders?mode=PAPER")
							.header("Authorization", "Bearer " + token))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.availability").value("UNSUPPORTED"))
					.andExpect(jsonPath("$.orders").isEmpty())
					.andExpect(jsonPath("$.source").value("LOCAL_PAPER"));
		}

		@Test
		@DisplayName("spot transaction history reports that no income endpoint exists")
		void spotTransactionHistoryIsUnavailable() throws Exception {
			mockMvc.perform(get("/api/v1/portfolio/SPOT/transaction-history?mode=LIVE")
							.header("Authorization", "Bearer " + token))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.availability").value("UNSUPPORTED"))
					.andExpect(jsonPath("$.entries").isEmpty())
					.andExpect(jsonPath("$.statusMessage").value(
							org.hamcrest.Matchers.containsString("Not available")));
		}

		@Test
		@DisplayName("an unknown filter value is rejected instead of silently matching nothing")
		void unknownFilterValueIsRejected() throws Exception {
			mockMvc.perform(get("/api/v1/portfolio/FUTURES/history?type=ORDER&status=MADE_UP")
							.header("Authorization", "Bearer " + token))
					.andExpect(status().isBadRequest());
		}

		@Test
		@DisplayName("an unbounded history window is rejected rather than attempted")
		void unboundedWindowIsRejected() throws Exception {
			mockMvc.perform(get("/api/v1/portfolio/FUTURES/transaction-history"
							+ "?from=2000-01-01T00:00:00Z&to=2030-01-01T00:00:00Z")
							.header("Authorization", "Bearer " + token))
					.andExpect(status().isBadRequest());
		}

		@Test
		@DisplayName("the new endpoints require authentication")
		void newEndpointsRequireAuthentication() throws Exception {
			for (String path : new String[] {
					"/api/v1/portfolio/SPOT/open-orders",
					"/api/v1/portfolio/FUTURES/closed-positions",
					"/api/v1/portfolio/FUTURES/transaction-history",
					"/api/v1/portfolio/FUTURES/funding-fees"
			}) {
				mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
			}
		}
	}

	// ---------------------------------------------------- C. no credential leak

	@Nested
	@DisplayName("C. credentials never reach a Portfolio response")
	class NoCredentialExposure {

		@Test
		@DisplayName("no new endpoint echoes a credential, listen key or signature")
		void newEndpointsExposeNoCredentials() throws Exception {
			for (String path : new String[] {
					"/api/v1/portfolio/SPOT/open-orders?mode=LIVE",
					"/api/v1/portfolio/FUTURES/open-orders?mode=LIVE",
					"/api/v1/portfolio/FUTURES/closed-positions?mode=LIVE",
					"/api/v1/portfolio/FUTURES/transaction-history?mode=LIVE",
					"/api/v1/portfolio/FUTURES/funding-fees?mode=LIVE"
			}) {
				String body = mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
						.andExpect(status().isOk())
						.andReturn().getResponse().getContentAsString();

				for (String forbidden : new String[] {
						"apiKey", "apiSecret", "listenKey", "X-MBX-APIKEY", "signature", "enc:"
				}) {
					assertThat(body)
							.as("%s must not expose %s", path, forbidden)
							.doesNotContain(forbidden);
				}
			}
		}
	}
}