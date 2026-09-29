package com.shyblack.cryptosignals.config;

import java.time.Duration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

/**
 * Builds {@link SimpleClientHttpRequestFactory} instances with explicit,
 * bounded connect/read timeouts so no outbound HTTP call can hang a request
 * thread or scheduler thread indefinitely.
 */
public final class HttpClientFactory {

	/** TCP connect timeout for outbound exchange/market HTTP calls. */
	public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

	/** Socket read timeout for outbound exchange/market HTTP calls. */
	public static final Duration READ_TIMEOUT = Duration.ofSeconds(15);

	private HttpClientFactory() {
	}

	public static SimpleClientHttpRequestFactory withDefaultTimeouts() {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(CONNECT_TIMEOUT);
		factory.setReadTimeout(READ_TIMEOUT);
		return factory;
	}
}
