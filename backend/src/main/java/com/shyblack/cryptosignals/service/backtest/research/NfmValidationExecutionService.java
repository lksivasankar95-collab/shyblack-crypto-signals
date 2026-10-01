package com.shyblack.cryptosignals.service.backtest.research;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shyblack.cryptosignals.entity.enums.BacktestExecutionModel;
import com.shyblack.cryptosignals.entity.enums.BacktestSameCandlePolicy;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import com.shyblack.cryptosignals.entity.research.MarketCandle;
import com.shyblack.cryptosignals.exception.BadRequestException;
import com.shyblack.cryptosignals.repository.research.MarketCandleRepository;
import com.shyblack.cryptosignals.service.backtest.engine.BacktestConfig;
import com.shyblack.cryptosignals.service.backtest.historical.HistoricalCandle;
import com.shyblack.cryptosignals.service.backtest.historical.HistoricalEvent;
import com.shyblack.cryptosignals.service.backtest.historical.HistoricalEventProvider;
import com.shyblack.cryptosignals.service.backtest.strategy.BacktestStrategyRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Operational single entry point that turns an {@link NfmValidationExecutionRequest}
 * into real (or blocked) NFM validation runs using ONLY the existing engines,
 * providers, and immutable persistence. It never fabricates candles, events,
 * derivatives, or results, and never touches the JVM lifecycle.
 *
 * <p>Execution is synchronous and per-symbol. Repeated identical requests resolve
 * to the same deterministic run id; the immutable persistence layer rejects the
 * duplicate write and the response flags it ({@code duplicate=true}).</p>
 */
@Service
@RequiredArgsConstructor
public class NfmValidationExecutionService {

	public static final String STRATEGY_ID = "NFM_FUTURES";
	public static final String DEFAULT_TIMEFRAME = "1h";
	public static final String DEFAULT_MARKET_DATASET = "NFM_RESEARCH_2023_09_2026_09_V1";
	public static final String DEFAULT_EVENT_DATASET = "NFM_EVENTS_2023_09_2026_09_V1";
	public static final String DEFAULT_DERIVATIVES_DATASET = "NFM_DERIVATIVES_2023_09_2026_09_V1";
	private static final int DEFAULT_WINDOW_DAYS = 90;
	private static final int DEFAULT_STEP_DAYS = 30;

	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final Map<String, Duration> TIMEFRAMES = Map.of(
			"1m", Duration.ofMinutes(1),
			"5m", Duration.ofMinutes(5),
			"15m", Duration.ofMinutes(15),
			"1h", Duration.ofHours(1),
			"4h", Duration.ofHours(4));

	private final MarketCandleRepository candleRepository;
	private final HistoricalEventProvider eventProvider;
	private final BacktestStrategyRegistry strategyRegistry;
	private final NfmValidationResultPersistenceService persistenceService;
	private final NfmAttributionPersistenceService attributionPersistenceService;

	public NfmValidationExecutionResponse execute(NfmValidationExecutionRequest request) {
		Plan plan = plan(request);
		List<NfmValidationExecutionResponse.SymbolOutcome> outcomes = new ArrayList<>();
		for (String symbol : plan.symbols) {
			BacktestConfig config = config(plan, symbol);
			List<HistoricalCandle> candles = loadCandles(symbol, plan.timeframe, plan.start, plan.end);
			List<HistoricalEvent> events = eventProvider.load(symbol, plan.start, plan.end);

			NfmValidationResult result = NfmValidationRunner.run(plan.runType, config,
					paramsJson -> strategyRegistry.create(STRATEGY_ID, paramsJson),
					candles, events, plan.marketDatasetVersion, plan.eventDatasetVersion,
					plan.derivativesDatasetVersion, plan.windowDays, plan.stepDays, plan.variants);

			boolean duplicate = false;
			try {
				persistenceService.persist(result, configurationJson(config));
			} catch (IllegalStateException alreadyExists) {
				duplicate = true;
			}
			attributionPersistenceService.persistAll(result.runId(), result.attributions());
			outcomes.add(new NfmValidationExecutionResponse.SymbolOutcome(symbol, result.runId(),
					result.executionStatus().name(), result.dataQuality(), result.configurationHash(),
					result.tradeCount(), duplicate));
		}
		return new NfmValidationExecutionResponse(plan.runType.name(), outcomes);
	}

	private List<HistoricalCandle> loadCandles(String symbol, String timeframe, Instant start, Instant end) {
		List<MarketCandle> rows = candleRepository
				.findBySymbolAndTimeframeAndOpenTimeBetweenOrderByOpenTimeAsc(symbol, timeframe, start, end);
		List<HistoricalCandle> out = new ArrayList<>(rows.size());
		for (MarketCandle c : rows) {
			HistoricalCandle h = toCandle(c, timeframe);
			if (h != null) {
				out.add(h);
			}
		}
		return out;
	}

	private static HistoricalCandle toCandle(MarketCandle c, String timeframe) {
		if (c == null || c.getOpenTime() == null) {
			return null;
		}
		Instant closeTime = c.getCloseTime();
		if (closeTime == null) {
			Duration d = TIMEFRAMES.get(timeframe == null ? "" : timeframe.toLowerCase(Locale.ROOT));
			if (d == null) {
				return null;
			}
			closeTime = c.getOpenTime().plus(d);
		}
		return new HistoricalCandle(c.getOpenTime(), c.getOpen(), c.getHigh(), c.getLow(), c.getClose(),
				c.getVolume(), closeTime);
	}

	private BacktestConfig config(Plan p, String symbol) {
		return new BacktestConfig(STRATEGY_ID, symbol, p.timeframe, TradingMode.FUTURES, p.start, p.end,
				p.initialCapital, p.riskPerTradePct, p.feePct, p.slippagePct, p.leverage, p.executionModel,
				p.sameCandlePolicy, p.strategyParams);
	}

	private static String configurationJson(BacktestConfig c) {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("strategyId", c.strategyId());
		map.put("symbol", c.symbol());
		map.put("timeframe", c.timeframe());
		map.put("tradingMode", c.tradingMode().name());
		map.put("startDate", c.startDate().toString());
		map.put("endDate", c.endDate().toString());
		map.put("initialCapital", c.initialCapital().toPlainString());
		map.put("riskPerTradePct", c.riskPerTradePct().toPlainString());
		map.put("feePct", c.feePct().toPlainString());
		map.put("slippagePct", c.slippagePct().toPlainString());
		map.put("leverage", c.leverage());
		map.put("executionModel", c.executionModel().name());
		map.put("sameCandlePolicy", c.sameCandlePolicy().name());
		map.put("strategyParams", c.strategyParams());
		try {
			return MAPPER.writeValueAsString(map);
		} catch (Exception ex) {
			return map.toString();
		}
	}

	private Plan plan(NfmValidationExecutionRequest r) {
		if (r == null) {
			throw new BadRequestException("request required");
		}
		NfmValidationRunType runType = parseRunType(r.runType());
		if (r.symbols() == null || r.symbols().isEmpty()) {
			throw new BadRequestException("symbols required");
		}
		if (r.start() == null || r.end() == null || !r.start().isBefore(r.end())) {
			throw new BadRequestException("invalid date range");
		}
		List<String> symbols = r.symbols().stream().filter(Objects::nonNull)
				.map(s -> s.trim().toUpperCase(Locale.ROOT)).filter(s -> !s.isEmpty()).distinct().toList();
		if (symbols.isEmpty()) {
			throw new BadRequestException("symbols required");
		}
		List<SensitivityRunner.Variant> variants = new ArrayList<>();
		if (runType == NfmValidationRunType.SENSITIVITY) {
			if (r.variants() == null || r.variants().isEmpty()) {
				throw new BadRequestException("variants required for SENSITIVITY");
			}
			for (NfmValidationExecutionRequest.SensitivityVariantRequest v : r.variants()) {
				if (v == null || v.label() == null || v.label().isBlank()) {
					throw new BadRequestException("each sensitivity variant needs a label");
				}
				variants.add(new SensitivityRunner.Variant(v.label(), v.paramsJson()));
			}
		}
		return new Plan(runType, symbols, r.start(), r.end(),
				orDefault(r.timeframe(), DEFAULT_TIMEFRAME),
				orDefault(r.initialCapital(), new BigDecimal("10000")),
				orDefault(r.riskPerTradePct(), new BigDecimal("2")),
				orDefault(r.feePct(), new BigDecimal("0.05")),
				orDefault(r.slippagePct(), new BigDecimal("0.03")),
				r.leverage() == null ? 1 : r.leverage(),
				r.executionModel() == null ? BacktestExecutionModel.NEXT_CANDLE_OPEN : r.executionModel(),
				r.sameCandlePolicy() == null ? BacktestSameCandlePolicy.SL_FIRST : r.sameCandlePolicy(),
				orDefault(r.marketDatasetVersion(), DEFAULT_MARKET_DATASET),
				orDefault(r.eventDatasetVersion(), DEFAULT_EVENT_DATASET),
				orDefault(r.derivativesDatasetVersion(), DEFAULT_DERIVATIVES_DATASET),
				r.walkForwardWindowDays() == null ? DEFAULT_WINDOW_DAYS : r.walkForwardWindowDays(),
				r.walkForwardStepDays() == null ? DEFAULT_STEP_DAYS : r.walkForwardStepDays(),
				List.copyOf(variants), null);
	}

	public static NfmValidationRunType parseRunType(String raw) {
		if (raw == null || raw.isBlank()) {
			throw new BadRequestException("runType required");
		}
		try {
			return NfmValidationRunType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ex) {
			throw new BadRequestException("invalid run type: " + raw);
		}
	}

	private static <T> T orDefault(T value, T fallback) {
		return value == null ? fallback : value;
	}

	private static String orDefault(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value;
	}

	private record Plan(NfmValidationRunType runType, List<String> symbols, Instant start, Instant end,
			String timeframe, BigDecimal initialCapital, BigDecimal riskPerTradePct, BigDecimal feePct,
			BigDecimal slippagePct, int leverage, BacktestExecutionModel executionModel,
			BacktestSameCandlePolicy sameCandlePolicy, String marketDatasetVersion, String eventDatasetVersion,
			String derivativesDatasetVersion, int windowDays, int stepDays,
			List<SensitivityRunner.Variant> variants, String strategyParams) {
	}
}
