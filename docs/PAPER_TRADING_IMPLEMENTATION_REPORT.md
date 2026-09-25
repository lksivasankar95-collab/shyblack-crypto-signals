# Paper Trading — Implementation Report

**Date:** 2026-09-25  
**Based on:** PAPER_TRADING_GAP_ANALYSIS.md  
**Status:** All gaps implemented, tests passing, flutter analyze clean.

---

## Summary

Six gaps were identified in the gap analysis. All were fixed in this session. Three were backend entity/service/DTO changes, one was a critical isolation bug, one was a Flutter entity/model update, and one was a set of new tests.

Additionally, two pre-existing compile errors in the Strategy Builder code (from the previous session) were fixed during the build process:
- `TradingStrategyService` imported `NotFoundException` (not in the exception package) → replaced with `ResourceNotFoundException`
- `TradingStrategyService` called `principal.id()` on a Lombok `@Getter` class → replaced with `principal.getId()`
- `StrategyResolver` required an `ObjectMapper` bean that was never declared → added `JacksonConfig` with a `@Primary @Bean ObjectMapper`
- `strategy_providers.dart` used `AutoDisposeAsyncNotifier` (removed in Riverpod 3) with a family provider pattern that requires code generation → rewrote as two concrete non-family `AsyncNotifier` classes with a helper function

---

## Changes Made

### Backend

#### `entity/Position.java`
Added two new auditing columns that were missing since the Strategy Builder task:
```java
@Column private UUID strategyId;
@Column private Integer strategyVersion;
```
These are nullable, so existing rows are unaffected. New positions carry the strategy reference forward from the signal that triggered them.

#### `service/paper/PaperTradingExecutionService.java`
In `openFromSignal()`, the two new fields are now copied from the signal:
```java
p.setStrategyId(signal.getStrategyId());
p.setStrategyVersion(signal.getStrategyVersion());
```

#### `service/paper/PaperTradingEngineService.java`
Two fixes in `openPositionsForSymbol()`:
1. Removed the `@Transactional(propagation = REQUIRES_NEW, readOnly = true)` annotation — it was a dead no-op due to Spring AOP self-call bypass.
2. Changed `positionRepository.findByStatus(OPEN)` (all account types) to `positionRepository.findByStatusAndPortfolio_AccountType(OPEN, AccountType.PAPER)` — prevents the paper engine from touching live positions during tick evaluation.

#### `dto/paper/PaperPositionResponse.java`
Two new fields appended to the record:
```java
UUID strategyId,
Integer strategyVersion
```

#### `controller/PaperTradingController.java`
Updated `toPositionDto()` to pass `p.getStrategyId()` and `p.getStrategyVersion()` to the response.

#### `config/JacksonConfig.java` (new)
Declares the `ObjectMapper` bean that `StrategyResolver` requires:
```java
@Bean @Primary ObjectMapper objectMapper()
```
Configured with `JavaTimeModule`, dates as ISO-8601 strings, unknown properties ignored.

#### `service/strategy/TradingStrategyService.java`
Fixed two pre-existing compile errors:
- `import NotFoundException` → `import ResourceNotFoundException`
- `principal.id()` → `principal.getId()` (7 occurrences)

### Frontend

#### `domain/entities/paper_position.dart`
Added two optional fields:
```dart
final String? strategyId;
final int? strategyVersion;
```
Constructor updated with named optional parameters.

#### `data/models/paper_position_model.dart`
Updated `fromJson()` to parse the new fields:
```dart
strategyId: json['strategyId'] as String?,
strategyVersion: (json['strategyVersion'] as num?)?.toInt(),
```

#### `domain/entities/trading_strategy.dart`
Removed unused `import 'package:flutter/foundation.dart'` (was triggering a lint warning).

#### `presentation/providers/strategy_providers.dart`
Complete rewrite. The previous code used:
- `AutoDisposeAsyncNotifier<T>` — class doesn't exist in Riverpod 3.x
- `AsyncNotifierProvider.autoDispose.family<>()` — requires codegen in Riverpod 3
- `state.valueOrNull` — renamed to `state.asData?.value` in Riverpod 3

New approach: abstract base class `StrategyTabController extends AsyncNotifier<StrategyTabState>` with two concrete subclasses `SpotStrategyController` and `FuturesStrategyController` (each baking in their `mode`), plus two providers `spotStrategyTabProvider` and `futuresStrategyTabProvider`. A `strategyTabProvider(mode)` helper function returns the correct provider for a given mode, preserving the call-site API.

#### `presentation/screens/settings/strategy_build_screen.dart`
`StrategyTabController.provider(mode)` → `strategyTabProvider(mode)` (4 occurrences).

#### `presentation/screens/settings/create_strategy_screen.dart`
`StrategyTabController.provider(widget.mode).notifier` → `strategyTabProvider(widget.mode).notifier`.

---

## Test Results

### Backend
```
258 tests completed, 1 failed
```

The single failure is `NewsApiIntegrationTest.assetContextAggregatesProcessedArticles()` — a pre-existing flaky test documented in the project memory. All 258 other tests pass, including the 9 newly added paper trading tests:

New tests added to `PaperTradingExecutionServiceTest`:
- `openFromSignal_copiesStrategyIdAndVersion` — verifies strategyId/strategyVersion are transferred from signal to position
- `openFromSignal_short_setsCorrectSide` — verifies SHORT signal opens with correct side, SL above entry, TP below entry
- `close_short_atTP_profits_whenPriceDropsBelowTP` — verifies SHORT position closing at TP produces positive P&L

### Frontend
```
flutter analyze: 0 errors, 8 info-level lints (all pre-existing, none in changed files)
```

---

## Deferred Items (unchanged)

These remain deferred per the project memory and are not regressions:
- TP2/TP3 partial-close simulation — paper trading closes fully at TP1 by design
- FUTURES leverage/margin simulation — `margin` and `liquidationPrice` fields exist on Position but are informational only; leverage simulation is deferred
- User-data WebSocket for live position push (using 10s polling instead)
