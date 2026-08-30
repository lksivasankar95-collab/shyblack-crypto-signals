# ShyBlack Crypto Signals — Master Project Spec

> Paste this whole document into Cursor AI as project context before starting development. Build screen by screen, in the phase order below.

---

## 1. Project Overview

Personal, production-grade Flutter app: crypto portfolio tracker + buy/sell signal platform, combined. Not intended for public app store launch — built for personal use, but engineered to production standards.

---

## 2. Tech Stack

| Layer | Choice |
|---|---|
| Mobile Framework | Flutter (Riverpod for state management) |
| Backend | Spring Boot (Kotlin) |
| Database | PostgreSQL |
| Hosting (backend) | Railway or Render (start), migrate to AWS/GCP if scaling later |
| Auth | Spring Security + JWT (access + refresh token), Google OAuth2 login |
| Real-time prices | Binance WebSocket (primary) |
| Historical/backup prices | CoinGecko API |
| Market sentiment | Alternative.me Fear & Greed Index |
| API docs | Springdoc OpenAPI (Swagger) |
| Backend testing | JUnit 5 + Mockito |

**Note:** Earlier draft used Firebase Auth/Firestore — superseded. Spring Boot + PostgreSQL is the current decision for the fresh build.

---

## 3. Architecture

**Backend:** Layered — `Controller → Service → Repository → Entity`, with DTOs decoupling API responses from DB entities. Global exception handling via `@ControllerAdvice`. Environment config via Spring Profiles (`application-dev.yml` / `application-prod.yml`), secrets never hardcoded.

**Frontend (Flutter):** Clean Architecture — `core / data / domain / presentation` layers. Fully online, backend-synced (no offline-first, no local Hive storage — cache-only layer if needed).

**Signal Engine:** Heavy computation (RSI, MACD, EMA, etc.) runs backend-side via scheduled jobs (`@Scheduled`), never on-device. Results pushed to app via WebSocket/SSE, not client polling.

---

## 4. Design System

- Background: `#0D0D0D`
- Cards: `#1A1A1A`
- Accent (primary/positive): `#00E676` (neon green)
- Negative/loss: `#FF3B30` — reserved exclusively for losses/negative values
- Fresh visual design (not reusing any old ShyBlack theme/layout)

---

## 5. Final Screen List (consolidated from planning + reference mockups)

### Bottom Navigation (final — 5 tabs, rest under a "More"/profile menu to avoid overcrowding)
`Signals | Markets | Analysis | Portfolio | Settings`
(News and Backtesting accessible from within Analysis/Markets or a secondary nav — avoid a 7-tab bottom bar.)

### A. Onboarding & Auth
- Splash/Intro Screen (carousel: Market Analysis, High Probability Signals, Risk Management, Performance Tracking)
- Login Screen (email/password + Google Sign-In)
- Sign Up Screen
- Forgot Password Screen

### B. Signals Module
- Signals Feed (tabs: Active / Pending / Closed / Drafts; stats: active count, win rate, total PnL, avg return)
- Signal Detail Screen (confidence %, entry/target/stop-loss, candlestick + volume chart, suggested position size at 2% risk, strategy win rate, technical analysis tab, updates tab, related signals tab, disclaimer, Set Alert / Share / Track Signal actions)
- Signal History
- Watchlist

### C. Portfolio Module
- Portfolio Overview (Live/Paper account toggle, total balance, available balance, invested, total PnL, trade stats, win rate, profit factor)
- Account / Trading / Open Positions / Closed Positions sub-sections
- Open Positions table (pair, long/short, size, entry/current price, unrealized PnL, margin, liq. price, position value, add/reduce)
- Add Holding / Add Transaction Screen
- Coin Detail Screen (shared with Markets — price chart w/ volume, multi-timeframe, market stats, key stats, About section, Similar Coins, Trade CTA)

### D. Markets Module
- Markets List (Watchlist / All Markets / Top Gainers / Top Losers / New Listings, filters by category/currency/timeframe)
- Coin Detail Screen (see above, shared)

### E. Analysis Module
- Market Overview (market trend, BTC dominance, altcoin season index, total market cap, top gainers/losers, AI key insights)
- Technical Analysis (indicator grid — RSI, MACD, Stochastic, ADX, CCI, Williams %R, Bollinger Bands, Ichimoku; technical summary gauge; support/resistance levels)
- On-Chain Analysis
- Sentiment
- Reports
- Backtesting (strategy config: trading mode, account, leverage, margin mode, strategy, asset, timeframe, data source, date range, initial capital → performance summary, equity curve, trade statistics, monthly returns, profit-by-day heatmap)

### F. News Module
- News Feed (categories: Market Updates, Regulation, DeFi, NFT, Exchanges; top story banner; market impact sidebar)

### G. Notifications
- Notifications Center (filters: All/Signals/Trades/Account/System/News; grouped Today/Yesterday/Older; mark all as read; manage preferences link)

### H. Settings & Profile
- Profile Screen (member since, membership tier, subscription validity, profile info, trading preferences — mode/quote currency/risk profile/leverage view, quick actions: change password/security/download data/delete account)
- Settings Screen (trading mode selector, paper vs live account toggle, connect exchange accounts, signal preferences, notifications, theme, language, data management, help & support, about)

### I. Misc
- Error/Empty states (no internet, no data, no holdings)
- Loading/skeleton states

---

## 6. MVP Phasing

**Phase 1 (MVP — build first):**
Splash → Login/Signup → Signals Feed + Signal Detail → Portfolio Overview + Open/Closed Positions → Coin Detail → Markets List → Basic Settings/Profile → Notifications Center

**Phase 2 (after MVP works end-to-end):**
Full Analysis module (Technical/On-Chain/Sentiment/Reports) → Backtesting → News → Watchlist refinements → Add Transaction flows

---

## 7. Data Sources Reference

- Binance WebSocket — live price feed (primary)
- CoinGecko API — historical/backup price data
- Alternative.me Fear & Greed Index — sentiment

---

## 8. Build Instructions for Cursor

1. Scaffold backend first: Spring Boot + Kotlin + PostgreSQL, set up entities for User, Portfolio, Position, Transaction, Signal, Watchlist, Notification.
2. Set up JWT auth + Google OAuth2 before any other feature.
3. Build Signal Engine as a separate service with scheduled jobs; expose via REST + WebSocket.
4. Scaffold Flutter app with Clean Architecture folders (`core/data/domain/presentation`) and Riverpod providers before writing any screen UI.
5. Build Phase 1 screens end-to-end (UI + wired to real backend, not mock data) before starting Phase 2.
6. Apply the design system (colors above) globally via a theme file — do not hardcode colors per screen.
