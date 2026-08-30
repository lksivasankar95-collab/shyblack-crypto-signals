package com.shyblack.cryptosignals.controller;

import com.shyblack.cryptosignals.dto.transaction.TransactionResponse;
import com.shyblack.cryptosignals.service.TransactionService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

	private final TransactionService transactionService;

	@GetMapping
	public List<TransactionResponse> list() {
		return transactionService.findAll();
	}

	@GetMapping("/{id}")
	public TransactionResponse getById(@PathVariable UUID id) {
		return transactionService.findById(id);
	}
}
