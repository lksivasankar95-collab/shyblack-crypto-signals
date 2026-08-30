package com.shyblack.cryptosignals.service;

import com.shyblack.cryptosignals.dto.portfolio.PortfolioResponse;
import com.shyblack.cryptosignals.repository.PortfolioRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PortfolioService {

	private final PortfolioRepository portfolioRepository;

	public List<PortfolioResponse> findAll() {
		return List.of();
	}

	public PortfolioResponse findById(UUID id) {
		throw new UnsupportedOperationException("Portfolio lookup is not implemented yet");
	}
}
