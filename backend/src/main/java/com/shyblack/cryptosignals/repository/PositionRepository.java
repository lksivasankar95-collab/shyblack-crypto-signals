package com.shyblack.cryptosignals.repository;

import com.shyblack.cryptosignals.entity.Portfolio;
import com.shyblack.cryptosignals.entity.Position;
import com.shyblack.cryptosignals.entity.enums.PositionStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PositionRepository extends JpaRepository<Position, UUID> {

	List<Position> findByPortfolio(Portfolio portfolio);

	List<Position> findByPortfolioAndStatus(Portfolio portfolio, PositionStatus status);
}
