package com.shyblack.cryptosignals.service;

import com.shyblack.cryptosignals.dto.position.PositionResponse;
import com.shyblack.cryptosignals.repository.PositionRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PositionService {

	private final PositionRepository positionRepository;

	public List<PositionResponse> findAll() {
		return List.of();
	}

	public PositionResponse findById(UUID id) {
		throw new UnsupportedOperationException("Position lookup is not implemented yet");
	}
}
