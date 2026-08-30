package com.shyblack.cryptosignals.controller;

import com.shyblack.cryptosignals.dto.portfolio.PortfolioResponse;
import com.shyblack.cryptosignals.service.PortfolioService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/portfolios")
@RequiredArgsConstructor
public class PortfolioController {

	private final PortfolioService portfolioService;

	@GetMapping
	public List<PortfolioResponse> list() {
		return portfolioService.findAll();
	}

	@GetMapping("/{id}")
	public PortfolioResponse getById(@PathVariable UUID id) {
		return portfolioService.findById(id);
	}
}
