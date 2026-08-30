package com.shyblack.cryptosignals.controller;

import com.shyblack.cryptosignals.dto.notification.NotificationResponse;
import com.shyblack.cryptosignals.service.NotificationService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

	private final NotificationService notificationService;

	@GetMapping
	public List<NotificationResponse> list() {
		return notificationService.findAll();
	}

	@GetMapping("/{id}")
	public NotificationResponse getById(@PathVariable UUID id) {
		return notificationService.findById(id);
	}
}
