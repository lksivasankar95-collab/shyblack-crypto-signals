package com.shyblack.cryptosignals.entity;

import com.shyblack.cryptosignals.entity.enums.LiveOrderLifecycleEventType;
import com.shyblack.cryptosignals.entity.enums.LiveOrderStatus;
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
 * Immutable audit log for live-order state transitions. One row per state
 * change or fill. Never modify after write; this is the ledger.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
		name = "live_order_lifecycle_events",
		indexes = {
				@Index(name = "ix_lo_events_order", columnList = "order_id"),
				@Index(name = "ix_lo_events_type", columnList = "event_type")
		}
)
public class LiveOrderLifecycleEvent extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_id", nullable = false)
	private LiveOrder order;

	@Enumerated(EnumType.STRING)
	@Column(name = "event_type", nullable = false)
	private LiveOrderLifecycleEventType eventType;

	@Enumerated(EnumType.STRING)
	@Column(name = "status_snapshot")
	private LiveOrderStatus statusSnapshot;

	@Column(precision = 19, scale = 8)
	private BigDecimal quantity;

	@Column(precision = 19, scale = 8)
	private BigDecimal price;

	@Column(precision = 19, scale = 8)
	private BigDecimal fee;

	@Column(length = 20)
	private String feeAsset;

	@Column(length = 500)
	private String message;
}
