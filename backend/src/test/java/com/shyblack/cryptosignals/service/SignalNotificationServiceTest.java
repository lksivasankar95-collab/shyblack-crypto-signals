package com.shyblack.cryptosignals.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.google.gson.JsonObject;
import com.shyblack.cryptosignals.entity.DeviceToken;
import com.shyblack.cryptosignals.entity.Notification;
import com.shyblack.cryptosignals.entity.Signal;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.SignalGrade;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import com.shyblack.cryptosignals.repository.DeviceTokenRepository;
import com.shyblack.cryptosignals.repository.NotificationRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class SignalNotificationServiceTest {

    private NotificationRepository notificationRepository;
    private DeviceTokenRepository deviceTokenRepository;
    private NotificationPreferenceService preferenceService;
    private FcmSenderService fcmSenderService;
    private com.shyblack.cryptosignals.market.AlertsWebSocketHandler websocketHandler;
    private com.shyblack.cryptosignals.repository.SignalRepository signalRepository;
    private SignalNotificationService service;

    @BeforeEach
    void beforeEach() {
        notificationRepository = Mockito.mock(NotificationRepository.class);
        deviceTokenRepository = Mockito.mock(DeviceTokenRepository.class);
        preferenceService = Mockito.mock(NotificationPreferenceService.class);
        fcmSenderService = Mockito.mock(FcmSenderService.class);
        websocketHandler = Mockito.mock(com.shyblack.cryptosignals.market.AlertsWebSocketHandler.class);
        signalRepository = Mockito.mock(com.shyblack.cryptosignals.repository.SignalRepository.class);
        service = new SignalNotificationService(notificationRepository, deviceTokenRepository, preferenceService, fcmSenderService, websocketHandler, signalRepository);
    }

    private Signal buildSignal(SignalGrade grade) {
        Signal s = new Signal();
        s.setId(UUID.randomUUID());
        s.setSymbol("BTCUSDT");
        s.setScore(77);
        s.setSignalGrade(grade);
        s.setTradingMode(TradingMode.SPOT);
        s.setEntryPrice(java.math.BigDecimal.valueOf(30000));
        return s;
    }

    private DeviceToken deviceTokenFor(User u, String token) {
        DeviceToken dt = new DeviceToken();
        dt.setId(UUID.randomUUID());
        dt.setToken(token);
        dt.setActive(true);
        dt.setUser(u);
        return dt;
    }

    @Test
    void buy_sends_notifications_and_persists_history() {
        Signal s = buildSignal(SignalGrade.BUY);
        User u = new User(); u.setId(UUID.randomUUID());
        DeviceToken dt = deviceTokenFor(u, "tok-1");

        when(deviceTokenRepository.findByActiveTrue()).thenReturn(List.of(dt));
        when(preferenceService.signalsEnabledFor(u)).thenReturn(true);
        when(notificationRepository.existsByUserAndSignalId(u, s.getId())).thenReturn(false);
        when(fcmSenderService.sendToToken(eq("tok-1"), any(), any(), any(JsonObject.class))).thenReturn(true);

        service.notifyForSignal(s);

        ArgumentCaptor<Notification> cap = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(cap.capture());
        Notification saved = cap.getValue();
        assert saved.getSignalId().equals(s.getId());
        assert saved.getMarketType().equals(s.getTradingMode().name());
        assert saved.getUser().equals(u);
        assert !saved.isRead();
    }

    @Test
    void watch_does_not_send() {
        Signal s = buildSignal(SignalGrade.WATCH);
        service.notifyForSignal(s);
        verifyNoInteractions(deviceTokenRepository);
        verifyNoInteractions(fcmSenderService);
    }

    @Test
    void duplicate_notification_is_skipped() {
        Signal s = buildSignal(SignalGrade.BUY);
        User u = new User(); u.setId(UUID.randomUUID());
        DeviceToken dt = deviceTokenFor(u, "tok-dup");

        when(deviceTokenRepository.findByActiveTrue()).thenReturn(List.of(dt));
        when(preferenceService.signalsEnabledFor(u)).thenReturn(true);
        when(notificationRepository.existsByUserAndSignalId(u, s.getId())).thenReturn(true);

        service.notifyForSignal(s);

        verify(fcmSenderService, never()).sendToToken(any(), any(), any(), any());
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void preference_disabled_skips_user() {
        Signal s = buildSignal(SignalGrade.BUY);
        User u = new User(); u.setId(UUID.randomUUID());
        DeviceToken dt = deviceTokenFor(u, "tok-3");

        when(deviceTokenRepository.findByActiveTrue()).thenReturn(List.of(dt));
        when(preferenceService.signalsEnabledFor(u)).thenReturn(false);

        service.notifyForSignal(s);

        verify(fcmSenderService, never()).sendToToken(any(), any(), any(), any());
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void multiple_devices_partial_failure_continues() {
        Signal s = buildSignal(SignalGrade.STRONG_BUY);
        User u1 = new User(); u1.setId(UUID.randomUUID());
        User u2 = new User(); u2.setId(UUID.randomUUID());
        DeviceToken d1 = deviceTokenFor(u1, "ok");
        DeviceToken d2 = deviceTokenFor(u2, "bad");

        when(deviceTokenRepository.findByActiveTrue()).thenReturn(List.of(d1, d2));
        when(preferenceService.signalsEnabledFor(u1)).thenReturn(true);
        when(preferenceService.signalsEnabledFor(u2)).thenReturn(true);
        when(notificationRepository.existsByUserAndSignalId(any(), eq(s.getId()))).thenReturn(false);
        when(fcmSenderService.sendToToken(eq("ok"), any(), any(), any(JsonObject.class))).thenReturn(true);
        when(fcmSenderService.sendToToken(eq("bad"), any(), any(), any(JsonObject.class))).thenReturn(false);

        service.notifyForSignal(s);

        // one success -> one save
        verify(notificationRepository, times(1)).save(any());
    }

    @Test
    void fcm_failure_does_not_rollback_signal() {
        Signal s = buildSignal(SignalGrade.BUY);
        User u = new User(); u.setId(UUID.randomUUID());
        DeviceToken dt = deviceTokenFor(u, "tok-fail");

        when(deviceTokenRepository.findByActiveTrue()).thenReturn(List.of(dt));
        when(preferenceService.signalsEnabledFor(u)).thenReturn(true);
        when(notificationRepository.existsByUserAndSignalId(u, s.getId())).thenReturn(false);
        when(fcmSenderService.sendToToken(any(), any(), any(), any(JsonObject.class))).thenReturn(false);

        // Should not throw
        service.notifyForSignal(s);

        verify(notificationRepository, never()).save(any());
    }
}
