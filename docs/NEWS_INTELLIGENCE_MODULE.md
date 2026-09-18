# News Intelligence Module

Fetches and ingests crypto news from RSS feeds, normalizes/dedups articles, classifies each
article by category, asset, sentiment, impact and scores it, then serves it through a
paginatable, filterable REST API and a Flutter (Riverpod) frontend.

> Spec: see `docs/shyblack_master_spec.md` § "News Module". Phase-1 live end-to-end is DONE;
> Phase-2 features (per-article processing backfill, ML-backed matching, economist pick, full
> analysis metadata) remain open.

---

## 1. Terms

- **Article** → `NewsArticle` (one RSS item).
- **Article-Asset** → `NewsAsset` junction row `NewsArticle.assets` (one or more per article).
- **Symbol** → e.g. `BTC`. Quotes manage the mapping; symbols are matched against the market
  catalog (`BTC`, `ETH`, `SOL`, …).

---

## 2. Out of scope / decisions

- **No AI/LLM at runtime** (matching project rule). Sentiment/impact/category is keyword+LSTM-free
  lexicon/scoring logic; no neural nets, no external NLP APIs in the request path.
- **Enums** used:
  - `NewsCategory`: `REGULATION`, `ETF`, `EXCHANGE`, `LISTING`, `DELISTING`, `PARTNERSHIP`,
    `ADOPTION`, `TECHNOLOGY`, `NETWORK`, `SECURITY`, `HACK`, `EXPLOIT`, `FUNDING`, `INVESTMENT`,
    `TOKEN_UNLOCK`, `TOKEN_BURN`, `GOVERNANCE`, `PROTOCOL_UPDATE`, `DEFI`, `NFT`, `MACRO`,
    `MARKET`, `MINING`, `LEGAL`, `OTHER`.
  - `NewsSentiment`: `POSITIVE`, `NEGATIVE`, `NEUTRAL`.
  - `NewsImpact`: `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`.
  - `NewsProcessingStatus`: `NEW`, `PROCESSED`, `PARTIALLY_PROCESSED`, `FAILED`, `UNSUPPORTED`.
  - `NewsAssetRelationshipType`: `PRIMARY`, `MENTIONED`.
- **Freshness window**: configurable (`news.freshness-window`, default e.g. 24h for ingestion; a
  "news score" decay uses `publishedAt` vs `now`).

---

## 3. Ingestion pipeline

Runs on a scheduled fixed delay (`news.sync-cron` / scheduler). For every configured feed:

1. **Fetch** → `RssNewsProvider` (Rome): digest/GMT-safe parsing; tolerant of missing fields.
2. **Normalize** (`NewsNormalizer`): canonicalized title/summary, canonical hash
   (`canonicalHash`) for dedupe, source cleanup, timestamps (`publishedAt`), enums defaulted.
3. **Dedupe** (`DuplicateDetector`): skip if same `canonicalHash` or `sourceUrl` already in DB.
4. **Classify**:
   - `AssetExtractor` → assets from title+summary auto-detected via market catalog.
   - `CategoryClassifier` → one category per article from title/summary keywords.
   - `SentimentAnalyzer` → lexicon polarity + negation handling.
   - `ImpactClassifier` → impact level from keywords (partnership→neutral low, hack/exploit→HIGH/CRITICAL).
   - `NewsScorer` → `newsScore`, `sentimentScore`, `impactScore`, per-asset `confidence`.
5. **Persist** (`NewsIngestionService`): insert article + asset junction rows; idempotent.
6. **Admin sync** (`NewsAdminController` `POST /api/v1/admin/news/sync`): trigger ingestion on
   demand (also scheduled).

---

## 4. REST API (backend)

Base: `/api/v1`

| Method | Path | Description |
|---|---|---|
| GET | `/news?page=0&size=20` | Paginated list, filters: `q`, `category`, `sentiment`, `impact`, `source`, `from`, `to`, sort/sortDirection |
| GET | `/news/{id}` | Detail: article + affected assets |
| GET | `/news/asset/{symbol}` | Paginated news for one asset |
| GET | `/news/asset/{symbol}/context` | Aggregated context for one asset |
| GET | `/news/meta` | Filters: sources, categories, sentiments, impactLevels, stats |
| GET | `/news/sources`, `/news/categories`, `/news/sentiments`, `/news/impact-levels` | Filter option lists |
| POST | `/admin/news/sync` | Admin: run ingestion+classification (role ADMIN) |

Query filters are optional; unset = not applied. `page` is 0-based. `size` default 20, max 100.

Response shapes:
- `PageResponse<NewsResponse>` for lists.
- `NewsDetailResponse`, `NewsMetaResponse`, `NewsContextResponse` for the rest.
- Paging: `{ items, page, size, totalElements, totalPages, hasNext }`.

### Backend implementation notes

- `NewsQueryService` uses **Criteria/Specification** (JPA `JpaSpecificationExecutor`) for all
  filtering, *not* monolithic `@Query` strings. This is deliberate: PostgreSQL fails with
  `function lower(bytea) does not exist` / `could not determine the data type of parameter $n`
  when null JPQL parameters are passed into `lower(...)`/`concat(...)` (untyped null → param
  inferred as bytea). Specifications compare non-null fields only, so **never** bind null params
  into `lower()`.
- Repository: `NewsArticleRepository extends JpaRepository<..., UUID>,
  JpaSpecificationExecutor<...>`. Custom derived methods only for exact lookups
  (`existsByCanonicalHash`, `existsBySourceUrl`, `findDistinctSourceNames`,
  `findProcessedForAssetSince`, …).
- `NewsAssetRepository.findByArticleIdIn` is used in batch for detail responses (no N+1).
- DTOs are immutable records in `com.shyblack.cryptosignals.dto.news`.
- Exceptions: `NewsNotFoundException`, `InvalidNewsFilterException` handled by
  `GlobalExceptionHandler`.

---

## 5. Frontend (Flutter)

Clean architecture under `frontend/lib`:

- `domain/entities/news.dart` — `NewsArticle`, `NewsArticleDetail`, `NewsPage`, `NewsMeta`,
  `NewsContext`, enums + labels.
- `domain/repositories/news_repository.dart` + `domain/usecases/get_news.dart`, `get_news_detail.dart`,
  `get_asset_news.dart`, `get_news_meta.dart`, `get_news_context.dart`.
- `data/datasources/news_remote_data_source.dart`, `data/repositories/news_repository_impl.dart`,
  `data/models/news_model.dart`.
- `core/di/providers.dart` — Riverpod providers (`newsRepositoryProvider`, `getNewsProvider`, …).
- `presentation/providers/news_providers.dart` — `NewsFeedController` (AsyncNotifier) managing
  feed state + filters + pagination; plus `newsDetailProvider`, `newsContextProvider`,
  `assetNewsProvider` (family).
- `presentation/screens/news/news_screen.dart`, `news_detail_screen.dart`, `asset_news_screen.dart`;
  `presentation/widgets/news_article_card.dart`.
- `core/constants/api_constants.dart` — endpoint helper constants (newsDetail/newsAsset/…).

### Riverpod notes

- Riverpod 3: `AsyncValue` has **no** `valueOrNull` — use `state.value` for null-safe current value.
- Presentational badges (sentiment/impact) are plain widgets (`NewsSentimentBadge`,
  `NewsImpactBadge`); `NewsImpactBadge` takes `impact:` not `impactLevel:`.
- Enums in Dart: `NewsProcessingStatus.new_` member (avoid the `new` keyword); only parsed/
  displayed, no `apiValue` serialization needed.

---

## 6. Testing

- Backend: `gradlew test` → **124 tests, 0 failures** (`NewsApiIntegrationTest`,
  `service/news/*Test`, `news/*Test`). H2 used in tests; the Criteria refactor was validated against
  a **real PostgreSQL 17** live run (feed fetch + search + admin sync all 200).
- Frontend: `flutter test` → 11 tests; `flutter analyze` → No issues. `news_screen_test.dart`
  covers list, tap→detail, empty state; providers are overridden with an in-memory fake repo.
- Live (Postgres, bootRun): all `/api/v1/news*` returns 200; a seeded+synced payload produced
  30 `news_articles` / 71 `news_article_assets`.

---

## 7. Known limitations / follow-ups

- RssNewsProvider feed-set is fixed in config (extend `news.rss.feeds` for more sources).
- Sentiment/impact/category classification is lexicon-based; accuracy improves with a labeled
  dataset + optional ML (Phase-2).
- `news_score` aggregation ("overall sentiment for BTC") is per-article today; a rolling asset
  context aggregation is Phase-2.
