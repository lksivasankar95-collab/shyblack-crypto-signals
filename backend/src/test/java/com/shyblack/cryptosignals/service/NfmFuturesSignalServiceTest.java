package com.shyblack.cryptosignals.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shyblack.cryptosignals.dto.market.KlineResponse;
import com.shyblack.cryptosignals.dto.strategy.NfmFuturesConfig;
import com.shyblack.cryptosignals.entity.NewsEvent;
import com.shyblack.cryptosignals.entity.NewsEventAsset;
import com.shyblack.cryptosignals.entity.Signal;
import com.shyblack.cryptosignals.entity.SignalNfmContext;
import com.shyblack.cryptosignals.entity.TradingStrategy;
import com.shyblack.cryptosignals.entity.enums.MarketRegime;
import com.shyblack.cryptosignals.entity.enums.NewsEventCategory;
import com.shyblack.cryptosignals.entity.enums.NewsEventStage;
import com.shyblack.cryptosignals.entity.enums.NewsEventType;
import com.shyblack.cryptosignals.entity.enums.NewsImpact;
import com.shyblack.cryptosignals.entity.enums.NewsSourceTier;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.entity.enums.SignalStatus;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import com.shyblack.cryptosignals.market.BinanceFuturesRestClient;
import com.shyblack.cryptosignals.market.DerivativesSnapshot;
import com.shyblack.cryptosignals.market.FuturesDerivativesService;
import com.shyblack.cryptosignals.market.MarketBook;
import com.shyblack.cryptosignals.market.MarketTicker;
import com.shyblack.cryptosignals.market.MarketTickerStore;
import com.shyblack.cryptosignals.repository.NewsEventRepository;
import com.shyblack.cryptosignals.repository.SignalNfmContextRepository;
import com.shyblack.cryptosignals.repository.SignalRepository;
import com.shyblack.cryptosignals.service.strategy.StrategyResolver;
import com.shyblack.cryptosignals.signal.FuturesSignalEngine;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

/**
 * End-to-end NFM service path: a deterministic NewsEvent flows through the
 * analyzer, score, dedup/cooldown, SL/TP, Signal persistence and
 * SignalGeneratedEvent — with no live market or exchange dependency.
 */
class NfmFuturesSignalServiceTest {

	private static final long BASE = 1_700_000_000_000L;
	private static final long FIVE_MIN = 300_000L;

	private final NewsEventRepository eventRepository = mock(NewsEventRepository.class);
	private final BinanceFuturesRestClient futuresRestClient = mock(BinanceFuturesRestClient.class);
	private final FuturesDerivativesService derivativesService = mock(FuturesDerivativesService.class);
	private final FuturesSignalEngine futuresSignalEngine = mock(FuturesSignalEngine.class);
	private final MarketBook marketBook = mock(MarketBook.class);
	private final SignalRepository signalRepository = mock(SignalRepository.class);
	private final SignalNfmContextRepository nfmContextRepository = mock(SignalNfmContextRepository.class);
	private final StrategyResolver strategyResolver = mock(StrategyResolver.class);
	private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

	private NfmFuturesSignalService service;
	private TradingStrategy strategy;
	private NewsEvent event;

	@BeforeEach
	void setUp() {
		service = new NfmFuturesSignalService(eventRepository, futuresRestClient, derivativesService,
				futuresSignalEngine, marketBook, signalRepository, nfmContextRepository,
				strategyResolver, eventPublisher);

		strategy = new TradingStrategy();
		strategy.setId(UUID.randomUUID());
		strategy.setName("News Flow Momentum Futures");
		strategy.setVersion(1);
		strategy.setTradingMode(TradingMode.FUTURES);
		strategy.setEngineKey("NFM_FUTURES");
		when(strategyResolver.parseNfmFuturesConfig(strategy)).thenReturn(NfmFuturesConfig.defaults());

		event = buildEvent();
		when(eventRepository.findByTradeableTrueAndEventTimeAfterOrderByEventTimeDesc(any()))
				.thenReturn(List.of(event));
		when(marketBook.futuresTickers()).thenReturn(futuresStore());
		when(futuresSignalEngine.detectMarketRegime()).thenReturn(MarketRegime.BULLISH);
		when(derivativesService.snapshot(anyString())).thenReturn(DerivativesSnapshot.unavailable());
		when(signalRepository.save(any(Signal.class))).thenAnswer(inv -> inv.getArgument(0));
	}

	@Test
	void bullishEvent_persistsLongSignal_persistsContext_andPublishesEvent() {
		when(futuresRestClient.klines(anyString(), anyString(), anyInt())).thenReturn(candles(100, 105));

		int generated = service.runCycle(strategy);

		assertThat(generated).isEqualTo(1);

		ArgumentCaptor<Signal> signalCaptor = ArgumentCaptor.forClass(Signal.class);
		verify(signalRepository).save(signalCaptor.capture());
		Signal saved = signalCaptor.getValue();
		assertThat(saved.getTradingMode()).isEqualTo(TradingMode.FUTURES);
		assertThat(saved.getSide()).isEqualTo(PositionSide.LONG);
		assertThat(saved.getStopLoss()).isLessThan(saved.getEntryPrice());
		assertThat(saved.getTargetPrice()).isGreaterThan(saved.getEntryPrice());
		assertThat(saved.getStrategyId()).isEqualTo(strategy.getId());
		assertThat(saved.getSetupId()).isNotBlank();
		assertThat(saved.getStatus()).isIn(SignalStatus.ACTIVE, SignalStatus.PENDING);

		ArgumentCaptor<SignalNfmContext> ctxCaptor = ArgumentCaptor.forClass(SignalNfmContext.class);
		verify(nfmContextRepository).save(ctxCaptor.capture());
		assertThat(ctxCaptor.getValue().getEventType()).isEqualTo("BTC_ETF");
		assertThat(ctxCaptor.getValue().getConfigVersion()).isEqualTo("NFM_FUTURES_V1");
		// Missing derivatives must not be interpreted as zero in the persisted context.
		assertThat(ctxCaptor.getValue().getFundingState()).isEqualTo("UNKNOWN");
		assertThat(ctxCaptor.getValue().getLiquidationState()).isEqualTo("UNKNOWN");
		assertThat(ctxCaptor.getValue().getOpenInterestChangePct()).isNull();

		verify(eventPublisher).publishEvent(any(SignalGeneratedEvent.class));
	}

	@Test
	void insufficientReaction_doesNotPersistSignal() {
		when(futuresRestClient.klines(anyString(), anyString(), anyInt())).thenReturn(candles(100, 100.05));

		int generated = service.runCycle(strategy);

		assertThat(generated).isZero();
		verify(signalRepository, never()).save(any());
		verify(eventPublisher, never()).publishEvent(any(SignalGeneratedEvent.class));
	}

	@Test
	void duplicateSetup_isNotPersistedTwice() {
		when(futuresRestClient.klines(anyString(), anyString(), anyInt())).thenReturn(candles(100, 105));
		when(signalRepository.existsBySymbolAndTradingModeAndSetupId(anyString(), any(), anyString()))
				.thenReturn(true);

		int generated = service.runCycle(strategy);

		assertThat(generated).isZero();
		verify(signalRepository, never()).save(any());
	}

	@Test
	void cooldownWindow_skipsBeforeFetchingData() {
		Signal recent = new Signal();
		recent.setId(UUID.randomUUID());
		recent.setCreatedAt(Instant.now());
		when(signalRepository.findBySymbolAndTradingModeAndStatusIn(anyString(), any(), any()))
				.thenReturn(List.of(recent));

		int generated = service.runCycle(strategy);

		assertThat(generated).isZero();
		verify(signalRepository, never()).save(any());
		verify(futuresRestClient, never()).klines(anyString(), anyString(), anyInt());
	}

	@Test
	void eventOlderThanWindow_notSelected_butNoCrash() {
		// Service relies on the repository's time filter; an empty result is a no-op.
		when(eventRepository.findByTradeableTrueAndEventTimeAfterOrderByEventTimeDesc(any()))
				.thenReturn(List.of());

		int generated = service.runCycle(strategy);

		assertThat(generated).isZero();
		verify(signalRepository, never()).save(any());
	}

	// ── fixtures ────────────────────────────────────────────────────────────

	private static NewsEvent buildEvent() {
		NewsEvent e = new NewsEvent();
		e.setId(UUID.randomUUID());
		e.setEventTime(Instant.ofEpochMilli(BASE + 30L * FIVE_MIN));
		e.setSource("reuters");
		e.setSourceTier(NewsSourceTier.TIER_1);
		e.setEventCategory(NewsEventCategory.CRYPTO_STRUCTURAL);
		e.setEventType(NewsEventType.BTC_ETF);
		e.setEventStage(NewsEventStage.APPROVAL);
		e.setEventImpact(NewsImpact.CRITICAL);
		e.setEventConfidence(90);
		e.setHeadline("Spot Bitcoin ETF approved");
		e.setTradeable(true);
		NewsEventAsset asset = new NewsEventAsset();
		asset.setSymbol("BTC");
		asset.setName("Bitcoin");
		asset.setRelevanceLevel(NewsImpact.CRITICAL);
		e.addAsset(asset);
		return e;
	}

	private static MarketTickerStore futuresStore() {
		MarketTickerStore store = new MarketTickerStore(TradingMode.FUTURES);
		BigDecimal price = BigDecimal.valueOf(100);
		store.upsert(new MarketTicker("BTCUSDT", "Bitcoin", price, BigDecimal.ZERO, BigDecimal.ZERO,
				BigDecimal.valueOf(10_000_000), price, price, Instant.now()));
		return store;
	}

	/** 30 flat candles then 10 candles moving to {@code post}, with a volume spike. */
	private static List<KlineResponse> candles(double flat, double post) {
		List<KlineResponse> list = new ArrayList<>();
		for (int i = 0; i < 30; i++) {
			list.add(kline(i, flat, 100));
		}
		for (int i = 0; i < 10; i++) {
			list.add(kline(30 + i, flat + (post - flat) * (i + 1) / 10.0, 300));
		}
		return list;
	}

	private static KlineResponse kline(int index, double price, double volume) {
		long t = BASE + (long) index * FIVE_MIN;
		BigDecimal p = BigDecimal.valueOf(price).setScale(8, RoundingMode.HALF_UP);
		return new KlineResponse(t, p, p, p, p, BigDecimal.valueOf(volume), t + FIVE_MIN);
	}
}
