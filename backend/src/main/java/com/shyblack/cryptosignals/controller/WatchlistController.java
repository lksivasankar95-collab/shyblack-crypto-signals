package com.shyblack.cryptosignals.controller;

import com.shyblack.cryptosignals.dto.watchlist.WatchlistResponse;
import com.shyblack.cryptosignals.service.WatchlistService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/watchlist")
@RequiredArgsConstructor
public class WatchlistController {

	private final WatchlistService watchlistService;

	@GetMapping
	public List<WatchlistResponse> list() {
		return watchlistService.findAll();
	}

	@GetMapping("/{id}")
	public WatchlistResponse getById(@PathVariable UUID id) {
		return watchlistService.findById(id);
	}
}
