package com.shyblack.cryptosignals.repository;

import com.shyblack.cryptosignals.entity.PortfolioExchangeBalance;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioExchangeBalanceRepository
		extends JpaRepository<PortfolioExchangeBalance, UUID> {

	List<PortfolioExchangeBalance> findByUserAndExchange(User user, ExchangeName exchange);

	Optional<PortfolioExchangeBalance> findByUserAndExchangeAndAsset(
			User user, ExchangeName exchange, String asset);

	void deleteByUserAndExchange(User user, ExchangeName exchange);
}