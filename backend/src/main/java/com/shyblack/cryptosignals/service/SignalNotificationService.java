package com.shyblack.cryptosignals.service;

import com.google.gson.JsonObject;
import com.shyblack.cryptosignals.entity.DeviceToken;
import com.shyblack.cryptosignals.entity.Notification;
import com.shyblack.cryptosignals.entity.NotificationPreference;
import com.shyblack.cryptosignals.entity.Signal;
import com.shyblack.cryptosignals.entity.enums.NotificationCategory;
import com.shyblack.cryptosignals.repository.DeviceTokenRepository;
import com.shyblack.cryptosignals.repository.NotificationRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.shyblack.cryptosignals.repository.SignalRepository;

@Service
@RequiredArgsConstructor
public class SignalNotificationService {

    private static final Logger log = LoggerFactory.getLogger(SignalNotificationService.class);

    private final NotificationRepository notificationRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final NotificationPreferenceService preferenceService;
    private final FcmSenderService fcmSenderService;
    private final com.shyblack.cryptosignals.market.AlertsWebSocketHandler websocketHandler;
    private final SignalRepository signalRepository;

    public void notifyForSignal(Signal signal) {
        UUID signalId = signal.getId();

        // Defensive guard: only send notifications for actionable BUY signals
        if (signal.getSignalGrade() != com.shyblack.cryptosignals.entity.enums.SignalGrade.BUY
                && signal.getSignalGrade() != com.shyblack.cryptosignals.entity.enums.SignalGrade.STRONG_BUY) {
            log.debug("[SIGNAL_NOTIFICATION] signal {} grade={} not actionable, skipping", signalId, signal.getSignalGrade());
            return;
        }

        String title = String.format("🚀 New Spot Signal — %s", signal.getSymbol());
        String body = String.format("%s • Score %d/100 • Entry %s",
                signal.getSignalGrade(), signal.getScore(), signal.getEntryPrice());

        JsonObject data = new JsonObject();
        data.addProperty("type", "SIGNAL_GENERATED");
        data.addProperty("marketType", signal.getTradingMode().name());
        data.addProperty("symbol", signal.getSymbol());
        data.addProperty("signalId", signalId.toString());
        data.addProperty("grade", signal.getSignalGrade().name());
        data.addProperty("score", Integer.toString(signal.getScore()));
        data.addProperty("entryPrice", signal.getEntryPrice().toPlainString());
        if (signal.getStopLoss() != null) data.addProperty("stopLoss", signal.getStopLoss().toPlainString());
        if (signal.getTargetPrice() != null) data.addProperty("takeProfit1", signal.getTargetPrice().toPlainString());

        List<DeviceToken> tokens = deviceTokenRepository.findByActiveTrue();
        int sent = 0;
        for (DeviceToken dt : tokens) {
            try {
                if (!preferenceService.signalsEnabledFor(dt.getUser())) {
                    continue;
                }
                // per-user idempotency check
                if (notificationRepository.existsByUserAndSignalId(dt.getUser(), signalId)) {
                    continue;
                }
                boolean ok = fcmSenderService.sendToToken(dt.getToken(), title, body, data);
                if (ok) {
                    sent++;
                    // persist a notification history record for this user
                    Notification n = new Notification();
                    n.setUser(dt.getUser());
                    n.setCategory(NotificationCategory.SIGNALS);
                    n.setTitle(title);
                    n.setBody(body);
                    n.setRead(false);
                    n.setSignalId(signalId);
                    n.setMarketType(signal.getTradingMode().name());
                    try {
                        notificationRepository.save(n);
                    } catch (Exception ex) {
                        log.warn("[SIGNAL_NOTIFICATION] failed to save notification history for user={}: {}", dt.getUser().getId(), ex.getMessage());
                    }
                }
            } catch (Exception ex) {
                log.warn("[SIGNAL_NOTIFICATION] token send failed for token id={} err={}", dt.getId(), ex.getMessage());
            }
        }
        log.info("[SIGNAL_NOTIFICATION] Dispatched signal={} symbol={} sent={} tokens={}",
                signalId, signal.getSymbol(), sent, tokens.size());

        // publish realtime event to websocket clients (as alert)
        try {
            com.google.gson.JsonObject payload = new com.google.gson.JsonObject();
            payload.addProperty("type", "alert");
            payload.addProperty("symbol", signal.getSymbol());
            payload.addProperty("signalId", signalId.toString());
            payload.addProperty("grade", signal.getSignalGrade().name());
            payload.addProperty("score", signal.getScore());
            websocketHandler.broadcastAlert(payload);
        } catch (Exception ex) {
            log.debug("[SIGNAL_NOTIFICATION] websocket publish failed: {}", ex.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSignalGeneratedEvent(SignalGeneratedEvent event) {
        try {
            signalRepository.findById(event.getSignalId()).ifPresent(this::notifyForSignal);
        } catch (Exception ex) {
            log.warn("[SIGNAL_NOTIFICATION] failed to process post-commit notification for signal {}: {}", event.getSignalId(), ex.getMessage());
        }
    }
}
