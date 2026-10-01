package com.shyblack.cryptosignals.service;

import com.shyblack.cryptosignals.dto.signal.SignalResponse;
import com.shyblack.cryptosignals.dto.signal.SignalResponse.NfmContextResponse;
import com.shyblack.cryptosignals.entity.Signal;
import com.shyblack.cryptosignals.entity.SignalNfmContext;
import com.shyblack.cryptosignals.entity.enums.SignalStatus;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import com.shyblack.cryptosignals.exception.ResourceNotFoundException;
import com.shyblack.cryptosignals.repository.SignalNfmContextRepository;
import com.shyblack.cryptosignals.repository.SignalRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SignalService {

    private final SignalRepository signalRepository;
    private final SignalNfmContextRepository nfmContextRepository;

    @Transactional(readOnly = true)
    public List<SignalResponse> findAll() {
        return map(signalRepository.findAll());
    }

    @Transactional(readOnly = true)
    public List<SignalResponse> findByModeAndStatus(TradingMode mode, SignalStatus status) {
        List<Signal> signals = status != null
                ? signalRepository.findByTradingModeAndStatusIn(mode, List.of(status))
                : signalRepository.findByTradingModeAndStatusIn(mode, List.of(SignalStatus.values()));
        return map(signals);
    }

    @Transactional(readOnly = true)
    public SignalResponse findById(UUID id) {
        Signal signal = signalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Signal not found: " + id));
        return toResponse(signal,
                NfmContextResponse.from(nfmContextRepository.findBySignalId(id).orElse(null)));
    }

    /** Batch-loads NFM context to avoid N+1 when listing signals. */
    private List<SignalResponse> map(List<Signal> signals) {
        if (signals.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = signals.stream().map(Signal::getId).toList();
        Map<UUID, SignalNfmContext> contexts = nfmContextRepository.findBySignalIdIn(ids).stream()
                .collect(Collectors.toMap(c -> c.getSignal().getId(), Function.identity(), (a, b) -> a));
        return signals.stream()
                .map(s -> toResponse(s, NfmContextResponse.from(contexts.get(s.getId()))))
                .toList();
    }

    private static SignalResponse toResponse(Signal s, NfmContextResponse context) {
        return new SignalResponse(
                s.getId(),
                s.getSymbol(),
                s.getStatus(),
                s.getSide(),
                s.getConfidence(),
                s.getEntryPrice(),
                s.getTargetPrice(),
                s.getStopLoss(),
                s.getStrategy(),
                s.getStrategyWinRate(),
                s.getSuggestedRiskPercent(),
                s.getClosedAt(),
                s.getTechnicalSummary(),
                s.getDisclaimer(),
                s.getCreatedAt(),
                s.getScore(),
                s.getSignalGrade(),
                s.getEntryType(),
                s.getTradingMode(),
                s.getMarketRegime(),
                s.getTargetPrice2(),
                s.getTargetPrice3(),
                s.getRiskReward(),
                s.getStrategyId(),
                s.getStrategyVersion(),
                s.getSetupId(),
                context
        );
    }
}
