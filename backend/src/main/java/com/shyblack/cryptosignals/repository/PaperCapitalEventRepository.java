package com.shyblack.cryptosignals.repository;

import com.shyblack.cryptosignals.entity.PaperCapitalEvent;
import com.shyblack.cryptosignals.entity.Portfolio;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaperCapitalEventRepository extends JpaRepository<PaperCapitalEvent, UUID> {

	/** Newest first; the caller bounds the window with {@link Pageable}. */
	List<PaperCapitalEvent> findByPortfolioOrderByCreatedAtDesc(Portfolio portfolio, Pageable pageable);

	long countByPortfolio(Portfolio portfolio);
}
