package com.shyblack.cryptosignals.entity;

import com.shyblack.cryptosignals.entity.enums.FuturesLifecycleEventType;
import com.shyblack.cryptosignals.entity.enums.FuturesOrderStatus;
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
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
		name = "futures_order_lifecycle_events",
		indexes = {
				@Index(name = "ix_fo_events_order", columnList = "order_id"),
				@Index(name = "ix_fo_events_position", columnList = "position_id"),
				@Index(name = "ix_fo_events_type", columnList = "event_type")
		})
public class FuturesOrderLifecycleEvent extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "order_id")
	private FuturesOrder order;

	/** Optional back-reference when the event belongs to a position rather than an order. */
	@Column(name = "position_id")
	private UUID positionId;

	@Enumerated(EnumType.STRING)
	@Column(name = "event_type", nullable = false)
	private FuturesLifecycleEventType eventType;

	@Enumerated(EnumType.STRING)
	@Column(name = "status_snapshot")
	private FuturesOrderStatus statusSnapshot;

	@Column(precision = 19, scale = 8) private BigDecimal quantity;
	@Column(precision = 19, scale = 8) private BigDecimal price;
	@Column(precision = 19, scale = 8) private BigDecimal fee;
	@Column(length = 20) private String feeAsset;
	@Column(length = 500) private String message;
}
