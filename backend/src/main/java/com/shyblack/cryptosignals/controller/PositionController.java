package com.shyblack.cryptosignals.controller;

import com.shyblack.cryptosignals.dto.position.PositionResponse;
import com.shyblack.cryptosignals.service.PositionService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/positions")
@RequiredArgsConstructor
public class PositionController {

	private final PositionService positionService;

	@GetMapping
	public List<PositionResponse> list() {
		return positionService.findAll();
	}

	@GetMapping("/{id}")
	public PositionResponse getById(@PathVariable UUID id) {
		return positionService.findById(id);
	}
}
