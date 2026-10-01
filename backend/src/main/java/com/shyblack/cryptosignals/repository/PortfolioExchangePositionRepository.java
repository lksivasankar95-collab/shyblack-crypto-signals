package com.shyblack.cryptosignals.repository;

import com.shyblack.cryptosignals.entity.PortfolioExchangePosition;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioExchangePositionRepository
		extends JpaRepository<PortfolioExchangePosition, UUID> {

	List<PortfolioExchangePosition> findByUserAndExchangeOrderBySymbolAsc(
			User user, ExchangeName exchange);

	void deleteByUserAndExchange(User user, ExchangeName exchange);
}