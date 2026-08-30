package com.shyblack.cryptosignals.service;

import com.shyblack.cryptosignals.dto.transaction.TransactionResponse;
import com.shyblack.cryptosignals.repository.TransactionRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TransactionService {

	private final TransactionRepository transactionRepository;

	public List<TransactionResponse> findAll() {
		return List.of();
	}

	public TransactionResponse findById(UUID id) {
		throw new UnsupportedOperationException("Transaction lookup is not implemented yet");
	}
}
