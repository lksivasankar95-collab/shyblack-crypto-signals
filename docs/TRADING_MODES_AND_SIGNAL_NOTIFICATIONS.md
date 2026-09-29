# Trading Modes & Signal Notifications

Application/strategy-controlled **trading modes**, mode-specific strategy
selection, global per-mode signal generation, and **application-driven** signal
notifications (no user-level mode eligibility).

Status labels: **VERIFIED**, **NOT VERIFIED**, **BLOCKED**, **KNOWN ISSUE**.

---

## 1. Trading modes are application-controlled (not a user preference)

Trading mode is **not** a user-level preference. There is no user-facing mode
selector on Signals, Profile, Signal Preferences, or Settings. Modes are
capabilities of the platform:

- **SPOT** — active strategy resolved per mode (`Trend Pullback` / `EMA Trend
  Following`), driven by `SpotSignalScheduler`.
- **FUTURES** — active strategy (Futures EMA + RSI), driven by
  `FuturesSignalScheduler`.
- **OPTIONS** — reserved capability (enum + market handling); **no engine or
  scheduler** is created.

The market browser uses a single application default, `kAppMarketMode`
(Flutter: `domain/entities/app_settings.dart`), currently `spot`.

### Removed user-level surface

| Concern | Removed |
|---|---|
| Settings API | `SettingsResponse.selectedTradingModes`, `SettingsUpdateRequest.selectedTradingModes`/`tradingMode` |
| User API | `UserResponse.tradingMode` |
| Service | `UserTradingModePreferenceService` (deleted) |
| Notification | per-user trading-mode eligibility filter |
| Entity behaviour | `User.tradingMode` and `UserSettings.selectedTradingModesCsv` are **no longer read or written** |
| Flutter | `AppSettings.tradingMode`/`selectedTradingModes`, `AppSettingsModel` mode parse/serialize, `SettingsController.setTradingMode`/`setSelectedTradingModes`, Signals `TRADING MODE` card, Profile/Signal-Preferences mode tiles, Settings multi-select |

### Backward compatibility / database

The legacy columns `users.trading_mode` and
`user_settings.selected_trading_modes_csv` are **retained (not dropped)** so
existing data is untouched; they have no readers or writers. No migration is
required (Hibernate `ddl-auto`; prod `validate` is unaffected because no schema
change is introduced).

## 2. Mode-specific strategy selection (existing — reused)

`entity/UserStrategySelection` is unique on `(user_id, trading_mode)` and
`service/strategy/StrategyResolver.resolveActive(mode)` + `TradingStrategy` +
`engineKey` provide the active strategy per mode. This is the **single source of
truth** for which engine runs for a mode. No global mode setting was introduced.
Changing the FUTURES strategy does not touch SPOT, and vice versa.

## 3. Signal generation (global, per-mode — unchanged)

```
SpotSignalScheduler  (@Scheduled cron "0 0/15 * * * *") → resolveActive(SPOT)
   → SpotSignalEngine / TrendPullbackSignalService / EmaTrendFollowingSignalService
     → Signal(tradingMode=SPOT) → save
FuturesSignalScheduler (@Scheduled same cron)          → resolveActive(FUTURES)
   → FuturesSignalEngine → Signal(tradingMode=FUTURES) → save
      → SignalGeneratedEvent (published inside the @Transactional cycle)
```

Signals are **global**; they are not generated per user. `Signal` carries
`tradingMode`, `strategyId`, `strategyVersion`, `setupId`, `score`,
`signalGrade`. A mode failure is isolated per scheduler.

### Scheduler pool (RESOLVED)

`spring.task.scheduling.pool.size` is now configured (`application.yml`,
`${SCHEDULER_POOL_SIZE:4}`), so Spot, Futures, News and reconciliation jobs no
longer share a single thread / head-of-line block each other. Cron expressions
are unchanged.

## 4. Notifications are application-driven (no user mode filter)

```
Signal persisted → SignalGeneratedEvent
   → SignalNotificationService.onSignalGeneratedEvent (@TransactionalEventListener AFTER_COMMIT)
        for each active device token:
          1. NotificationPreferenceService.signalsEnabledFor(user)?   else skip
          2. existsByUserAndSignalId(user, signalId)?                 else skip (dedup)
          3. FcmSenderService.sendToToken(...) → persist Notification(category=SIGNALS,
                                                                     signalId, marketType)
    → AlertsWebSocketHandler alert (global)
```

There is **no trading-mode eligibility decision**. A user with signal
notifications enabled receives every application-generated signal, regardless of
the signal's mode. `Notification.marketType` still records the originating
signal's mode (SPOT / FUTURES / OPTIONS). Dedup remains `(user_id, signal_id)`.

| User | SPOT signal | FUTURES signal |
|---|---|---|
| any eligible user | PUSH | PUSH |

## 5. Payload & Flutter navigation

FCM `data`: `type=SIGNAL_GENERATED`, `marketType`, `symbol`, `signalId`, `grade`,
`score`, `entryPrice`, optional `stopLoss`/`takeProfit1`.

Flutter: `NotificationService` extracts `signalId` → `pendingSignalProvider` →
`MainShell` → `SignalDetailsScreen(signalId)`. Flutter performs no eligibility
decision.

## 6. WebSocket

`AlertsWebSocketHandler` (`/ws/private`) broadcasts a generic, user-anonymous
`alert`. Flutter `NotificationsController` refreshes the user-scoped
`/api/v1/notifications` list on `type=alert`. Private WS URL is sourced from
runtime configuration (`WS_BASE_URL`, default platform-aware).

## 7. Configuration

| Key | Where | Notes |
|---|---|---|
| `spring.task.scheduling.pool.size` | `application.yml` | `4` (env `SCHEDULER_POOL_SIZE`) |
| `API_BASE_URL` / `WS_BASE_URL` | Flutter `--dart-define` | runtime-configurable; defaults: Android `10.0.2.2`, desktop/web `localhost` |

## 8. Verification status

- Application-driven notifications (SPOT signal → any eligible user, FUTURES
  signal → any eligible user, dedup, partial FCM failure, `marketType`):
  **VERIFIED** (unit — `SignalNotificationServiceTest`).
- Settings/User API no longer accept/return user trading mode; settings still
  load: **VERIFIED** (unit — `SettingsServiceTest`, live `8080`).
- Signals screen has no trading-mode selector: **VERIFIED** (widget test).
- Live backend contract (settings/user payload without mode) on the running
  `8080`: **BLOCKED** — `8080` runs the pre-change build and was not restarted.
- Live FCM/device push and notification-tap → detail on a device: **BLOCKED**
  (no Firebase credentials/device).
- Scheduler concurrency: **VERIFIED (config)** — dedicated pool configured.
