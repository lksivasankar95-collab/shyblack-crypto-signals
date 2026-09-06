package com.shyblack.cryptosignals.config;

import com.shyblack.cryptosignals.market.MarketsWebSocketHandler;
import com.shyblack.cryptosignals.market.AlertsWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class MarketWebSocketConfig implements WebSocketConfigurer {

	private final MarketsWebSocketHandler marketsWebSocketHandler;
	private final AlertsWebSocketHandler alertsWebSocketHandler;

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		registry.addHandler(marketsWebSocketHandler, "/ws/markets")
				.setAllowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*", "*");
		registry.addHandler(alertsWebSocketHandler, "/ws/private")
			.setAllowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*", "*");
	}
}
