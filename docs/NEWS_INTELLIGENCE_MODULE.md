# News Intelligence Module

Fetches and ingests crypto news from RSS/Atom feeds, normalizes/dedups articles, classifies each
article by category, asset, sentiment, impact and scores it, persists it, and then fans out a
post-commit **domain event** to two consumers: an FCM notification policy and a real-time
WebSocket broadcast. Read access is served through a paginatable, filterable REST API and a
Flutter (Riverpod) frontend that updates live.

> Spec: see `docs/shyblack_master_spec.md` § "News Module". Phase-1 ingestion + read API is DONE.
> Phase-2 features (per-article processing backfill, ML-backed matching, economist pick, full
> analysis metadata) remain open. The real-time event/notification/WebSocket pipeline is
> implemented (see status labels below).

This document is implementation-driven; every component named here exists in the repository.
Status labels used: **VERIFIED**, **NOT VERIFIED**, **BLOCKED**, **KNOWN ISSUE / FOLLOW-UP**.

---

## 1. Terms

- **Article** → `NewsArticle` (one RSS/Atom item).
- **Article-Asset** → `NewsAsset` junction row `NewsArticle.assets` (one or more per article).
- **Symbol** → e.g. `BTC`, matched against the market catalog.

---

## 2. Out of scope / decisions

- **No AI/LLM at runtime** (project rule). Sentiment/impact/category is lexicon/scoring logic;
  no neural nets, no external NLP APIs in the request path.
- **Enums** used:
  - `NewsCategory`: `REGULATION`, `ETF`, `EXCHANGE`, `LISTING`, `DELISTING`, `PARTNERSHIP`,
    `ADOPTION`, `TECHNOLOGY`, `NETWORK`, `SECURITY`, `HACK`, `EXPLOIT`, `FUNDING`, `INVESTMENT`,
    `TOKEN_UNLOCK`, `TOKEN_BURN`, `GOVERNANCE`, `PROTOCOL_UPDATE`, `DEFI`, `NFT`, `MACRO`,
    `MARKET`, `MINING`, `LEGAL`, `OTHER`.
  - `NewsSentiment`: `POSITIVE`, `NEGATIVE`, `NEUTRAL`.
  - `NewsImpact`: `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`.
  - `NewsProcessingStatus`: `NEW`, `PROCESSED`, `PARTIALLY_PROCESSED`, `FAILED`, `UNSUPPORTED`.
  - `NewsAssetRelationshipType`: `PRIMARY`, `MENTIONED`.
  - `NotificationCategory`: `SIGNALS`, `TRADES`, `ACCOUNT`, `SYSTEM`, **`NEWS`**.
- `NewsProcessingStatus`: an article that yields ≥1 asset is stored `PROCESSED`; an article with
  no extracted asset is stored `PARTIALLY_PROCESSED` (it is still returned by the general feed).
- **Freshness window**: `app.news.freshness-window-hours` (default 48). Used only for scoring
  decay (`NewsFreshness.weight(publishedAt, now, window)`), never to drop or reject an article.

---

## 3. Architecture (end-to-end)

```mermaid
flowchart TD
    RSS[RSS / Atom feeds] --> SCHED[NewsIngestionScheduler\n@Scheduled app.news.sync-cron]
    SCHED --> PROV[RssNewsProvider\nper-feed isolation + timeout + UA]
    PROV --> ING[NewsIngestionService\nnormalize -> classify -> score]
    ING --> DEDUP[DuplicateDetector]
    DEDUP -- duplicate --> STOP[no persistence]
    DEDUP -- new --> DB[(NewsArticle persisted\nper-article transaction)]
    DB --> EVT[NewsCreatedEvent\npublished inside tx]
    EVT --> COMMIT[AFTER COMMIT]
    COMMIT --> WS[AlertsWebSocketHandler\n/ws/private broadcast]
    COMMIT --> POLICY[NewsNotificationListener\neligibility policy]
    POLICY -- eligible --> PREF[user preference newsAlertsEnabled]
    PREF --> IDEM[existsByUserAndNewsId]
    IDEM --> FCM[FcmSenderService\nHTTP v1]
    POLICY --> HIST[(Notification history\ncategory=NEWS, newsId)]
    WS --> FCTRL[Flutter NotificationsController\ntype == news]
    FCTRL --> PREPEND[NewsFeedController.prependById newsId]
    PREPEND --> NSCR[NewsScreen shows article at top]
    FCM --> MOBILE[Mobile notification]
    MOBILE --> TAP[User tap]
    TAP --> DETAIL[NewsDetailScreen articleId = newsId]
    DB --> READ[NewsQueryService -> NewsController /api/v1/news]
```

The event is published **inside** the article's persistence transaction; the listener runs
strictly `@TransactionalEventListener(phase = AFTER_COMMIT)`, so no notification or socket work
can roll back the article.

---

## 4. Ingestion pipeline

Scheduled with `@Scheduled(cron = "${app.news.sync-cron:0 */15 * * * *}")` on
`NewsIngestionScheduler`, guarded by an `AtomicBoolean` so a slow run never overlaps itself.
`NewsProviderRegistry.activeProviders()` selects providers; today the only provider is the
keyless `RssNewsProvider`.

For every configured feed (`app.news.feeds`), `RssNewsProvider`:

1. **Fetches** over HTTP with a bounded connect/read timeout (`effectiveTimeoutSeconds`, 0/negative
   falls back to 10s) and a descriptive `User-Agent`.
2. **Parses** RSS 2.0 and Atom using the JDK DOM parser (DOCTYPE/external entities disabled;
   namespace-prefix tolerant). *(Earlier note "Rome" was incorrect — no Rome dependency.)*
3. **Isolates failures per feed**: a 403/timeout/malformed feed is logged and skipped; the
   remaining feeds still sync. Only if *all* feeds fail is a `NewsProviderException` raised.

`NewsIngestionService.sync(provider)` then, per article:

4. **Normalize** (`NewsNormalizer`): canonical URL, canonical title, `canonicalHash`
   (SHA-256 of the canonical identity), summary cleanup; `publishedAt` kept as the provider's
   original instant.
5. **Classify/score**: `AssetExtractor` (assets via market catalog), `CategoryClassifier`,
   `SentimentAnalyzer`, `ImpactClassifier`, `NewsFreshness`, `NewsScorer`
   (`newsScore`, `sentimentScore`, `impactScore`, `confidenceScore`).
6. **Dedup** (see §5).
7. **Persist** under a dedicated `TransactionTemplate` per article, so one malformed item can
   never roll back the rest, and publish `NewsCreatedEvent` inside that transaction.

Result counters are logged and returned as `NewsSyncResponse`
(`provider, articlesFetched, inserted, duplicates, rejected, failed, startedAt, finishedAt, durationMs`).
`max-articles-per-sync` is applied **after sorting the fetched batch newest-first**, so the cap
keeps the newest articles rather than an arbitrary feed-order slice.
`POST /api/v1/admin/news/sync` runs the same `syncAll()` on demand (ADMIN only).

---

## 5. Deduplication

Application-level `DuplicateDetector.check(raw, canonicalUrl, canonicalHash)` rejects, in order:

1. `source_url` — `NewsArticleRepository.existsBySourceUrl(canonicalUrl)`.
2. `external_id` — `existsByExternalNewsIdAndSourceName(externalId, sourceName)` when the feed
   supplied a GUID/id.
3. `canonical_hash` — `existsByCanonicalHash(canonicalHash)`.

The database is the final authority via unique constraints on `news_articles`:

- `uk_news_articles_canonical_hash (canonical_hash)`
- `uk_news_articles_source_url (source_url)`

A concurrent duplicate insert raises `DataIntegrityViolationException`, which `persistOne` maps
back to the `DUPLICATE` outcome. Net effect: the same story arriving from one or many feeds
produces **one** DB row, **one** `NewsCreatedEvent`, at most **one** eligible notification per
user, and **one** Flutter card.

---

## 6. Domain event

`NewsCreatedEvent(UUID newsArticleId)` (`service/news/NewsCreatedEvent.java`) carries only the id.
It is published inside the persistence transaction; `NewsNotificationListener.onNewsCreated` is
annotated `@TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true)`. It loads
the article and fans out. Emitted **once per newly inserted article**; never for duplicates.

---

## 7. Notification policy + FCM + idempotency

`NewsNotificationProperties` (`app.news-notification`):

| Property | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Master switch for news push (WebSocket broadcast still fires). |
| `min-impact` | `HIGH` | Notify when `impactLevel.ordinal() >= min-impact`. |
| `min-score` | `0` | Notify when `abs(newsScore) >= min-score`; `0` disables the score criterion. |

Eligibility is deterministic: `impactOk || scoreOk`. Low-priority articles are **still persisted
and still broadcast** over WebSocket — only the push notification is skipped. Not every RSS
article produces an FCM notification.

For each active device token (`DeviceTokenRepository.findByActiveTrue()`):

1. `NotificationPreferenceService.newsEnabledFor(user)` (backed by
   `NotificationPreference.newsAlertsEnabled`, default true).
2. Idempotency: `NotificationRepository.existsByUserAndNewsId(user, newsId)` — plus DB unique
   constraint `uk_notifications_user_news (user_id, news_id)`.
3. `FcmSenderService.sendToToken(token, title, body, data)`.
4. On success, persist a `Notification` history row: `category=NEWS`, `newsId`, `title`, `body`,
   `read=false`.

FCM `data` payload: `type="news"`, `newsId`, `source`, `publishedAt` (UTC ISO-8601), `url`.
Title: `📰 <source>`; body: article title. No secrets in the payload.

---

## 8. Real-time WebSocket

Reuses the existing private alerts socket — **no separate news WebSocket server**.

- Endpoint: **`/ws/private`** → `AlertsWebSocketHandler` (registered in
  `MarketWebSocketConfig`). Broadcast method: `broadcastAlert(JsonObject)`.
- Payload (exact fields emitted by `NewsNotificationListener.broadcast`):

```json
{ "type": "news", "newsId": "<uuid>", "title": "...", "source": "...",
  "publishedAt": "<UTC ISO-8601>", "url": "..." }
```

Broadcast is invoked once per `NewsCreatedEvent`, independent of notification eligibility, and is
wrapped in try/catch so a socket failure cannot affect persistence.

**KNOWN SECURITY FOLLOW-UP:** `/ws/private` is registered with no authentication interceptor and
`AlertsWebSocketHandler` accepts any connection (no origin/token check). This is pre-existing and
was **not** changed in this task. It should be secured (auth handshake / per-user session) in a
dedicated follow-up.

---

## 9. REST API (backend)

Base: `/api/v1`, controller `NewsController` (`/api/v1/news`), admin `NewsAdminController`
(`/api/v1/admin/news`). All `GET /news*` endpoints require authentication (Bearer JWT); anonymous
requests receive 401.

| Method | Path | Description |
|---|---|---|
| GET | `/news` | Paginated list. Filters: `q`, `category`, `sentiment`, `impact`, `source`, `from`, `to`, `page`, `size`, `sort`, `direction` |
| GET | `/news/search` | Same as `/news` (alias) |
| GET | `/news/{id}` | Detail: article + affected assets (`NewsDetailResponse`) |
| GET | `/news/asset/{symbol}` | Paginated news for one asset |
| GET | `/news/asset/{symbol}/context` | Aggregated asset context (`NewsContextResponse`) |
| GET | `/news/meta` | `totalArticles`, sources, categories, sentiments, impacts, `latestFetchedAt` |
| GET | `/news/sources`, `/news/categories`, `/news/sentiments`, `/news/levels` | Filter option lists |
| POST | `/admin/news/sync` | Run ingestion now (role `ADMIN`) |

Notes:
- `page` is 0-based; `size` default 20 (`app.news.page-size`), max 100; default sort is
  `publishedAt` **descending** (newest first). Sort whitelist: `publishedAt`, `createdAt`,
  `newsScore`, `title`, `sourceName`.
- Response shapes: `PageResponse<NewsResponse>` for lists (`{ items, page, size, totalElements,
  totalPages, hasNext }`), `NewsDetailResponse`, `NewsMetaResponse`, `NewsContextResponse` otherwise.
- `NewsResponse` fields: `id, sourceName, sourceUrl, title, summary, imageUrl, author, publishedAt,
  category, sentiment, sentimentScore, impactLevel, impactScore, newsScore, confidenceScore, assets`.

### Backend implementation notes

- `NewsQueryService` uses **Criteria/Specification** (`JpaSpecificationExecutor`) for all filtering,
  not monolithic `@Query` strings — PostgreSQL fails with `function lower(bytea) does not exist` /
  `could not determine the data type of parameter $n` when null parameters reach `lower(...)`.
  Specifications bind non-null fields only.
- `NewsArticleRepository extends JpaRepository<NewsArticle, UUID>, JpaSpecificationExecutor<...>`.
  Derived methods only for exact lookups (`existsByCanonicalHash`, `existsBySourceUrl`,
  `existsByExternalNewsIdAndSourceName`, `findDistinctSourceNames`, `findProcessedForAssetSince`, …).
- `NewsAssetRepository.findByArticleIdIn` is used in batch (no N+1).
- DTOs are immutable records in `com.shyblack.cryptosignals.dto.news`.
- `NewsNotFoundException` / `InvalidNewsFilterException` are handled by `GlobalExceptionHandler`.

---

## 10. Frontend (Flutter)

Clean architecture under `frontend/lib`:

- `domain/entities/news.dart` — `NewsArticle`, `NewsArticleDetail`, `NewsPage`, `NewsMeta`,
  `NewsContext`, enums + labels.
- `domain/repositories/news_repository.dart` + `domain/usecases/get_news.dart`,
  `get_news_detail.dart`, `get_asset_news.dart`, `get_news_meta.dart`, `get_news_context.dart`.
- `data/datasources/news_remote_data_source.dart`, `data/repositories/news_repository_impl.dart`,
  `data/models/news_model.dart`.
- `presentation/providers/news_providers.dart` — `NewsFeedController` (AsyncNotifier): feeds,
  filters, pagination, realtime prepend.
- `presentation/screens/news/news_screen.dart`, `news_detail_screen.dart`,
  `asset_news_screen.dart`; `presentation/widgets/news_article_card.dart`.
- `presentation/providers/notifications_controller.dart` — connects to `/ws/private`.

### Feed lifecycle

- **Initial load** — `NewsFeedController.build()` fetches page 0 with `sort=publishedAt`,
  `direction=desc`, `size=20`.
- **Refresh / pull-to-refresh** — `refresh()` sets `_page = 0` then re-fetches page 0 and replaces
  the list (newest articles appear; no app restart needed).
- **Pagination** — `loadMore()` fetches `_page + 1` and appends, deduplicating by article id.
- **Realtime prepend** — `prependById(id)` fetches the exact article via `getNewsDetailProvider`,
  inserts it at index 0, skips if the id is already present, and does **not** touch `_page` /
  `hasNext`, so the pagination cursor stays correct.
- **Detail** — tapping a card (or a news notification) opens
  `NewsDetailScreen(articleId: id)`, which loads via `newsDetailProvider(id)`.

Riverpod notes:
- Riverpod 3: `AsyncValue` has no `valueOrNull`; use `state.value`.
- Enums: `NewsProcessingStatus.new_` avoids the `new` keyword.
- `NewsImpactBadge` takes `impact:` (not `impactLevel:`).

---

## 11. FCM lifecycle

- Backend → **FCM HTTP v1** (`FcmSenderService`, project id + OAuth2 token from
  `FIREBASE_SERVICE_ACCOUNT_JSON` / `FIREBASE_ACCESS_TOKEN` env). Credentials are server-side only.
- Flutter `NotificationService` (`core/services/notification_service.dart`):
  - **Foreground** (`FirebaseMessaging.onMessage`): dedups by notification id, refreshes the
    notifications list, and for `type == "news"` calls `prependById(newsId)` so the article
    appears at the top without a manual refresh.
  - **Background** — the OS presents the notification; `firebaseBackgroundHandler` initializes
    Firebase.
  - **Terminated → tap** (`getInitialMessage`) and **background → tap**
    (`onMessageOpenedApp`) both call `_handleTap`.
  - `_handleTap` extracts `newsId` for `type == "news"` and sets `pendingNewsProvider`.
- `MainShell` listens to `pendingNewsProvider` and pushes `NewsDetailScreen(articleId: newsId)`,
  then clears the pending value. The screen resolves the article from the API — the payload is
  never used to hardcode content.

Realtime vs push are **separate legs**: WebSocket delivers in-app live updates; FCM delivers the
out-of-app notification.

### Verification scope
- Event → policy → WebSocket-broadcast invocation → API: **VERIFIED** (unit tests + live backend
  log/API evidence; see §14).
- Flutter `prependById`, dedup, and pagination-preservation: **VERIFIED** (Dart tests).
- Real device FCM delivery, on-device notification tap → detail, and on-device realtime
  WebSocket UI rendering: **BLOCKED** (no Firebase credentials / no mobile device during the
  verification run). Do **not** treat these as live-verified.

---

## 12. Failure isolation

- **One feed fails** → remaining feeds still sync (per-feed try/catch; only an all-feeds failure
  raises).
- **One malformed article** → rejected/isolated; the rest persist (per-article transaction).
- **FCM failure** → article remains persisted; other tokens still processed (per-token try/catch).
- **WebSocket failure** → article remains persisted (broadcast try/catch).
- **Duplicate notification** → suppressed by `existsByUserAndNewsId` + unique `(user_id, news_id)`;
  a race is swallowed and treated as already-sent.
- The FCM/WebSocket work happens **after** the article transaction commits — never inside it.

---

## 13. Security

- `GET /api/v1/news*` requires authentication (verified 401 unauthenticated).
- `POST /api/v1/admin/news/sync` requires role `ADMIN`.
- FCM credentials and device tokens are server-side only; the push payload contains no secrets.
- RSS/HTML descriptions are sanitized (`TextSanitizer` / `stripHtml`) before storage; feed XML is
  parsed with DOCTYPE/external entities disabled.
- **KNOWN ISSUE / FOLLOW-UP:** `/ws/private` has no authentication interceptor (see §8). Not fixed
  here.

---

## 14. Configuration & operations

### `app.news` (`NewsProperties`)

| Key | Env | Default | Purpose |
|---|---|---|---|
| `enabled` | `NEWS_ENABLED` | `true` | Scheduler + provider on/off |
| `sync-cron` | `NEWS_SYNC_CRON` | `0 */15 * * * *` | Scheduler cadence |
| `request-timeout-seconds` | `NEWS_REQUEST_TIMEOUT_SECONDS` | `10` | Per-request HTTP timeout (0/negative → 10) |
| `page-size` | `NEWS_PAGE_SIZE` | `20` | Default API page size |
| `max-articles-per-sync` | `NEWS_MAX_ARTICLES_PER_SYNC` | `200` | Cap per sync (newest kept) |
| `freshness-window-hours` | `NEWS_FRESHNESS_WINDOW_HOURS` | `48` | Scoring decay window |
| `api-key` | `NEWS_API_KEY` | (empty) | Reserved (no keyed provider implemented) |
| `feeds` | `APP_NEWS_FEEDS` (comma-separated) | see below | RSS/Atom feed URLs |

Configured feeds (8, in `application.yml`): `cointelegraph.com/rss`, `decrypt.co/feed`,
`bitcoinmagazine.com/feed`, `theblock.co/rss.xml`, `cryptoslate.com/feed/`,
`cryptobriefing.com/feed/`, `ambcrypto.com/feed/`, `u.today/rss`.

### `app.news-notification` (`NewsNotificationProperties`)

| Key | Env | Default |
|---|---|---|
| `enabled` | `NEWS_NOTIFY_ENABLED` | `true` |
| `min-impact` | `NEWS_NOTIFY_MIN_IMPACT` | `HIGH` |
| `min-score` | `NEWS_NOTIFY_MIN_SCORE` | `0` |

Operations:
- A backend **restart is required** after configuration changes (properties are bound at startup).
- The scheduler starts automatically after startup; manual runs via `POST /api/v1/admin/news/sync`.
- PostgreSQL must be reachable; schema is applied by Hibernate `ddl-auto` (`update` dev / `validate` prod).
- Actual FCM delivery additionally requires `FIREBASE_SERVICE_ACCOUNT_JSON` (or
  `FIREBASE_ACCESS_TOKEN`) and `FIREBASE_PROJECT_ID` set server-side. Never commit credentials.

---

## 15. Testing / verification status

**Backend:** `gradlew test` → **344 passed, 0 failed, 2 skipped** (skipped = env-gated
`NewsRuntimeSyncIT`, `TrendPullbackBaselineBacktestIT`). Relevant news tests:
`NewsApiIntegrationTest`, `service/news/*`, `news/*`, `NewsNotificationListenerTest`,
`NewsIngestionServiceTest`, `BinanceHistoricalDataProviderTest`.

**Backend build:** `gradlew build -x test` → **BUILD SUCCESSFUL**.

**Frontend:** `flutter test` → **32 passed, 0 failed, 2 skipped** (skipped = env-gated
`news_live_e2e_test`); `flutter analyze` → 7 pre-existing info lints, 0 errors. News tests:
`news_screen_test`, `news_model_parsing_test`, `news_feed_pagination_test`.

**Live backend evidence** (new build on a separate port, real Postgres + real feeds) — **VERIFIED**:
- RSS ingestion across multiple feeds: `[News] rss -> fetched=180 inserted=42 duplicates=138`,
  later `inserted=2 duplicates=198`, then steady `inserted=0 duplicates=200`.
- Persistence + newest-first REST: `GET /api/v1/news` → 200, `totalElements=274`, newest first;
  `/api/v1/news/meta` → `totalArticles=274, sources=7`.
- `NewsCreatedEvent` / AFTER_COMMIT fan-out: `[NEWS_NOTIFY] … eligible=true …` and
  `[NEWS_NOTIFY] skip … impact=… (below policy)` logged per newly inserted article.
- WebSocket broadcast invoked: `[NEWS_WS] broadcast newsId=… source=…`.

**Mobile / push limitations — BLOCKED (not verified):**
- Real FCM device delivery (no Firebase credentials / device).
- Physical-device notification tap → exact detail.
- Physical-device realtime WebSocket UI update.

---

## 16. Known limitations / follow-ups

- **`/ws/private` is unauthenticated** (see §8) — secure in a dedicated follow-up.
- FCM is attempted synchronously inside the AFTER_COMMIT listener (does not hold the article
  transaction, but runs on the scheduler thread per token); consider async if token volume grows.
- Sentiment/impact/category classification is lexicon-based; accuracy improves with a labeled
  dataset + optional ML (Phase-2).
- `news_score` aggregation is per-article; a rolling per-asset context is Phase-2.
- `NewsProperties.apiKey` is bound but unused (no keyed provider implemented).
- `max-articles-per-sync` bounds each sync; very long backfills would need pagination across syncs.
- Real device FCM / notification-tap / realtime UI verification remains outstanding (§15).
