package com.shyblack.cryptosignals.news.provider;

import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Holds all active {@link NewsProvider} beans so ingestion can iterate over them
 * without knowing concrete types.
 */
@Component
public class NewsProviderRegistry {

	private final List<NewsProvider> providers;

	public NewsProviderRegistry(List<NewsProvider> providers) {
		this.providers = List.copyOf(providers);
	}

	public List<NewsProvider> activeProviders() {
		return providers.stream().filter(NewsProvider::enabled).toList();
	}

	public List<NewsProvider> all() {
		return providers;
	}
}