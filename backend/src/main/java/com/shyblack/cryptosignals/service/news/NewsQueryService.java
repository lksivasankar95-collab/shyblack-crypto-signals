package com.shyblack.cryptosignals.service.news;

import com.shyblack.cryptosignals.config.NewsProperties;
import com.shyblack.cryptosignals.dto.news.NewsDetailResponse;
import com.shyblack.cryptosignals.dto.news.NewsMetaResponse;
import com.shyblack.cryptosignals.dto.news.NewsResponse;
import com.shyblack.cryptosignals.dto.news.PageResponse;
import com.shyblack.cryptosignals.entity.NewsArticle;
import com.shyblack.cryptosignals.entity.NewsAsset;
import com.shyblack.cryptosignals.entity.enums.NewsCategory;
import com.shyblack.cryptosignals.entity.enums.NewsImpact;
import com.shyblack.cryptosignals.entity.enums.NewsSentiment;
import com.shyblack.cryptosignals.exception.InvalidNewsFilterException;
import com.shyblack.cryptosignals.exception.NewsNotFoundException;
import com.shyblack.cryptosignals.repository.NewsArticleRepository;
import com.shyblack.cryptosignals.repository.NewsAssetRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read paths for news: paginated multi-filter queries, per-asset views,
 * detail, meta and source listings.
 */
@Service
public class NewsQueryService {

	private static final Set<String> SORT_WHITELIST = Set.of(
			"publishedAt", "createdAt", "newsScore", "title", "sourceName");
	private static final int MAX_SIZE = 100;
	private static final int MAX_Q_LENGTH = 120;

	private final NewsArticleRepository repository;
	private final NewsAssetRepository assetRepository;
	private final NewsResponseMapper mapper;
	private final NewsProperties properties;

	public NewsQueryService(
			NewsArticleRepository repository,
			NewsAssetRepository assetRepository,
			NewsResponseMapper mapper,
			NewsProperties properties
	) {
		this.repository = repository;
		this.assetRepository = assetRepository;
		this.mapper = mapper;
		this.properties = properties;
	}

	@Transactional(readOnly = true)
	public PageResponse<NewsResponse> search(
			NewsCategory category,
			NewsSentiment sentiment,
			NewsImpact impact,
			String source,
			String q,
			Instant from,
			Instant to,
			int page,
			int size,
			String sort,
			String direction
	) {
		Pageable pageable = pageable(page, size, sort, direction);
		Specification<NewsArticle> spec = filters(category, sentiment, impact, clean(source), clean(q), from, to);
		Page<NewsArticle> result = repository.findAll(spec, pageable);
		return toPage(result);
	}

	@Transactional(readOnly = true)
	public PageResponse<NewsResponse> assetNews(
			String symbol,
			NewsCategory category,
			NewsSentiment sentiment,
			NewsImpact impact,
			Instant from,
			Instant to,
			int page,
			int size,
			String sort,
			String direction
	) {
		Pageable pageable = pageable(page, size, sort, direction);
		String normalizedSymbol = normalizeSymbol(symbol);
		Specification<NewsArticle> assetSpec = (root, query, cb) ->
				cb.equal(root.join("assets").get("symbol"), normalizedSymbol);
		Specification<NewsArticle> spec = assetSpec.and(
				filters(category, sentiment, impact, null, null, from, to));
		Page<NewsArticle> result = repository.findAll(spec, pageable);
		return toPage(result);
	}

	@Transactional(readOnly = true)
	public NewsDetailResponse detail(UUID id) {
		NewsArticle article = repository.findById(id)
				.orElseThrow(() -> new NewsNotFoundException("News article not found: " + id));
		List<NewsAsset> assets = assetRepository.findByArticleId(id);
		return mapper.toDetail(article, assets);
	}

	@Transactional(readOnly = true)
	public List<String> sources() {
		return repository.findDistinctSourceNames();
	}

	@Transactional(readOnly = true)
	public NewsMetaResponse meta() {
		Instant latestFetchedAt = repository.findTopByOrderByFetchedAtDesc()
				.map(NewsArticle::getFetchedAt)
				.orElse(null);
		return new NewsMetaResponse(
				repository.count(),
				sources(),
				Stream.of(NewsCategory.values()).map(Enum::name).toList(),
				Stream.of(NewsSentiment.values()).map(Enum::name).toList(),
				Stream.of(NewsImpact.values()).map(Enum::name).toList(),
				latestFetchedAt);
	}

	/**
	 * Builds a fully-typed filter specification; null filters are simply omitted so no
	 * untyped null parameters ever reach the database (PostgreSQL otherwise fails with
	 * {@code function lower(bytea) does not exist} / {@code could not determine data type}).
	 */
	Specification<NewsArticle> filters(
			NewsCategory category,
			NewsSentiment sentiment,
			NewsImpact impact,
			String source,
			String q,
			Instant from,
			Instant to
	) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (category != null) {
				predicates.add(cb.equal(root.get("category"), category));
			}
			if (sentiment != null) {
				predicates.add(cb.equal(root.get("sentiment"), sentiment));
			}
			if (impact != null) {
				predicates.add(cb.equal(root.get("impactLevel"), impact));
			}
			if (source != null) {
				predicates.add(cb.equal(cb.lower(root.get("sourceName")), source.toLowerCase(Locale.ROOT)));
			}
			if (q != null) {
				String pattern = "%" + q.toLowerCase(Locale.ROOT) + "%";
				predicates.add(cb.or(
						cb.like(cb.lower(root.get("title")), pattern),
						cb.like(cb.lower(cb.coalesce(root.get("summary"), "")), pattern)));
			}
			if (from != null) {
				predicates.add(cb.greaterThanOrEqualTo(root.get("publishedAt"), from));
			}
			if (to != null) {
				predicates.add(cb.lessThanOrEqualTo(root.get("publishedAt"), to));
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}

	private PageResponse<NewsResponse> toPage(Page<NewsArticle> result) {
		List<NewsArticle> articles = result.getContent();
		Map<UUID, List<NewsAsset>> assetsByArticle = batchAssets(articles);
		List<NewsResponse> items = mapper.toResponses(articles, assetsByArticle);
		return new PageResponse<>(
				items,
				result.getNumber(),
				result.getSize(),
				result.getTotalElements(),
				result.getTotalPages(),
				result.hasNext());
	}

	private Map<UUID, List<NewsAsset>> batchAssets(List<NewsArticle> articles) {
		if (articles.isEmpty()) {
			return Map.of();
		}
		List<UUID> ids = articles.stream().map(NewsArticle::getId).toList();
		return mapper.groupByArticle(assetRepository.findByArticleIdIn(ids));
	}

	private Pageable pageable(int page, int size, String sort, String direction) {
		int safePage = Math.max(page, 0);
		int defaultSize = properties.pageSize() > 0 ? properties.pageSize() : 20;
		int safeSize = size <= 0 ? defaultSize : Math.min(size, MAX_SIZE);
		String safeSort = normalizeSort(sort);
		Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
		return PageRequest.of(safePage, safeSize, Sort.by(dir, safeSort));
	}

	private String normalizeSort(String sort) {
		if (sort == null || sort.isBlank()) {
			return "publishedAt";
		}
		String candidate = sort.trim();
		if (!SORT_WHITELIST.contains(candidate)) {
			throw new InvalidNewsFilterException("Invalid sort field: " + candidate
					+ " (allowed: " + SORT_WHITELIST + ")");
		}
		// createdAt is the entity's audit field column name.
		return "createdAt".equals(candidate) ? "createdAt" : candidate;
	}

	private static String clean(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String trimmed = value.trim();
		if (trimmed.length() > MAX_Q_LENGTH) {
			throw new InvalidNewsFilterException("Filter value too long (max " + MAX_Q_LENGTH + " chars)");
		}
		return trimmed;
	}

	private static String normalizeSymbol(String symbol) {
		if (symbol == null || symbol.isBlank()) {
			throw new InvalidNewsFilterException("Asset symbol is required");
		}
		String normalized = symbol.trim().toUpperCase(java.util.Locale.ROOT);
		if (!normalized.matches("[A-Z0-9]{1,20}")) {
			throw new InvalidNewsFilterException("Invalid asset symbol: " + symbol);
		}
		return normalized;
	}
}