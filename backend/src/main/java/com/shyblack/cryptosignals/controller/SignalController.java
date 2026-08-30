package com.shyblack.cryptosignals.controller;

import com.shyblack.cryptosignals.dto.signal.SignalResponse;
import com.shyblack.cryptosignals.service.SignalService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/signals")
@RequiredArgsConstructor
public class SignalController {

	private final SignalService signalService;

	@GetMapping
	public List<SignalResponse> list() {
		return signalService.findAll();
	}

	@GetMapping("/{id}")
	public SignalResponse getById(@PathVariable UUID id) {
		return signalService.findById(id);
	}
}
