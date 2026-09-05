package com.shyblack.cryptosignals.market;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import com.shyblack.cryptosignals.dto.market.MarketTickerResponse;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import com.shyblack.cryptosignals.service.BinanceOptionsMarketService;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Proxies live Binance ticker events to connected app clients immediately —
 * one broadcast per WebSocket event with no artificial batching delay.
 */
@Component
public class MarketsWebSocketHandler extends TextWebSocketHandler {

	private static final Logger log = LoggerFactory.getLogger(MarketsWebSocketHandler.class);

	private final MarketBook book;
	private final Gson gson = new GsonBuilder()
			.registerTypeAdapter(Instant.class, (JsonSerializer<Instant>) (src, type, ctx) -> new JsonPrimitive(src.toString()))
			.registerTypeAdapter(BigDecimal.class, (JsonSerializer<BigDecimal>) (src, type, ctx) -> new JsonPrimitive(src))
			.create();
	private final CopyOnWriteArraySet<WebSocketSession> sessions = new CopyOnWriteArraySet<>();
	private final ConcurrentHashMap<String, TradingMode> sessionModes = new ConcurrentHashMap<>();

	public MarketsWebSocketHandler(MarketBook book) {
		this.book = book;
		this.book.spotTickers().addBatchListener(changed -> broadcast(TradingMode.SPOT, changed));
		this.book.futuresTickers().addBatchListener(changed -> broadcast(TradingMode.FUTURES, changed));
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession session) {
		TradingMode mode = modeFrom(session);
		sessions.add(session);
		sessionModes.put(session.getId(), mode);
		sendSnapshot(session, mode);
		log.debug("[WS] Client connected id={} mode={} total={}", session.getId(), mode, sessions.size());
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
		sessions.remove(session);
		sessionModes.remove(session.getId());
	}

	private void sendSnapshot(WebSocketSession session, TradingMode mode) {
		JsonObject payload = new JsonObject();
		payload.addProperty("type", "snapshot");
		payload.addProperty("mode", mode.name());
		if (mode == TradingMode.OPTIONS) {
			payload.addProperty("message", BinanceOptionsMarketService.UNAVAILABLE_MESSAGE);
			payload.add("tickers", gson.toJsonTree(List.of()));
		} else {
			payload.add("tickers", gson.toJsonTree(book.tickers(mode).snapshot().stream().map(this::toDto).toList()));
		}
		send(session, gson.toJson(payload));
	}

	/**
	 * Called synchronously by {@link MarketTickerStore#upsertAll} on every
	 * Binance array event. Sends the changed tickers immediately — no buffering.
	 */
	private void broadcast(TradingMode mode, List<MarketTicker> changed) {
		if (changed == null || changed.isEmpty() || sessions.isEmpty()) {
			return;
		}
		List<MarketTickerResponse> dtos = changed.stream().map(this::toDto).toList();
		JsonObject payload = new JsonObject();
		payload.addProperty("type", "tickers");
		payload.addProperty("mode", mode.name());
		payload.add("tickers", gson.toJsonTree(dtos));
		String json = gson.toJson(payload);
		log.debug("[WS] Broadcasting {} {} ticker(s)", changed.size(), mode);
		for (WebSocketSession session : sessions) {
			if (sessionModes.getOrDefault(session.getId(), TradingMode.SPOT) == mode) {
				send(session, json);
			}
		}
	}

	private static TradingMode modeFrom(WebSocketSession session) {
		URI uri = session.getUri();
		if (uri == null) {
			return TradingMode.SPOT;
		}
		Map<String, List<String>> query = UriComponentsBuilder.fromUri(uri).build().getQueryParams();
		List<String> values = query.get("mode");
		if (values == null || values.isEmpty()) {
			return TradingMode.SPOT;
		}
		try {
			return TradingMode.valueOf(values.get(0).trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ex) {
			return TradingMode.SPOT;
		}
	}

	private void send(WebSocketSession session, String json) {
		if (!session.isOpen()) {
			sessions.remove(session);
			sessionModes.remove(session.getId());
			return;
		}
		synchronized (session) {
			try {
				session.sendMessage(new TextMessage(json));
			} catch (IOException ex) {
				sessions.remove(session);
				sessionModes.remove(session.getId());
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
