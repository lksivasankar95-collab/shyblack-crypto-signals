package com.shyblack.cryptosignals.repository;

import com.shyblack.cryptosignals.entity.ExchangeCredential;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExchangeCredentialRepository extends JpaRepository<ExchangeCredential, UUID> {

	List<ExchangeCredential> findByUser_Id(UUID userId);

	Optional<ExchangeCredential> findByUser_IdAndExchange(UUID userId, ExchangeName exchange);

	Optional<ExchangeCredential> findByIdAndUser_Id(UUID id, UUID userId);

	long countByUser_Id(UUID userId);
}