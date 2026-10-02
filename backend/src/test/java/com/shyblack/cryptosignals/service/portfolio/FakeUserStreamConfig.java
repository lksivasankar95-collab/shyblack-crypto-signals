package com.shyblack.cryptosignals.service.portfolio;

import com.shyblack.cryptosignals.service.portfolio.UserStreamConnector;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Replaces the real Binance user-data transport with an in-process double.
 *
 * <p>Imported by the lifecycle tests so no socket is ever opened and no exchange is contacted.
 * Without this, a test that started a LIVE lifecycle would attempt a real listen-key request
 * against whatever {@code app.live-trading.mode} happened to point at.
 *
 * <p>{@code @Primary} because {@link PortfolioUserStreamConfig} registers the real connector
 * unconditionally; the double wins by precedence so no configuration change is needed to make a
 * test safe.
 */
@TestConfiguration
public class FakeUserStreamConfig {

	@Bean
	@Primary
	public UserStreamConnector fakeUserStreamConnector() {
		return new FakeUserStreamConnector();
	}
}