package com.shyblack.cryptosignals.market;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import com.shyblack.cryptosignals.dto.market.MarketTickerResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class MarketsWebSocketHandler extends TextWebSocketHandler {

	private static final Logger log = LoggerFactory.getLogger(MarketsWebSocketHandler.class);
	private static final long THROTTLE_MS = 1_000;

	private final MarketTickerStore store;
	private final Gson gson = new GsonBuilder()
			.registerTypeAdapter(Instant.class, (JsonSerializer<Instant>) (src, type, ctx) -> new JsonPrimitive(src.toString()))
			.registerTypeAdapter(BigDecimal.class, (JsonSerializer<BigDecimal>) (src, type, ctx) -> new JsonPrimitive(src))
			.create();
	private final CopyOnWriteArraySet<WebSocketSession> sessions = new CopyOnWriteArraySet<>();
	private final ConcurrentHashMap<String, Long> lastPushMs = new ConcurrentHashMap<>();

	public MarketsWebSocketHandler(MarketTickerStore store) {
		this.store = store;
		this.store.addListener(this::onTicker);
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession session) {
		sessions.add(session);
		JsonObject payload = new JsonObject();
		payload.addProperty("type", "snapshot");
		payload.add("tickers", gson.toJsonTree(store.snapshot().stream().map(this::toDto).toList()));
		send(session, gson.toJson(payload));
		log.debug("Markets WS client connected id={} count={}", session.getId(), sessions.size());
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
		sessions.remove(session);
	}

	private void onTicker(MarketTicker ticker) {
		long now = System.currentTimeMillis();
		Long previous = lastPushMs.get(ticker.symbol());
		if (previous != null && now - previous < THROTTLE_MS) {
			return;
		}
		lastPushMs.put(ticker.symbol(), now);
		JsonObject payload = new JsonObject();
		payload.addProperty("type", "ticker");
		payload.add("ticker", gson.toJsonTree(toDto(ticker)));
		String json = gson.toJson(payload);
		for (WebSocketSession session : sessions) {
			send(session, json);
		}
	}

	private void send(WebSocketSession session, String json) {
		if (!session.isOpen()) {
			sessions.remove(session);
			return;
		}
		synchronized (session) {
			try {
				session.sendMessage(new TextMessage(json));
			} catch (IOException ex) {
				sessions.remove(session);
			}
		}
	}

	private MarketTickerResponse toDto(MarketTicker ticker) {
		return new MarketTickerResponse(
				ticker.symbol(),
				ticker.name(),
				ticker.price(),
				ticker.change24h(),
				ticker.changePercent24h(),
				ticker.volume24h(),
				ticker.high24h(),
				ticker.low24h(),
				ticker.updatedAt()
		);
	}
}
