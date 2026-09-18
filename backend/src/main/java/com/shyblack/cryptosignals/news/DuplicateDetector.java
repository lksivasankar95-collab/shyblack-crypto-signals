package com.shyblack.cryptosignals.news;

import com.shyblack.cryptosignals.news.provider.RawNewsArticle;
import com.shyblack.cryptosignals.repository.NewsArticleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Application-level duplicate detection and logging.
 * <p>Database uniqueness constraints on {@code canonicalHash} and {@code sourceUrl}
 * remain the final authority; this class avoids the expensive insert when possible
 * and reports exactly which signal matched.</p>
 */
@Component
public class DuplicateDetector {

	private static final Logger log = LoggerFactory.getLogger(DuplicateDetector.class);

	private final NewsArticleRepository repository;

	public DuplicateDetector(NewsArticleRepository repository) {
		this.repository = repository;
	}

	public record DedupResult(boolean duplicated, String reason) {
	}

	public DedupResult check(RawNewsArticle raw, String canonicalUrl, String canonicalHash) {
		if (canonicalUrl != null && repository.existsBySourceUrl(canonicalUrl)) {
			log.debug("[News] Duplicate by source URL: {}", canonicalUrl);
			return new DedupResult(true, "source_url");
		}
		if (raw.externalId() != null && !raw.externalId().isBlank()) {
			if (repository.existsByExternalNewsIdAndSourceName(raw.externalId(), raw.sourceName())) {
				log.debug("[News] Duplicate by external id '{}' from {}", raw.externalId(), raw.sourceName());
				return new DedupResult(true, "external_id");
			}
		}
		if (canonicalHash != null && repository.existsByCanonicalHash(canonicalHash)) {
			log.debug("[News] Duplicate by canonical hash: {}", canonicalHash);
			return new DedupResult(true, "canonical_hash");
		}
		return new DedupResult(false, null);
	}
}