package com.shyblack.cryptosignals.news.provider;

import java.util.List;

/**
 * Abstraction over any external crypto news source.
 * <p>Implementations fetch raw articles, map provider-specific formats into the
 * neutral {@link RawNewsArticle} model, and expose provider metadata. The system
 * supports multiple active providers without any core rewrite.</p>
 */
public interface NewsProvider {

	/** Stable provider identifier, e.g. "rss". */
	String providerName();

	/** Display name for logging/metadata, e.g. "CoinDesk RSS". */
	String displayName();

	/**
	 * Whether this provider is available for ingestion right now.
	 * Implementations must be safe to call frequently.
	 */
	boolean enabled();

	/**
	 * Fetches the latest articles from this provider.
	 *
	 * @return normalized raw articles; never null
	 * @throws com.shyblack.cryptosignals.exception.NewsProviderException when the
	 *         provider is unreachable or returns unusable data
	 */
	List<RawNewsArticle> fetchLatest() throws Exception;

	/**
	 * Fetches the latest articles for a single asset, when the provider supports it.
	 * Defaults to delegating to {@link #fetchLatest()}.
	 */
	default List<RawNewsArticle> fetchForAsset(String symbol) throws Exception {
		return fetchLatest();
	}
}