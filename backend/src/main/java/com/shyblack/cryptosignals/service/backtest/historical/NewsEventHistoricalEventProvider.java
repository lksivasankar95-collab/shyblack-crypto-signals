package com.shyblack.cryptosignals.service.backtest.historical;

import com.shyblack.cryptosignals.entity.NewsEvent;
import com.shyblack.cryptosignals.entity.NewsEventAsset;
import com.shyblack.cryptosignals.repository.NewsEventRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adapts persisted {@link NewsEvent} rows into backtest {@link HistoricalEvent}s.
 *
 * <p>Note: the platform ingests news live only, so historical coverage is
 * limited to what has already been stored. This provider does not fabricate
 * history — an empty result means no events were recorded in the range.</p>
 */
@Component
public class NewsEventHistoricalEventProvider implements HistoricalEventProvider {

	private final NewsEventRepository eventRepository;

	public NewsEventHistoricalEventProvider(NewsEventRepository eventRepository) {
		this.eventRepository = eventRepository;
	}

	@Override
	@Transactional(readOnly = true)
	public List<HistoricalEvent> load(String symbol, Instant start, Instant end) {
		String base = baseOf(symbol);
		List<HistoricalEvent> result = new ArrayList<>();
		// Bounded range pushed into SQL (start inclusive, end exclusive, ascending).
		for (NewsEvent event : eventRepository
				.findByEventTimeGreaterThanEqualAndEventTimeLessThanOrderByEventTimeAsc(start, end)) {
			Instant time = event.getEventTime();
			if (time == null) {
				continue;
			}
			NewsEventAsset match = matchAsset(event, base);
			if (match == null) {
				continue;
			}
			result.add(new HistoricalEvent(
					event.getId(), time, symbol, event.getEventType(), event.getEventCategory(),
					event.getEventStage(), event.getSourceTier(),
					match.getRelevanceLevel() != null ? match.getRelevanceLevel() : event.getEventImpact(),
					event.getExpectedValue(), event.getActualValue(), event.getSurpriseValue(),
					event.getSource(), event.getHeadline()));
		}
		result.sort(Comparator.comparing(HistoricalEvent::time));
		return result;
	}

	private static NewsEventAsset matchAsset(NewsEvent event, String base) {
		if (event.getAssets() == null) {
			return null;
		}
		for (NewsEventAsset asset : event.getAssets()) {
			if (asset.getSymbol() != null && asset.getSymbol().equalsIgnoreCase(base)) {
				return asset;
			}
		}
		return null;
	}

	private static String baseOf(String symbol) {
		if (symbol == null) {
			return "";
		}
		String upper = symbol.toUpperCase(Locale.ROOT);
		return upper.endsWith("USDT") ? upper.substring(0, upper.length() - 4) : upper;
	}
}
