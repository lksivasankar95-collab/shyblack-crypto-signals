package com.shyblack.cryptosignals.service.news;

import com.shyblack.cryptosignals.dto.news.NewsAssetResponse;
import com.shyblack.cryptosignals.dto.news.NewsDetailResponse;
import com.shyblack.cryptosignals.dto.news.NewsResponse;
import com.shyblack.cryptosignals.entity.NewsArticle;
import com.shyblack.cryptosignals.entity.NewsAsset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Maps news entities to response DTOs. Database access for asset batching lives
 * in the query/intelligence services; this class stays a pure mapper.
 */
@Component
public class NewsResponseMapper {

	public NewsResponse toResponse(NewsArticle article, List<NewsAsset> assets) {
		return new NewsResponse(
				article.getId(),
				article.getSourceName(),
				article.getSourceUrl(),
				article.getTitle(),
				article.getSummary(),
				article.getImageUrl(),
				article.getAuthor(),
				article.getPublishedAt(),
				article.getCategory(),
				article.getSentiment(),
				article.getSentimentScore(),
				article.getImpactLevel(),
				article.getImpactScore(),
				article.getNewsScore(),
				article.getConfidenceScore(),
				assets == null ? List.of() : toAssetResponses(assets));
	}

	public NewsDetailResponse toDetail(NewsArticle article, List<NewsAsset> assets) {
		return new NewsDetailResponse(
				article.getId(),
				article.getSourceName(),
				article.getSourceUrl(),
				article.getTitle(),
				article.getSummary(),
				article.getContent(),
				article.getImageUrl(),
				article.getAuthor(),
				article.getPublishedAt(),
				article.getFetchedAt(),
				article.getCategory(),
				article.getSentiment(),
				article.getSentimentScore(),
				article.getImpactLevel(),
				article.getImpactScore(),
				article.getNewsScore(),
				article.getConfidenceScore(),
				article.getProcessingStatus(),
				assets == null ? List.of() : toAssetResponses(assets));
	}

	public List<NewsAssetResponse> toAssetResponses(List<NewsAsset> assets) {
		return assets.stream().map(this::toAssetResponse).toList();
	}

	public NewsAssetResponse toAssetResponse(NewsAsset asset) {
		return new NewsAssetResponse(
				asset.getSymbol(),
				asset.getName(),
				asset.getRelevanceScore(),
				asset.getRelationshipType());
	}

	/** Groups asset rows by their article id. */
	public Map<UUID, List<NewsAsset>> groupByArticle(List<NewsAsset> rows) {
		return rows.stream().collect(Collectors.groupingBy(
				row -> row.getArticle().getId(),
				Collectors.toList()));
	}

	public List<NewsResponse> toResponses(List<NewsArticle> articles, Map<UUID, List<NewsAsset>> assetsByArticle) {
		return articles.stream()
				.map(article -> toResponse(article, assetsByArticle.getOrDefault(article.getId(), List.of())))
				.toList();
	}
}