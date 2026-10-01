package com.shyblack.cryptosignals.repository;

import com.shyblack.cryptosignals.entity.NewsEvent;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NewsEventRepository extends JpaRepository<NewsEvent, UUID> {

	boolean existsByArticleId(UUID articleId);

	Optional<NewsEvent> findByArticleId(UUID articleId);

	List<NewsEvent> findTop50ByTradeableTrueOrderByEventTimeDesc();

	List<NewsEvent> findByTradeableTrueAndEventTimeAfterOrderByEventTimeDesc(Instant since);

	List<NewsEvent> findByEventTimeAfterOrderByEventTimeDesc(Instant since);
}
