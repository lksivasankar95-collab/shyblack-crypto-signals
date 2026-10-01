package com.shyblack.cryptosignals.service.backtest.research;

import com.shyblack.cryptosignals.entity.research.NfmValidationAttribution;
import com.shyblack.cryptosignals.repository.research.NfmValidationAttributionRepository;
import com.shyblack.cryptosignals.signal.nfm.NfmDecisionContext;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists immutable EVENT → SIGNAL → TRADE → OUTCOME attribution rows and reads
 * them back for analytics. Duplicate {@code (runId, signalId)} rows are refused
 * (never overwrite history). NULL/UNKNOWN values are preserved exactly.
 */
@Service
@RequiredArgsConstructor
public class NfmAttributionPersistenceService {

	private final NfmValidationAttributionRepository repository;

	@Transactional
	public int persistAll(UUID runId, List<NfmEventAttribution> attributions) {
		if (attributions == null || attributions.isEmpty()) {
			return 0;
		}
		int inserted = 0;
		for (NfmEventAttribution a : attributions) {
			UUID signalId = a.signalId();
			if (signalId == null || repository.existsByRunIdAndSignalId(runId, signalId)) {
				continue;
			}
			repository.save(toEntity(runId, a));
			inserted++;
		}
		return inserted;
	}

	@Transactional(readOnly = true)
	public List<NfmEventAttribution> attributions(UUID runId) {
		return repository.findByRunIdOrderByCreatedAtAsc(runId).stream()
				.map(NfmAttributionPersistenceService::toAttribution).toList();
	}

	@Transactional(readOnly = true)
	public long count(UUID runId) {
		return repository.countByRunId(runId);
	}

	private static NfmValidationAttribution toEntity(UUID runId, NfmEventAttribution a) {
		NfmValidationAttribution e = new NfmValidationAttribution();
		e.setRunId(runId);
		e.setSignalId(a.signalId());
		e.setEventIds(join(a.attributedEventIds()));
		e.setEventType(a.eventType());
		e.setEventStage(a.eventStage());
		e.setSourceTier(a.sourceTier());
		e.setEventTimestamp(a.eventTimestamp());
		e.setSymbol(a.symbol());
		e.setSignalDirection(a.signalDirection());
		e.setSignalScore(a.signalScore() == null ? null : a.signalScore().intValue());
		e.setSignalGrade(a.signalGrade());
		e.setSignalStatus(a.signalStatus());
		e.setExpected(a.expected());
		e.setActual(a.actual());
		e.setSurprise(a.surprise());
		e.setPriceReaction(a.priceReaction());
		e.setVolumeRatio(a.volumeRatio());
		e.setOiChange(a.oiChange());
		e.setFunding(a.funding());
		e.setLiquidation(a.liquidation());
		e.setMarketRegime(a.marketRegime());
		e.setTradeabilityState(a.decisionContext() == null ? null : a.decisionContext().tradeabilityState());
		e.setRejectReason(a.decisionContext() == null ? null : a.decisionContext().rejectReason());
		e.setEventAgeSeconds(a.decisionContext() == null ? null : a.decisionContext().eventAgeSeconds());
		e.setEntryPrice(a.tradeEntryPrice());
		e.setStopLoss(a.stopLoss());
		e.setTp1(a.tp1());
		e.setTp2(a.tp2());
		e.setTp3(a.tp3());
		e.setTp1Hit(a.tp1Hit());
		e.setTp2Hit(a.tp2Hit());
		e.setTp3Hit(a.tp3Hit());
		e.setSlHit(a.slHit());
		e.setGrossPnl(a.grossPnl());
		e.setFees(a.fees());
		e.setSlippage(a.slippage());
		e.setNetPnl(a.netPnl());
		e.setTradeOutcome(a.tradeOutcome());
		e.setAttributionStatus(a.attributionStatus());
		return e;
	}

	private static NfmEventAttribution toAttribution(NfmValidationAttribution e) {
		List<UUID> ids = parse(e.getEventIds());
		NfmDecisionContext ctx = new NfmDecisionContext(ids,
				e.getSignalScore(), e.getSignalGrade(), e.getEventType(), e.getEventStage(),
				e.getSourceTier(), e.getPriceReaction(), e.getVolumeRatio(), e.getOiChange(),
				e.getFunding(), e.getLiquidation(), e.getMarketRegime(), e.getTradeabilityState(),
				e.getRejectReason(), e.getEventAgeSeconds(), e.getExpected(), e.getActual(),
				e.getSurprise());
		return new NfmEventAttribution(e.getSymbol(),
				ids.isEmpty() ? null : ids.get(0), e.getEventType(), e.getEventStage(), e.getSourceTier(),
				e.getEventTimestamp(), List.copyOf(ids), e.getSignalDirection(),
				e.getSignalScore() == null ? null : java.math.BigDecimal.valueOf(e.getSignalScore()),
				e.getSignalGrade(), e.getSignalStatus(), e.getExpected(), e.getActual(), e.getSurprise(),
				e.getPriceReaction(), e.getVolumeRatio(), e.getOiChange(), e.getFunding(),
				e.getLiquidation(), e.getMarketRegime(), e.getEntryPrice(), null, e.getStopLoss(),
				e.getTp1(), e.getTp2(), e.getTp3(), e.getTp1Hit(), e.getTp2Hit(), e.getTp3Hit(),
				e.getSlHit(), e.getGrossPnl(), e.getFees(), e.getSlippage(), e.getNetPnl(),
				e.getTradeOutcome(), e.getAttributionStatus(), ctx, e.getSignalId());
	}

	private static String join(List<UUID> ids) {
		if (ids == null || ids.isEmpty()) {
			return null;
		}
		StringBuilder sb = new StringBuilder();
		for (UUID id : ids) {
			if (id == null) continue;
			if (sb.length() > 0) sb.append(',');
			sb.append(id);
		}
		return sb.length() == 0 ? null : sb.toString();
	}

	private static List<UUID> parse(String joined) {
		if (joined == null || joined.isBlank()) {
			return List.of();
		}
		List<UUID> out = new ArrayList<>();
		for (String part : joined.split(",")) {
			try {
				out.add(UUID.fromString(part.trim()));
			} catch (IllegalArgumentException ignored) {
				// skip malformed ids rather than fabricate
			}
		}
		return out;
	}
}
