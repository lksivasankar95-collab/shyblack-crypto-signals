package com.shyblack.cryptosignals.controller;

import com.shyblack.cryptosignals.dto.news.NewsContextResponse;
import com.shyblack.cryptosignals.dto.news.NewsDetailResponse;
import com.shyblack.cryptosignals.dto.news.NewsMetaResponse;
import com.shyblack.cryptosignals.dto.news.NewsResponse;
import com.shyblack.cryptosignals.dto.news.PageResponse;
import com.shyblack.cryptosignals.entity.enums.NewsCategory;
import com.shyblack.cryptosignals.entity.enums.NewsImpact;
import com.shyblack.cryptosignals.entity.enums.NewsSentiment;
import com.shyblack.cryptosignals.service.news.NewsIntelligenceService;
import com.shyblack.cryptosignals.service.news.NewsQueryService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/news")
@RequiredArgsConstructor
public class NewsController {

	private final NewsQueryService queryService;
	private final NewsIntelligenceService intelligenceService;

	@GetMapping
	public PageResponse<NewsResponse> list(
			@RequestParam(required = false) NewsCategory category,
			@RequestParam(required = false) NewsSentiment sentiment,
			@RequestParam(required = false) NewsImpact impact,
			@RequestParam(required = false) String source,
			@RequestParam(required = false) String q,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
			@RequestParam(required = false, defaultValue = "0") int page,
			@RequestParam(required = false, defaultValue = "0") int size,
			@RequestParam(required = false) String sort,
			@RequestParam(required = false, defaultValue = "desc") String direction) {
		return queryService.search(category, sentiment, impact, source, q, from, to, page, size, sort, direction);
	}

	@GetMapping("/search")
	public PageResponse<NewsResponse> search(
			@RequestParam(required = false) NewsCategory category,
			@RequestParam(required = false) NewsSentiment sentiment,
			@RequestParam(required = false) NewsImpact impact,
			@RequestParam(required = false) String source,
			@RequestParam(required = false) String q,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
			@RequestParam(required = false, defaultValue = "0") int page,
			@RequestParam(required = false, defaultValue = "0") int size,
			@RequestParam(required = false) String sort,
			@RequestParam(required = false, defaultValue = "desc") String direction) {
		return queryService.search(category, sentiment, impact, source, q, from, to, page, size, sort, direction);
	}

	@GetMapping("/asset/{symbol}")
	public PageResponse<NewsResponse> assetNews(
			@PathVariable String symbol,
			@RequestParam(required = false) NewsCategory category,
			@RequestParam(required = false) NewsSentiment sentiment,
			@RequestParam(required = false) NewsImpact impact,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
			@RequestParam(required = false, defaultValue = "0") int page,
			@RequestParam(required = false, defaultValue = "0") int size,
			@RequestParam(required = false) String sort,
			@RequestParam(required = false, defaultValue = "desc") String direction) {
		return queryService.assetNews(symbol, category, sentiment, impact, from, to, page, size, sort, direction);
	}

	@GetMapping("/asset/{symbol}/context")
	public NewsContextResponse assetContext(
			@PathVariable String symbol,
			@RequestParam(required = false) Integer windowHours) {
		return intelligenceService.context(symbol, windowHours);
	}

	@GetMapping("/meta")
	public NewsMetaResponse meta() {
		return queryService.meta();
	}

	@GetMapping("/sources")
	public List<String> sources() {
		return queryService.sources();
	}

	@GetMapping("/categories")
	public List<NewsCategory> categories() {
		return List.of(NewsCategory.values());
	}

	@GetMapping("/sentiments")
	public List<NewsSentiment> sentiments() {
		return List.of(NewsSentiment.values());
	}

	@GetMapping("/levels")
	public List<NewsImpact> impactLevels() {
		return List.of(NewsImpact.values());
	}

	@GetMapping("/{id}")
	public NewsDetailResponse detail(@PathVariable UUID id) {
		return queryService.detail(id);
	}
}