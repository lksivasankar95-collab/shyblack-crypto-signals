package com.shyblack.cryptosignals.repository;

import com.shyblack.cryptosignals.entity.NewsArticle;
import com.shyblack.cryptosignals.entity.enums.NewsProcessingStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NewsArticleRepository
		extends JpaRepository<NewsArticle, UUID>, JpaSpecificationExecutor<NewsArticle> {

	boolean existsByCanonicalHash(String canonicalHash);

	boolean existsBySourceUrl(String sourceUrl);

	boolean existsByExternalNewsIdAndSourceName(String externalNewsId, String sourceName);

	@Query("""
			select a from NewsArticle a
			join a.assets asset
			where asset.symbol = :symbol
			  and a.publishedAt >= :since
			  and a.processingStatus = :status
			order by a.publishedAt desc
			""")
	List<NewsArticle> findProcessedForAssetSince(
			@Param("symbol") String symbol,
			@Param("since") Instant since,
			@Param("status") NewsProcessingStatus status);

	@Query("select distinct a.sourceName from NewsArticle a order by a.sourceName asc")
	List<String> findDistinctSourceNames();

	java.util.Optional<NewsArticle> findTopByOrderByFetchedAtDesc();
}