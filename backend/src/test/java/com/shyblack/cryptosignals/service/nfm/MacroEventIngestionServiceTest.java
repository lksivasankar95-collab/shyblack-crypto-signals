package com.shyblack.cryptosignals.service.nfm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.shyblack.cryptosignals.dto.nfm.MacroEventRequest;
import com.shyblack.cryptosignals.entity.NewsEvent;
import com.shyblack.cryptosignals.entity.enums.NewsEventStage;
import com.shyblack.cryptosignals.entity.enums.NewsEventType;
import com.shyblack.cryptosignals.entity.enums.NewsSourceTier;
import com.shyblack.cryptosignals.repository.NewsEventRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MacroEventIngestionServiceTest {

	private final NewsEventRepository repository = mock(NewsEventRepository.class);
	private MacroEventIngestionService service;

	@BeforeEach
	void setUp() {
		service = new MacroEventIngestionService(repository, new NewsEventTypeClassifier());
		when(repository.save(any(NewsEvent.class))).thenAnswer(inv -> inv.getArgument(0));
	}

	@Test
	void computesSurpriseAndMarksTradeable() {
		MacroEventRequest req = new MacroEventRequest(
				"CPI", "Federal Reserve", Instant.parse("2026-10-01T12:30:00Z"),
				new BigDecimal("2.8"), new BigDecimal("3.1"), null,
				"US CPI hotter than expected", null, null, null, List.of("btc", "eth"));

		NewsEvent event = service.record(req);

		assertThat(event.getEventType()).isEqualTo(NewsEventType.CPI);
		assertThat(event.getSourceTier()).isEqualTo(NewsSourceTier.TIER_1);
		assertThat(event.getEventStage()).isEqualTo(NewsEventStage.OFFICIAL_CONFIRMATION);
		assertThat(event.getSurpriseValue()).isEqualByComparingTo("0.3");
		assertThat(event.getSurpriseDirection()).isEqualTo("ABOVE_EXPECTATION");
		assertThat(event.isTradeable()).isTrue();
		assertThat(event.getAssets()).hasSize(2);
		assertThat(event.getAssets().get(0).getSymbol()).isEqualTo("BTC");
	}

	@Test
	void inlineWhenActualEqualsExpected() {
		MacroEventRequest req = new MacroEventRequest(
				"FOMC", "Federal Reserve", Instant.now(),
				new BigDecimal("4.50"), new BigDecimal("4.50"), null,
				null, null, null, null, List.of());
		NewsEvent event = service.record(req);
		assertThat(event.getSurpriseDirection()).isEqualTo("INLINE");
		assertThat(event.getAssets()).isEmpty();
	}
}
