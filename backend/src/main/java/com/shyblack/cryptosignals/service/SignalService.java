package com.shyblack.cryptosignals.service;

import com.shyblack.cryptosignals.dto.signal.SignalResponse;
import com.shyblack.cryptosignals.entity.Signal;
import com.shyblack.cryptosignals.entity.enums.SignalStatus;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import com.shyblack.cryptosignals.exception.ResourceNotFoundException;
import com.shyblack.cryptosignals.repository.SignalRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SignalService {

    private final SignalRepository signalRepository;

    @Transactional(readOnly = true)
    public List<SignalResponse> findAll() {
        return signalRepository.findAll().stream()
                .map(SignalService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SignalResponse> findByModeAndStatus(TradingMode mode, SignalStatus status) {
        if (status != null) {
            return signalRepository.findByTradingModeAndStatusIn(mode, List.of(status)).stream()
                    .map(SignalService::toResponse)
                    .toList();
        }
        return signalRepository.findByTradingModeAndStatusIn(
                mode, List.of(SignalStatus.values())).stream()
                .map(SignalService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SignalResponse findById(UUID id) {
        Signal signal = signalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Signal not found: " + id));
        return toResponse(signal);
    }

    static SignalResponse toResponse(Signal s) {
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
                s.getRiskReward()
        );
    }
}
