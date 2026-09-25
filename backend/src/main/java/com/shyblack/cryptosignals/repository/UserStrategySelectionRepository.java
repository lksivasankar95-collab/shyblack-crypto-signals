package com.shyblack.cryptosignals.repository;

import com.shyblack.cryptosignals.entity.UserStrategySelection;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserStrategySelectionRepository extends JpaRepository<UserStrategySelection, UUID> {
    Optional<UserStrategySelection> findByUserIdAndTradingMode(UUID userId, TradingMode mode);
    List<UserStrategySelection> findByTradingModeOrderByUpdatedAtDesc(TradingMode mode);
}
