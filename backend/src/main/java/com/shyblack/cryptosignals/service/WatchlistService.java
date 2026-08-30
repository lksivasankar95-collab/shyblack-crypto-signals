package com.shyblack.cryptosignals.service;

import com.shyblack.cryptosignals.dto.watchlist.WatchlistResponse;
import com.shyblack.cryptosignals.repository.WatchlistRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WatchlistService {

	private final WatchlistRepository watchlistRepository;

	public List<WatchlistResponse> findAll() {
		return List.of();
	}

	public WatchlistResponse findById(UUID id) {
		throw new UnsupportedOperationException("Watchlist lookup is not implemented yet");
	}
}
