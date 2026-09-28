package com.shyblack.cryptosignals.service.news;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.gson.JsonObject;
import com.shyblack.cryptosignals.config.NewsNotificationProperties;
import com.shyblack.cryptosignals.entity.DeviceToken;
import com.shyblack.cryptosignals.entity.NewsArticle;
import com.shyblack.cryptosignals.entity.Notification;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.NewsImpact;
import com.shyblack.cryptosignals.entity.enums.NotificationCategory;
import com.shyblack.cryptosignals.market.AlertsWebSocketHandler;
import com.shyblack.cryptosignals.repository.DeviceTokenRepository;
import com.shyblack.cryptosignals.repository.NewsArticleRepository;
import com.shyblack.cryptosignals.repository.NotificationRepository;
import com.shyblack.cryptosignals.service.FcmSenderService;
import com.shyblack.cryptosignals.service.NotificationPreferenceService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NewsNotificationListenerTest {

	private final UUID newsId = UUID.randomUUID();

	private DeviceTokenRepository deviceRepo;
	private NotificationRepository notificationRepo;
	private NotificationPreferenceService preferences;
	private FcmSenderService fcm;
	private AlertsWebSocketHandler ws;
	private NewsNotificationListener listener;
	private DeviceToken device;

	@BeforeEach
	void setUp() {
		NewsArticleRepository newsRepo = mock(NewsArticleRepository.class);
		deviceRepo = mock(DeviceTokenRepository.class);
		notificationRepo = mock(NotificationRepository.class);
		preferences = mock(NotificationPreferenceService.class);
		fcm = mock(FcmSenderService.class);
		ws = mock(AlertsWebSocketHandler.class);

		listener = new NewsNotificationListener(newsRepo, deviceRepo, notificationRepo,
				preferences, fcm, ws, new NewsNotificationProperties(true, NewsImpact.HIGH, 0));

		User user = new User();
		user.setId(UUID.randomUUID());
		device = new DeviceToken();
		device.setUser(user);
		device.setToken("tok-1");
		device.setActive(true);
		when(deviceRepo.findByActiveTrue()).thenReturn(List.of(device));
	}

	private NewsArticle article(NewsImpact impact, double score) {
		NewsArticle a = new NewsArticle();
		a.setId(newsId);
		a.setSourceName("Cointelegraph");
		a.setTitle("BTC hits a new high");
		a.setSourceUrl("https://cointelegraph.com/btc");
		a.setPublishedAt(Instant.parse("2026-09-27T08:00:00Z"));
		a.setImpactLevel(impact);
		a.setNewsScore(score);
		return a;
	}

	@Test
	void eligibleArticle_notifiesOnce_persistsHistory_broadcastsOnce() {
		when(preferences.newsEnabledFor(any())).thenReturn(true);
		when(notificationRepo.existsByUserAndNewsId(any(), any())).thenReturn(false);
		when(fcm.sendToToken(anyString(), anyString(), anyString(), any())).thenReturn(true);

		listener.process(article(NewsImpact.HIGH, 5.0));

		verify(fcm, times(1)).sendToToken(eq("tok-1"), anyString(), anyString(), any());
		ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
		verify(notificationRepo).save(saved.capture());
		assertThat(saved.getValue().getCategory()).isEqualTo(NotificationCategory.NEWS);
		assertThat(saved.getValue().getNewsId()).isEqualTo(newsId);
		verify(ws, times(1)).broadcastAlert(any(JsonObject.class));
	}

	@Test
	void belowPolicyArticle_noNotification_butStillBroadcast() {
		listener.process(article(NewsImpact.LOW, 0.0));
		verifyNoInteractions(fcm);
		verify(notificationRepo, never()).save(any());
		verify(ws, times(1)).broadcastAlert(any(JsonObject.class));
	}

	@Test
	void duplicateNotification_isSuppressed() {
		when(preferences.newsEnabledFor(any())).thenReturn(true);
		when(notificationRepo.existsByUserAndNewsId(any(), any())).thenReturn(true);

		listener.process(article(NewsImpact.CRITICAL, 9.0));

		verifyNoInteractions(fcm);
		verify(notificationRepo, never()).save(any());
		verify(ws, times(1)).broadcastAlert(any());
	}

	@Test
	void preferenceDisabled_skipsFcm() {
		when(preferences.newsEnabledFor(any())).thenReturn(false);
		listener.process(article(NewsImpact.HIGH, 5.0));
		verifyNoInteractions(fcm);
	}

	@Test
	void fcmFailure_isIsolated() {
		when(preferences.newsEnabledFor(any())).thenReturn(true);
		when(notificationRepo.existsByUserAndNewsId(any(), any())).thenReturn(false);
		when(fcm.sendToToken(anyString(), anyString(), anyString(), any())).thenReturn(false);

		listener.process(article(NewsImpact.HIGH, 5.0)); // must not throw

		verify(notificationRepo, never()).save(any());
		verify(ws, times(1)).broadcastAlert(any());
	}

	@Test
	void websocketFailure_doesNotStopNotification() {
		when(preferences.newsEnabledFor(any())).thenReturn(true);
		when(notificationRepo.existsByUserAndNewsId(any(), any())).thenReturn(false);
		when(fcm.sendToToken(anyString(), anyString(), anyString(), any())).thenReturn(true);
		doThrow(new RuntimeException("ws down")).when(ws).broadcastAlert(any());

		listener.process(article(NewsImpact.HIGH, 5.0)); // must not throw

		verify(fcm, times(1)).sendToToken(anyString(), anyString(), anyString(), any());
	}
}
