package com.shyblack.cryptosignals.entity;

import com.shyblack.cryptosignals.entity.enums.PositionLifecycleEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Immutable audit trail for a Position. One row per transition (CREATED,
 * OPENED, SL_HIT, TP_HIT, MANUAL_CLOSE, CLOSED, REJECTED). No updates allowed
 * once written — this table is the ledger of truth for lifecycle history.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
		name = "position_lifecycle_events",
		indexes = {
				@Index(name = "ix_pl_events_position", columnList = "position_id"),
				@Index(name = "ix_pl_events_type", columnList = "event_type")
		}
)
public class PositionLifecycleEvent extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "position_id", nullable = false)
	private Position position;

	@Enumerated(EnumType.STRING)
	@Column(name = "event_type", nullable = false)
	private PositionLifecycleEventType eventType;

	@Column(precision = 19, scale = 8)
	private BigDecimal price;

	@Column(precision = 19, scale = 8)
	private BigDecimal pnl;

	@Column(length = 500)
	private String message;
}
