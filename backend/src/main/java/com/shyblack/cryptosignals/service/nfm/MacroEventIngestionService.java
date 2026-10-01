package com.shyblack.cryptosignals.service.nfm;

import com.shyblack.cryptosignals.dto.nfm.MacroEventRequest;
import com.shyblack.cryptosignals.entity.NewsEvent;
import com.shyblack.cryptosignals.entity.NewsEventAsset;
import com.shyblack.cryptosignals.entity.enums.NewsEventStage;
import com.shyblack.cryptosignals.entity.enums.NewsEventType;
import com.shyblack.cryptosignals.entity.enums.NewsImpact;
import com.shyblack.cryptosignals.entity.enums.NewsSourceTier;
import com.shyblack.cryptosignals.repository.NewsEventRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records scheduled macro / central-bank events (spec §4, §8, §36). These are
 * Tier-1, official, and always tradeable — the NFM engine still waits for the
 * market reaction before producing a directional signal.
 */
@Service
public class MacroEventIngestionService {

	private final NewsEventRepository eventRepository;
	private final NewsEventTypeClassifier classifier;

	public MacroEventIngestionService(NewsEventRepository eventRepository, NewsEventTypeClassifier classifier) {
		this.eventRepository = eventRepository;
		this.classifier = classifier;
	}

	@Transactional
	public NewsEvent record(MacroEventRequest request) {
		NewsEventType type = NewsEventType.parse(request.eventType());
		BigDecimal expected = request.expectedValue();
		BigDecimal actual = request.actualValue();
		BigDecimal surprise = (expected != null && actual != null) ? actual.subtract(expected) : null;

		NewsEvent event = new NewsEvent();
		event.setEventTime(request.eventTime() != null ? request.eventTime() : Instant.now());
		event.setSource(request.source() == null ? "official" : request.source());
		event.setSourceTier(NewsSourceTier.TIER_1);
		event.setEventCategory(type.category());
		event.setEventType(type);
		event.setEventStage(request.eventStage() == null ? NewsEventStage.OFFICIAL_CONFIRMATION : request.eventStage());
		event.setHeadline(request.headline());
		event.setSummary(request.summary());
		event.setExpectedValue(expected);
		event.setActualValue(actual);
		event.setInitialValue(actual);
		event.setRevisedValue(request.revisedValue());
		event.setReleaseTimestamp(event.getEventTime());
		event.setSurpriseValue(surprise);
		event.setSurpriseDirection(direction(expected, actual));
		event.setMarketInterpretation(request.marketInterpretation());
		event.setEventImpact(NewsImpact.HIGH);
		event.setTradeable(true);

		List<String> assets = request.assets() == null ? List.of() : request.assets();
		for (String symbol : assets) {
			if (symbol == null || symbol.isBlank()) {
				continue;
			}
			NewsEventAsset asset = new NewsEventAsset();
			asset.setSymbol(symbol.trim().toUpperCase());
			asset.setRelevanceLevel(NewsImpact.HIGH);
			event.addAsset(asset);
		}
		return eventRepository.save(event);
	}

	private static String direction(BigDecimal expected, BigDecimal actual) {
		if (expected == null || actual == null) {
			return "UNKNOWN";
		}
		int cmp = actual.compareTo(expected);
		if (cmp > 0) return "ABOVE_EXPECTATION";
		if (cmp < 0) return "BELOW_EXPECTATION";
		return "INLINE";
	}
}
