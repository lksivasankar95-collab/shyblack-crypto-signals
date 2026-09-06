package com.shyblack.cryptosignals.market;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
 * Simple private alerts WebSocket handler. Keeps alerts concerns separate from public market feeds.
 */
@Component
public class AlertsWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(AlertsWebSocketHandler.class);
    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(Instant.class, (JsonSerializer<Instant>) (src, type, ctx) -> new JsonPrimitive(src.toString()))
            .registerTypeAdapter(BigDecimal.class, (JsonSerializer<BigDecimal>) (src, type, ctx) -> new JsonPrimitive(src))
            .create();
    private final CopyOnWriteArraySet<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.debug("[WS-ALERTS] Client connected id={} total={}", session.getId(), sessions.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    public void broadcastAlert(JsonObject payload) {
        if (payload == null || sessions.isEmpty()) return;
        String json = gson.toJson(payload);
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) continue;
            synchronized (session) {
                try {
                    session.sendMessage(new TextMessage(json));
                } catch (IOException ex) {
                    log.debug("[WS-ALERTS] send failed for session {}: {}", session.getId(), ex.getMessage());
                }
            }
        }
    }
}
