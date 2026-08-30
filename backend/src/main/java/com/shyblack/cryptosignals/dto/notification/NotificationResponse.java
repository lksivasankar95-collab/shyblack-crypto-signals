package com.shyblack.cryptosignals.dto.notification;

import com.shyblack.cryptosignals.entity.enums.NotificationCategory;
import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
		UUID id,
		UUID userId,
		NotificationCategory category,
		String title,
		String body,
		boolean read,
		Instant readAt,
		Instant createdAt
) {
}
