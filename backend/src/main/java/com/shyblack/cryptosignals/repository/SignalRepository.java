package com.shyblack.cryptosignals.repository;

import com.shyblack.cryptosignals.entity.Signal;
import com.shyblack.cryptosignals.entity.enums.SignalStatus;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SignalRepository extends JpaRepository<Signal, UUID> {

    List<Signal> findByStatus(SignalStatus status);

    List<Signal> findBySymbolAndTradingModeAndStatusIn(
            String symbol, TradingMode mode, List<SignalStatus> statuses);

    List<Signal> findByTradingModeAndStatusIn(
            TradingMode mode, List<SignalStatus> statuses);
}
