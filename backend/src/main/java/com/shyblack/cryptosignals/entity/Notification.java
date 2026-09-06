package com.shyblack.cryptosignals.entity;

import com.shyblack.cryptosignals.entity.enums.NotificationCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "notifications",
	uniqueConstraints = @UniqueConstraint(name = "uk_notifications_user_signal", columnNames = {"user_id", "signal_id"}))
public class Notification extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private NotificationCategory category;

	@Column(nullable = false)
	private String title;

	@Column(nullable = false, length = 2000)
	private String body;

	@Column(nullable = false)
	private boolean read;

	private Instant readAt;
    
	// Link to a signal if this notification was generated for a signal
	private UUID signalId;
    
	// e.g. SPOT
	private String marketType;
}
