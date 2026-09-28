# Trading Modes & Signal Notifications

Multi-select trading modes, mode-specific strategy selection, global per-mode
signal generation, and **user-scoped mode-filtered** signal notifications.

Status labels: **VERIFIED**, **NOT VERIFIED**, **BLOCKED**, **KNOWN ISSUE**.

---

## 1. Trading modes (settings)

A user selects one or more modes: `SPOT`, `FUTURES`, `OPTIONS`. Selection is
persisted in `user_settings.selected_trading_modes_csv` (comma-joined enum names,
e.g. `"SPOT,FUTURES"`).

| Concern | Detail |
|---|---|
| Entity | `entity/UserSettings.java` — column `selected_trading_modes_csv` + transient `get/setSelectedTradingModes` |
| API | `GET/PATCH /api/v1/settings` — `SettingsResponse.selectedTradingModes`, `SettingsUpdateRequest.selectedTradingModes` |
| Validation | `SettingsService.update` rejects an empty list, dedupes (`LinkedHashSet`); Jackson rejects unknown enum values |
| Legacy field | `users.trading_mode` is kept and set to the **first** selection (existing consumers keep working) |
| Backward compat | If the stored list is null/blank, `SettingsDtos.effectiveSelectedModes` derives `[User.tradingMode]` |
| Flutter | `AppSettings.selectedTradingModes` (always non-empty), `AppSettingsModel` parse/serialize with legacy fallback, `SettingsController.setSelectedTradingModes`, Settings screen multi-select checkboxes (single-select radio removed) |

DDL: the new column is applied by Hibernate `ddl-auto` (`update` dev / `create-drop`
tests). The prod profile uses `validate` and there is no migration tool, so the
column must be added to prod out-of-band — **KNOWN ISSUE / deployment note**.

## 2. Supported modes

- **SPOT** — signal engine present (`SpotSignalEngine` / `TrendPullbackSignalService`).
- **FUTURES** — signal engine present (`FuturesSignalEngine`).
- **OPTIONS** — selectable in Settings, **no signal engine/scheduler** (`signal/` has
  only Spot + Futures). Not faked. If an OPTIONS signal is ever emitted, the
  mode filter below handles it automatically.

## 3. Mode-specific strategy selection (already existed — reused)

`entity/UserStrategySelection` is unique on `(user_id, trading_mode)` → one strategy
per user per mode. `service/strategy/StrategyResolver.resolveActive(mode)` +
`TradingStrategy` + the Strategy Builder (`/api/v1/strategies?mode=`) provide the
per-mode selection. Changing the FUTURES strategy does not touch SPOT, and vice
versa. Trend Pullback is a USER/SYSTEM SPOT strategy and is unaffected.

## 4. Signal generation (global, per-mode — unchanged)

```
SpotSignalScheduler  (@Scheduled cron "0 0/15 * * * *") → resolveActive(SPOT)
   → SpotSignalEngine / TrendPullbackSignalService → Signal(tradingMode=SPOT) → save
FuturesSignalScheduler (@Scheduled same cron)          → resolveActive(FUTURES)
   → FuturesSignalEngine → Signal(tradingMode=FUTURES) → save
      → SignalGeneratedEvent (published inside the @Transactional cycle)
```

Signals are **global** (one active strategy per mode platform-wide); they are not
generated per user. `Signal` carries `tradingMode`, `strategyId`, `strategyVersion`,
`setupId`, `score`, `signalGrade`. A mode failure is isolated: each scheduler wraps
its body in try/catch, so one mode's failure never aborts the other.

### KNOWN ISSUE — schedulers are serialized, not concurrent
No `spring.task.scheduling.pool.size` is configured, so Spring Boot's default
scheduler pool is **1**: all `@Scheduled` jobs (spot, futures, news, reconciliation)
run on a single thread (`scheduling-1`) and are **serialized**. A long cycle (e.g.
Futures over ~527 symbols) delays Spot and News (head-of-line blocking). They are
independent and cannot crash each other, but they do **not** run in parallel.
Recommended fix (config only): set `spring.task.scheduling.pool.size: 4`.

## 5. User-scoped mode filtering (notifications)

The signal is generated globally; **delivery** is filtered per user.

```
Signal persisted → SignalGeneratedEvent
   → SignalNotificationService.onSignalGeneratedEvent (@TransactionalEventListener AFTER_COMMIT)
        for each active device token:
          1. NotificationPreferenceService.signalsEnabledFor(user)?      else skip
          2. UserTradingModePreferenceService.isModeSelected(user, signal.tradingMode)?  else skip
          3. existsByUserAndSignalId(user, signalId)?                    else skip (dedup)
          4. FcmSenderService.sendToToken(...) → persist Notification(category=SIGNALS, signalId, marketType)
    → AlertsWebSocketHandler alert (global)
```

`UserTradingModePreferenceService.effectiveSelectedModes(user)` = stored list, else
legacy `[User.tradingMode]`. A mode-mismatched recipient gets **no FCM call and no
`Notification` row**, with a concise log:
`[SignalNotify] skip user=<id> signal=<id> reason=TRADING_MODE_NOT_SELECTED signalMode=FUTURES selectedModes=[SPOT]`.

| User | SPOT signal | FUTURES signal |
|---|---|---|
| `[SPOT]` | PUSH | SKIP |
| `[FUTURES]` | SKIP | PUSH |
| `[SPOT,FUTURES]` | PUSH | PUSH |

Dedup remains `(user_id, signal_id)` → at most one notification per signal per user.

## 6. Payload & Flutter navigation

FCM `data`: `type=SIGNAL_GENERATED`, `marketType` (trading mode), `symbol`,
`signalId`, `grade`, `score`, `entryPrice`, optional `stopLoss`/`takeProfit1`.

Flutter: `NotificationService` extracts `signalId` → `pendingSignalProvider` →
`MainShell` → `SignalDetailsScreen(signalId)`. Flutter performs **no** eligibility
decision — mode filtering is server-side only.

## 7. WebSocket

`AlertsWebSocketHandler` (`/ws/private`) broadcasts a generic, **user-anonymous**
`alert` (no per-user signal content). The Flutter `NotificationsController` only
refreshes the user-scoped `/api/v1/notifications` list on `type=alert`. It is not
user-targeted because the WS sessions are unauthenticated (pre-existing
**KNOWN SECURITY FOLLOW-UP**); authoritative per-user filtering is enforced on FCM
+ `Notification` rows.

## 8. Configuration

| Key | Where | Notes |
|---|---|---|
| (none) | `spring.task.scheduling.pool.size` | Not set → pool 1 (see §4 known issue) |
| `user_settings.selected_trading_modes_csv` | DB column | comma-joined enum names |

## 9. Verification status

- Multi-select settings (model, API, validation, backward compat): **VERIFIED**
  (`SettingsServiceTest`, `trading_modes_settings_test.dart`).
- Mode-filtered notifications (delivery/skip, legacy fallback, dedup):
  **VERIFIED** (unit — `SignalNotificationServiceTest`).
- Live run (real Binance data): SPOT resolves `Trend Pullback v1` over 496 spot
  symbols; FUTURES resolves `EMA + RSI Futures v1` over 527 futures symbols;
  persisted signals carry correct `tradingMode` (SPOT list ⊆ SPOT, FUTURES ⊆
  FUTURES); Trend Pullback and News unaffected — **VERIFIED**.
- Live FCM/device push, notification-tap → detail on a device: **BLOCKED**
  (no Firebase credentials/device).
- Scheduler concurrency (parallel, non-blocking): **NOT VERIFIED** (single-thread
  scheduler).
