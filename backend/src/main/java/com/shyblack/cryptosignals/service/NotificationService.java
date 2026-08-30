package com.shyblack.cryptosignals.service;

import com.shyblack.cryptosignals.dto.notification.NotificationResponse;
import com.shyblack.cryptosignals.repository.NotificationRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationService {

	private final NotificationRepository notificationRepository;

	public List<NotificationResponse> findAll() {
		return List.of();
	}

	public NotificationResponse findById(UUID id) {
		throw new UnsupportedOperationException("Notification lookup is not implemented yet");
	}
}
