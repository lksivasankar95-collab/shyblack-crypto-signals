package com.shyblack.cryptosignals.repository;

import com.shyblack.cryptosignals.entity.NewsEvent;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NewsEventRepository extends JpaRepository<NewsEvent, UUID> {

	boolean existsByArticleId(UUID articleId);

	boolean existsByExternalEventId(String externalEventId);

	Optional<NewsEvent> findByArticleId(UUID articleId);

	List<NewsEvent> findTop50ByTradeableTrueOrderByEventTimeDesc();

	List<NewsEvent> findByTradeableTrueAndEventTimeAfterOrderByEventTimeDesc(Instant since);

	List<NewsEvent> findByEventTimeAfterOrderByEventTimeDesc(Instant since);

	/** Efficient bounded range (start inclusive, end exclusive), ascending. */
	List<NewsEvent> findByEventTimeGreaterThanEqualAndEventTimeLessThanOrderByEventTimeAsc(
			Instant start, Instant end);

	List<NewsEvent> findByTradeableTrueAndEventTimeGreaterThanEqualAndEventTimeLessThanOrderByEventTimeAsc(
			Instant start, Instant end);

	/** Paged variant for large research windows. */
	Page<NewsEvent> findByEventTimeGreaterThanEqualAndEventTimeLessThan(
			Instant start, Instant end, Pageable pageable);
}
