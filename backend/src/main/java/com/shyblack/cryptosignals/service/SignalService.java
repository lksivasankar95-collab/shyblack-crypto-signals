package com.shyblack.cryptosignals.service;

import com.shyblack.cryptosignals.dto.signal.SignalResponse;
import com.shyblack.cryptosignals.repository.SignalRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SignalService {

	private final SignalRepository signalRepository;

	public List<SignalResponse> findAll() {
		return List.of();
	}

	public SignalResponse findById(UUID id) {
		throw new UnsupportedOperationException("Signal lookup is not implemented yet");
	}
}
