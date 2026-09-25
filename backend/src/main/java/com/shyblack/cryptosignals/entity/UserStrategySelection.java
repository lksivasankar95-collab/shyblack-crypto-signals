package com.shyblack.cryptosignals.entity;

import com.shyblack.cryptosignals.entity.enums.TradingMode;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor
@Entity
@Table(name = "user_strategy_selections",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_user_strategy_mode",
           columnNames = {"user_id", "trading_mode"}))
public class UserStrategySelection extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "trading_mode", nullable = false)
    private TradingMode tradingMode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "strategy_id", nullable = false)
    private TradingStrategy strategy;
}
