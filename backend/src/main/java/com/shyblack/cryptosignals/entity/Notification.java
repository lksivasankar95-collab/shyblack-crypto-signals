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
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "notifications")
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
}
