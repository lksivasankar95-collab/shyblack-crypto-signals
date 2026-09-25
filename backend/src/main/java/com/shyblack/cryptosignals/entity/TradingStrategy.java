package com.shyblack.cryptosignals.entity;

import com.shyblack.cryptosignals.entity.enums.StrategyStatus;
import com.shyblack.cryptosignals.entity.enums.StrategyType;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor
@Entity
@Table(name = "trading_strategies")
public class TradingStrategy extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TradingMode tradingMode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StrategyType strategyType;

    @Column
    private UUID ownerId; // null for SYSTEM strategies

    @Column(nullable = false)
    private int version = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StrategyStatus status = StrategyStatus.ACTIVE;

    @Column(nullable = false)
    private boolean deletable = true;

    @Column(nullable = false)
    private boolean editable = true;

    @Column(columnDefinition = "TEXT")
    private String configJson;
}
