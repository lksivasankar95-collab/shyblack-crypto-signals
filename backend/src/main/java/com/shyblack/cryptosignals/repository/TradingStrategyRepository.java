package com.shyblack.cryptosignals.repository;

import com.shyblack.cryptosignals.entity.TradingStrategy;
import com.shyblack.cryptosignals.entity.enums.StrategyType;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TradingStrategyRepository extends JpaRepository<TradingStrategy, UUID> {
    List<TradingStrategy> findByTradingModeAndStrategyType(TradingMode mode, StrategyType type);
    List<TradingStrategy> findByTradingModeAndOwnerIdAndStrategyType(TradingMode mode, UUID ownerId, StrategyType type);
    List<TradingStrategy> findByTradingModeIn(List<TradingMode> modes);
    Optional<TradingStrategy> findByNameAndTradingModeAndStrategyType(String name, TradingMode mode, StrategyType type);
    boolean existsByNameAndTradingModeAndStrategyType(String name, TradingMode mode, StrategyType type);
}
