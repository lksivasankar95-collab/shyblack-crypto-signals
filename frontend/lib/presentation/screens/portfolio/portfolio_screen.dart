import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../../../domain/entities/portfolio_account.dart';
import '../../providers/portfolio_controller.dart';
import '../../providers/settings_controller.dart';
import '../../widgets/portfolio_capital_sheet.dart';
import '../../widgets/portfolio_position_sheets.dart';
import '../../widgets/portfolio_order_sheets.dart';
import '../../widgets/portfolio_widgets.dart';

/// Binance-style, read-only Portfolio.
///
/// Structure:
///
/// ```
/// PORTFOLIO   [PAPER ACCOUNT | LIVE ACCOUNT]   <- read-only badge, not a control
/// [SPOT] [FUTURES] [OPTIONS]                   <- the only navigation
/// ```
///
/// **There is deliberately no PAPER / LIVE selector here.** The account mode is the one
/// configured in Settings (`AppSettings.tradingAccount`), and this screen is a pure
/// projection of it. Switching account mode in Settings changes this screen; nothing on
/// this screen can change the mode. That is what makes it impossible to see paper data
/// under a live label, or to reach a paper account by accident from a live one.
///
/// The tabs are market accounts (SPOT, FUTURES, OPTIONS). MAIN is not offered: it is the
/// backend's aggregate read-model scope, not an account a user owns.
///
/// This screen is a view layer only. It contains no order, close or cancel control: LIVE
/// execution lives on the dedicated live/futures screens and PAPER execution on the paper
/// screen, reachable from Settings.
class PortfolioScreen extends ConsumerWidget {
  const PortfolioScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Scaffold(
      backgroundColor: AppColors.background,
      body: SafeArea(
        child: Column(
          children: [
            const _Header(),
            const Divider(color: Color(0xFF2A2A2A), height: 1),
            Expanded(child: const _ScopeBody()),
          ],
        ),
      ),
    );
  }
}

/// Title, the read-only account badge, and the market-account tabs.
///
/// The badge names which account the figures below belong to. It is deliberately not
/// tappable: changing the account mode happens in Settings, never here.
class _Header extends ConsumerWidget {
  const _Header();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final category = ref.watch(portfolioCategoryProvider);
    final mode = ref.watch(portfolioAccountModeProvider);

    return Padding(
      padding: const EdgeInsets.fromLTRB(14, 12, 14, 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Text(
                'PORTFOLIO',
                style: TextStyle(
                  color: AppColors.accent,
                  fontSize: 15,
                  fontWeight: FontWeight.w800,
                  letterSpacing: 1.2,
                ),
              ),
              const Spacer(),
              // Flexible so the badge ellipsises rather than overflowing when
              // the row is tight on a narrow phone. The mode is never dropped:
              // an ellipsised badge still names the account.
              if (mode != null)
                Flexible(child: PortfolioAccountBadge(mode: mode)),
              // Capital management is a simulated-funds concern, so the control
              // exists only while Settings has resolved the account as PAPER. It
              // is absent — not merely disabled — in LIVE mode, so there is no
              // live screen from which a simulated balance could be changed.
              if (mode == PortfolioMode.paper)
                IconButton(
                  key: const Key('paper-capital-management'),
                  onPressed: () => PortfolioCapitalSheet.show(context),
                  icon: const Icon(
                    Icons.tune,
                    color: AppColors.accent,
                    size: 20,
                  ),
                  tooltip: 'Paper account capital',
                  // A tighter footprint than the Material default; 40px stays
                  // above the 32px minimum touch target while leaving room for
                  // the badge on a small screen.
                  visualDensity: VisualDensity.compact,
                  constraints: const BoxConstraints(
                    minWidth: 40,
                    minHeight: 40,
                  ),
                  padding: EdgeInsets.zero,
                ),
            ],
          ),
          const SizedBox(height: 12),
          PortfolioCategoryTabs(
            selected: category,
            onChanged: (next) =>
                ref.read(portfolioCategoryProvider.notifier).select(next),
          ),
        ],
      ),
    );
  }
}

class _ScopeBody extends ConsumerWidget {
  const _ScopeBody();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final scope = ref.watch(portfolioScopeProvider);

    // Settings has not resolved which account the user selected. No request is issued
    // and no figure is rendered, because guessing PAPER here could show a simulated
    // account to a user who actually chose their live one.
    if (scope == null) {
      return _AccountModeUnavailable(
        onRetry: () => ref.invalidate(settingsControllerProvider),
      );
    }

    final async = ref.watch(portfolioControllerProvider);
    return async.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (error, stack) {
        // The account mode itself could not be resolved, which is a Settings problem
        // rather than a portfolio transport problem, and must not be presented as one.
        if (error is PortfolioAccountModeUnresolved) {
          return _AccountModeUnavailable(
            onRetry: () => ref.invalidate(settingsControllerProvider),
          );
        }
        return _ErrorState(
          onRetry: () => ref.invalidate(portfolioControllerProvider),
        );
      },
      data: (data) => _PortfolioBody(data: data, scope: scope),
    );
  }
}

/// Explicit state for "we do not know which account mode you selected".
///
/// This is never replaced by a paper fallback, because a paper fallback would be a
/// different account from the one the user asked for.
class _AccountModeUnavailable extends StatelessWidget {
  const _AccountModeUnavailable({required this.onRetry});

  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(14),
      children: [
        const PortfolioStatePanel(
          title: 'Account mode unavailable',
          message:
              'The account mode selected in Settings could not be read, so no portfolio can be '
              'shown. Nothing is displayed rather than guessing between a paper account and a '
              'live one.',
          icon: Icons.help_outline,
          isError: true,
        ),
        const SizedBox(height: 12),
        OutlinedButton(
          onPressed: onRetry,
          style: OutlinedButton.styleFrom(
            foregroundColor: AppColors.accent,
            side: const BorderSide(color: AppColors.accent),
            minimumSize: const Size.fromHeight(44),
          ),
          child: const Text('RETRY'),
        ),
      ],
    );
  }
}

class _ErrorState extends StatelessWidget {
  const _ErrorState({required this.onRetry});

  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(14),
      children: [
        const PortfolioStatePanel(
          title: 'Could not load this account',
          message:
              'The portfolio could not be read. This is a transport or server problem, not an '
              'empty account.',
          icon: Icons.cloud_off,
          isError: true,
        ),
        const SizedBox(height: 12),
        OutlinedButton(
          onPressed: onRetry,
          style: OutlinedButton.styleFrom(
            foregroundColor: AppColors.accent,
            side: const BorderSide(color: AppColors.accent),
            minimumSize: const Size.fromHeight(44),
          ),
          child: const Text('RETRY'),
        ),
      ],
    );
  }
}

class _PortfolioBody extends ConsumerWidget {
  const _PortfolioBody({required this.data, required this.scope});

  final PortfolioViewData data;
  final PortfolioScope scope;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    // A response that does not belong to the scope on screen is discarded rather than
    // painted, which is the guard against one account mode's values appearing under
    // another mode's label. Normally unreachable, because changing the scope rebuilds
    // the request; shown as a static, non-animating state rather than a spinner so it
    // can never become an endless loading indicator.
    if (!data.matchesScope) {
      return const Center(
        child: Padding(
          padding: EdgeInsets.all(24),
          child: Text(
            'Switching account scope...',
            style: TextStyle(color: AppColors.muted, fontSize: 12),
          ),
        ),
      );
    }

    final account = data.account;
    final isOptions = scope.category == PortfolioCategory.options;
    final isSpot = scope.category == PortfolioCategory.spot;
    final sections = PortfolioSection.forCategory(scope.category);
    final section = ref.watch(portfolioSectionProvider);

    return RefreshIndicator(
      onRefresh: () async {
        ref.invalidate(portfolioControllerProvider);
        ref.invalidate(portfolioOpenOrdersProvider);
        ref.invalidate(portfolioHoldingsProvider);
        ref.invalidate(portfolioHistoryControllerProvider);
        ref.invalidate(portfolioClosedPositionsProvider);
        ref.invalidate(portfolioTransactionControllerProvider);
        ref.invalidate(portfolioFundingControllerProvider);
        ref.invalidate(portfolioSyncStatusControllerProvider);
      },
      child: Column(
        children: [
          // Exactly one section is mounted at a time. Stacking a wallet, an order
          // book and a trade history into one scrolling list is unreadable on a
          // phone and buries whichever section the user opened the screen for.
          if (sections.isNotEmpty)
            PortfolioSectionTabs(
              sections: sections,
              selected: section,
              onChanged: (next) =>
                  ref.read(portfolioSectionProvider.notifier).select(next),
            ),
          const SizedBox(height: 8),
          Expanded(
            child: ListView(
              padding: const EdgeInsets.fromLTRB(14, 6, 14, 32),
              children: [
                if (isOptions)
                  const _OptionsUnsupported()
                else if (account.availability ==
                    PortfolioAvailability.notConnected)
                  _LiveConnectionUnavailable(account: account)
                else ...[
                  _Summary(account: account, scope: scope),
                  const SizedBox(height: 14),
                  if (account.availability.isUnsupported)
                    _UnsupportedScope(account: account)
                  else if (account.availability ==
                      PortfolioAvailability.unavailable)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 12),
                      child: PortfolioStatePanel(
                        title: 'Summary unavailable',
                        message:
                            account.statusMessage ??
                            'No summary figure can be shown for this account without '
                                'inventing one.',
                        icon: Icons.info_outline,
                      ),
                    ),

                  // Spot is an exchange wallet, not a leveraged position book, so
                  // its first section is holdings; futures shows open positions.
                  // A PAPER holding is a paper Position — the backend reports no
                  // wallet assets for a simulated account — so the holdings
                  // section shows those positions, which is also what makes
                  // close / stop-target reachable for a paper spot holding.
                  ...switch (section) {
                    PortfolioSection.holdings =>
                      scope.mode == PortfolioMode.paper
                          ? const [
                              PortfolioSectionHeader(label: 'CURRENT HOLDINGS'),
                              SizedBox(height: 10),
                              _PositionsSection(embeddedHeader: true),
                            ]
                          : const [_HoldingsSection()],
                    PortfolioSection.positions => const [
                      _PositionsSection(),
                      SizedBox(height: 18),
                      _ClosedPositionsSection(),
                    ],
                    PortfolioSection.openOrders => const [_OpenOrdersSection()],
                    PortfolioSection.tradeHistory => [
                      const _HistorySection(),
                      const SizedBox(height: 18),
                      const _TransactionHistorySection(),
                      // Funding is a futures-only income type; spot publishes none.
                      if (!isSpot) ...[
                        const SizedBox(height: 18),
                        const _FundingSection(),
                      ],
                    ],
                  },
                  const SizedBox(height: 18),
                  const _SyncStatusSection(),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }
}

/// The live account could not be reached.
///
/// Explicitly says so instead of falling back to paper data. Falling back would show a
/// simulated balance under a live account label, which is the single worst failure this
/// screen must not have.
class _LiveConnectionUnavailable extends StatelessWidget {
  const _LiveConnectionUnavailable({required this.account});

  final PortfolioAccount account;

  @override
  Widget build(BuildContext context) {
    return PortfolioStatePanel(
      title: 'LIVE ACCOUNT',
      message:
          account.statusMessage ??
          'Connection unavailable. No Binance account data can be read for this scope, and no '
              'simulated data is shown in its place.',
      icon: Icons.cloud_off,
      isError: true,
    );
  }
}

class _UnsupportedScope extends StatelessWidget {
  const _UnsupportedScope({required this.account});

  final PortfolioAccount account;

  @override
  Widget build(BuildContext context) {
    return PortfolioStatePanel(
      title: 'Not supported',
      message:
          account.statusMessage ??
          'This account has no such capability, so no data can be shown.',
      icon: Icons.info_outline,
    );
  }
}

/// Options has no engine, no account and no exchange API. It stays a clearly
/// unsupported state rather than an empty account or a fabricated balance.
class _OptionsUnsupported extends StatelessWidget {
  const _OptionsUnsupported();

  @override
  Widget build(BuildContext context) {
    return const PortfolioStatePanel(
      title: 'Options coming soon',
      message:
          'Options trading is a reserved capability. No engine, account or exchange options API is '
          'integrated, so no balance, position, order or history can be shown. Nothing is '
          'estimated in the meantime.',
      icon: Icons.block,
    );
  }
}

// ------------------------------------------------------------------ account summary

class _Summary extends StatelessWidget {
  const _Summary({required this.account, required this.scope});

  final PortfolioAccount account;
  final PortfolioScope scope;

  @override
  Widget build(BuildContext context) {
    final quote = account.quoteCurrency;
    final live = account.accountMode == PortfolioMode.live;
    final futures = account.accountCategory == PortfolioCategory.futures;

    return PortfolioCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Flexible on the title and the exchange name: the account title can be long
          // ("Binance Futures Account") and both the availability chip and the exchange
          // name must stay visible beside it on a narrow screen, so the two text
          // children yield rather than overflowing the row.
          Row(
            children: [
              Flexible(
                child: Text(
                  _accountTitle(scope),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    color: AppColors.onCard,
                    fontSize: 13,
                    fontWeight: FontWeight.w800,
                  ),
                ),
              ),
              const SizedBox(width: 8),
              PortfolioAvailabilityChip(availability: account.availability),
              if (account.exchange != null) ...[
                const SizedBox(width: 8),
                Flexible(
                  child: Text(
                    account.exchange!,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      color: AppColors.muted,
                      fontSize: 10,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ),
              ],
            ],
          ),
          const SizedBox(height: 14),
          Wrap(
            spacing: 18,
            runSpacing: 14,
            children: [
              // A futures wallet has its own vocabulary, so its figures are labelled the way
              // the exchange labels them rather than as a generic equity/available pair.
              PortfolioKpi(
                label: futures ? 'Wallet Balance' : 'Total Balance',
                value: PortfolioValue(
                  value: account.equity,
                  suffix: quote,
                  emphasise: true,
                ),
              ),
              PortfolioKpi(
                label: 'Available',
                value: PortfolioValue(
                  value: account.availableBalance,
                  suffix: quote,
                ),
              ),
              if (futures)
                PortfolioKpi(
                  label: 'Used Margin',
                  value: PortfolioValue(value: account.invested, suffix: quote),
                )
              else if (!live)
                PortfolioKpi(
                  label: 'Invested',
                  value: PortfolioValue(value: account.invested, suffix: quote),
                ),
              if (futures)
                PortfolioKpi(
                  label: 'Unrealized P&L',
                  value: PortfolioValue(
                    value: account.unrealizedPnl,
                    suffix: quote,
                    signed: true,
                  ),
                ),
              PortfolioKpi(
                label: 'Realized P&L',
                value: PortfolioValue(
                  value: account.realizedPnl,
                  suffix: quote,
                  signed: true,
                ),
              ),
            ],
          ),
          if (account.lastSyncedAt != null) ...[
            const SizedBox(height: 12),
            Text(
              'Last sync ${account.lastSyncedAt!.toIso8601String().substring(0, 19)}Z',
              style: const TextStyle(color: AppColors.muted, fontSize: 10),
            ),
          ],
          if (account.statusMessage != null &&
              account.statusMessage!.isNotEmpty &&
              account.availability.isAvailable) ...[
            const SizedBox(height: 10),
            Text(
              account.statusMessage!,
              style: const TextStyle(
                color: AppColors.muted,
                fontSize: 11,
                height: 1.4,
              ),
            ),
          ],
        ],
      ),
    );
  }

  /// Names the account the figures belong to, so a paper and a live balance can never
  /// be read as the same account.
  static String _accountTitle(PortfolioScope scope) {
    switch (scope.category) {
      case PortfolioCategory.futures:
        return scope.mode == PortfolioMode.live
            ? 'Binance Futures Account'
            : 'Paper Futures Account';
      case PortfolioCategory.spot:
      case PortfolioCategory.main:
      case PortfolioCategory.options:
        return scope.mode == PortfolioMode.live
            ? 'Binance Spot Account'
            : 'Paper Spot Account';
    }
  }
}

// ------------------------------------------------------------------ spot holdings

/// Per-asset wallet assets.
///
/// Deliberately labelled holdings and never "positions": Binance Spot has no
/// open-position concept, and a wallet asset balance is not a leveraged position.
class _HoldingsSection extends ConsumerWidget {
  const _HoldingsSection();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final mode = ref.watch(portfolioAccountModeProvider);
    final holdings = ref.watch(portfolioHoldingsProvider);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const PortfolioSectionHeader(label: 'WALLET / ASSETS'),
        const SizedBox(height: 10),
        holdings.when(
          loading: () => const PortfolioCard(
            child: Text(
              'Loading assets...',
              style: TextStyle(color: AppColors.muted, fontSize: 12),
            ),
          ),
          error: (error, stack) {
            if (error is PortfolioAccountModeUnresolved) {
              return const SizedBox.shrink();
            }
            return const PortfolioStatePanel(
              title: 'Assets unavailable',
              message:
                  'Wallet balances could not be read. This is a transport or server problem, not '
                  'an empty wallet.',
              icon: Icons.cloud_off,
              isError: true,
            );
          },
          data: (data) {
            // Holdings are a property of the spot wallet, so only the mode is matched.
            // The category is deliberately not compared: a FUTURES selection must still
            // see that the spot wallet holds assets rather than a response silently
            // disappearing.
            if (mode != null && data.accountMode != mode) {
              return const SizedBox.shrink();
            }
            if (data.isEmptyBecauseUnsupported) {
              return PortfolioStatePanel(
                title: 'No wallet assets',
                message:
                    data.statusMessage ??
                    'This account does not expose per-asset wallet balances.',
                icon: Icons.info_outline,
              );
            }
            if (data.holdings.isEmpty) {
              return const PortfolioCard(
                child: Text(
                  'No assets held in this wallet.',
                  style: TextStyle(color: AppColors.muted, fontSize: 12),
                ),
              );
            }
            return Column(
              children: data.holdings
                  .map(
                    (h) => Padding(
                      padding: const EdgeInsets.only(bottom: 8),
                      child: _HoldingRow(holding: h),
                    ),
                  )
                  .toList(),
            );
          },
        ),
      ],
    );
  }
}

class _HoldingRow extends StatelessWidget {
  const _HoldingRow({required this.holding});

  final PortfolioHolding holding;

  @override
  Widget build(BuildContext context) {
    return PortfolioCard(
      padding: const EdgeInsets.all(12),
      // Wrap rather than Row: four labelled figures plus the asset name do not fit a
      // narrow phone, and an overflow would be worse than a wrapped row.
      child: Wrap(
        spacing: 18,
        runSpacing: 12,
        crossAxisAlignment: WrapCrossAlignment.center,
        children: [
          SizedBox(
            width: 88,
            child: Text(
              holding.asset,
              style: const TextStyle(
                color: AppColors.onCard,
                fontSize: 13,
                fontWeight: FontWeight.w800,
              ),
            ),
          ),
          PortfolioKpi(
            label: 'Available',
            value: PortfolioValue(value: holding.free),
          ),
          PortfolioKpi(
            label: 'Locked',
            value: PortfolioValue(value: holding.locked),
          ),
          PortfolioKpi(
            label: 'Total',
            value: PortfolioValue(value: holding.total, emphasise: true),
          ),
        ],
      ),
    );
  }
}

// ------------------------------------------------------------------ futures positions

/// Open futures positions, sourced from the exchange's own position state.
class _PositionsSection extends ConsumerWidget {
  const _PositionsSection({this.embeddedHeader = false});

  /// True when a caller has already rendered a header for this section, so the
  /// section does not repeat it.
  final bool embeddedHeader;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final scope = ref.watch(portfolioScopeProvider);
    final data = ref.watch(portfolioControllerProvider).asData?.value;
    final positions = data?.positions;
    final account = data?.account;

    if (scope == null || positions == null || account == null) {
      return const SizedBox.shrink();
    }

    // A scope with no position concept is not the same as a supported account holding
    // nothing, and is never rendered as a count of zero.
    if (positions.isEmptyBecauseUnsupported) {
      return PortfolioStatePanel(
        title: positions.availability.isUnsupported
            ? 'No position concept'
            : 'Positions unavailable',
        message:
            positions.statusMessage ??
            'This scope does not expose positions, so none are shown.',
        icon: Icons.info_outline,
      );
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (!embeddedHeader) ...[
          PortfolioSectionHeader(
            label: 'OPEN POSITIONS',
            trailing: account.openPositionCount == null
                ? null
                : '${account.openPositionCount} open',
          ),
          const SizedBox(height: 10),
        ],
        if (positions.positions.isEmpty)
          const PortfolioCard(
            child: Text(
              'No open positions in this account.',
              style: TextStyle(color: AppColors.muted, fontSize: 12),
            ),
          )
        else
          ...positions.positions.map(
            (p) => Padding(
              padding: const EdgeInsets.only(bottom: 10),
              child: _PositionCard(position: p),
            ),
          ),
      ],
    );
  }
}

class _PositionCard extends StatelessWidget {
  const _PositionCard({required this.position});

  final PortfolioPosition position;

  @override
  Widget build(BuildContext context) {
    final quote = _quoteFor(position);
    final isLong = position.side == 'LONG';

    // The whole card opens the detail sheet: on a phone a full-width tap target
    // beats a small chevron, and the detail carries the close / stop-target
    // actions keyed on this row's own position id.
    return GestureDetector(
      key: Key('position-card-${position.positionId ?? position.symbol}'),
      onTap: () => PortfolioPositionSheets.showDetail(context, position),
      child: PortfolioCard(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Text(
                  position.symbol,
                  style: const TextStyle(
                    color: AppColors.onCard,
                    fontSize: 14,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(width: 8),
                if (position.side != null)
                  Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 6,
                      vertical: 2,
                    ),
                    decoration: BoxDecoration(
                      color: isLong
                          ? AppColors.accent.withValues(alpha: 0.16)
                          : AppColors.loss.withValues(alpha: 0.16),
                      borderRadius: BorderRadius.circular(5),
                    ),
                    child: Text(
                      position.side!,
                      style: TextStyle(
                        color: isLong ? AppColors.accent : AppColors.loss,
                        fontSize: 9,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                  ),
                if (position.leverage != null) ...[
                  const SizedBox(width: 6),
                  Text(
                    '${position.leverage}x',
                    style: const TextStyle(
                      color: AppColors.muted,
                      fontSize: 10,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ],
                const Spacer(),
                if (position.status != null)
                  Text(
                    position.status!,
                    style: const TextStyle(color: AppColors.muted, fontSize: 9),
                  ),
              ],
            ),
            const SizedBox(height: 10),
            Wrap(
              spacing: 16,
              runSpacing: 10,
              children: [
                PortfolioKpi(
                  label: 'Quantity',
                  value: PortfolioValue(value: position.quantity),
                ),
                PortfolioKpi(
                  label: 'Entry',
                  value: PortfolioValue(
                    value: position.entryPrice,
                    suffix: quote,
                  ),
                ),
                PortfolioKpi(
                  label: 'Mark',
                  value: PortfolioValue(
                    value: position.currentPrice,
                    suffix: quote,
                  ),
                ),
                PortfolioKpi(
                  label: 'Unrealized',
                  value: PortfolioValue(
                    value: position.unrealizedPnl,
                    suffix: quote,
                    signed: true,
                  ),
                ),
                if (position.liquidationPrice != null)
                  PortfolioKpi(
                    label: 'Liq.',
                    value: PortfolioValue(
                      value: position.liquidationPrice,
                      suffix: quote,
                    ),
                  ),
                // SL and the TP ladder are only rendered when the source actually supplies
                // them. Nothing is derived or defaulted here.
                PortfolioKpi(
                  label: 'SL',
                  value: PortfolioValue(
                    value: position.stopLoss,
                    suffix: quote,
                  ),
                ),
                PortfolioKpi(
                  label: 'TP1',
                  value: PortfolioValue(
                    value: position.takeProfit1,
                    suffix: quote,
                  ),
                ),
                if (position.takeProfit2 != null ||
                    position.takeProfit3 != null) ...[
                  PortfolioKpi(
                    label: 'TP2',
                    value: PortfolioValue(
                      value: position.takeProfit2,
                      suffix: quote,
                    ),
                  ),
                  PortfolioKpi(
                    label: 'TP3',
                    value: PortfolioValue(
                      value: position.takeProfit3,
                      suffix: quote,
                    ),
                  ),
                ],
              ],
            ),
          ],
        ),
      ),
    );
  }

  /// Quote currency is not carried on a position, so the common exchange quote is used
  /// for labelling only; no value is computed from it.
  String? _quoteFor(PortfolioPosition position) =>
      position.accountMode == PortfolioMode.paper ? null : 'USDT';
}

// ------------------------------------------------------------------ open orders

/// Orders the exchange currently reports as resting.
///
/// An order whose state cannot be determined is listed with an UNKNOWN chip rather than
/// hidden or shown as filled.
class _OpenOrdersSection extends ConsumerWidget {
  const _OpenOrdersSection();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final scope = ref.watch(portfolioScopeProvider);
    final orders = ref.watch(portfolioOpenOrdersProvider);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        PortfolioSectionHeader(
          label: 'OPEN ORDERS',
          trailing: orders.asData?.value.orders.isNotEmpty == true
              ? '${orders.asData!.value.orders.length} open'
              : null,
        ),
        const SizedBox(height: 10),
        orders.when(
          loading: () => const PortfolioCard(
            child: Text(
              'Loading open orders...',
              style: TextStyle(color: AppColors.muted, fontSize: 12),
            ),
          ),
          error: (error, stack) {
            if (error is PortfolioAccountModeUnresolved) {
              return const SizedBox.shrink();
            }
            return const PortfolioStatePanel(
              title: 'Open orders unavailable',
              message:
                  'The exchange open-order state could not be read. This is a transport or '
                  'server problem, not an account with no open orders.',
              icon: Icons.cloud_off,
              isError: true,
            );
          },
          data: (data) {
            if (scope == null ||
                data.accountMode != scope.mode ||
                data.accountCategory != scope.category) {
              return const SizedBox.shrink();
            }
            if (data.isEmptyBecauseUnsupported) {
              return PortfolioStatePanel(
                title: data.availability.isUnsupported
                    ? 'No open-order book'
                    : 'Open orders unavailable',
                message:
                    data.statusMessage ??
                    'This scope does not expose resting orders.',
                icon: Icons.info_outline,
              );
            }
            if (data.orders.isEmpty) {
              return const PortfolioCard(
                child: Text(
                  'No open orders.',
                  style: TextStyle(color: AppColors.muted, fontSize: 12),
                ),
              );
            }
            return Column(
              children: data.orders
                  .map(
                    (o) => Padding(
                      padding: const EdgeInsets.only(bottom: 8),
                      child: _OrderCard(order: o),
                    ),
                  )
                  .toList(),
            );
          },
        ),
      ],
    );
  }
}

class _OrderCard extends StatelessWidget {
  const _OrderCard({required this.order});

  final PortfolioOrder order;

  @override
  Widget build(BuildContext context) {
    // Full-width tap target on a phone; the detail sheet carries View Details and
    // — only when the backend says it would be accepted — Cancel Order.
    return GestureDetector(
      key: Key(
        'order-card-${order.clientOrderId ?? order.orderId ?? order.symbol}',
      ),
      onTap: () => PortfolioOrderSheets.showOrderDetail(context, order),
      child: PortfolioCard(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Text(
                  order.symbol,
                  style: const TextStyle(
                    color: AppColors.onCard,
                    fontSize: 13,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(width: 8),
                if (order.side != null)
                  Text(
                    order.side!,
                    style: const TextStyle(
                      color: AppColors.muted,
                      fontSize: 9,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                const Spacer(),
                PortfolioOrderStatusChip(status: order.status),
              ],
            ),
            const SizedBox(height: 6),
            Text(
              order.orderType,
              style: const TextStyle(color: AppColors.muted, fontSize: 10),
            ),
            const SizedBox(height: 10),
            Wrap(
              spacing: 16,
              runSpacing: 10,
              children: [
                PortfolioKpi(
                  label: 'Quantity',
                  value: PortfolioValue(value: order.originalQuantity),
                ),
                PortfolioKpi(
                  label: 'Price',
                  value: PortfolioValue(value: order.price),
                ),
                if (order.stopPrice != null)
                  PortfolioKpi(
                    label: 'Stop',
                    value: PortfolioValue(value: order.stopPrice),
                  ),
                PortfolioKpi(
                  label: 'Filled',
                  value: PortfolioValue(value: order.executedQuantity),
                ),
                PortfolioKpi(
                  label: 'Remaining',
                  value: PortfolioValue(value: order.remainingQuantity),
                ),
                if (order.averageFillPrice != null)
                  PortfolioKpi(
                    label: 'Avg Fill',
                    value: PortfolioValue(value: order.averageFillPrice),
                  ),
                if (order.reduceOnly != null)
                  PortfolioKpi(
                    label: 'Reduce Only',
                    value: Text(
                      order.reduceOnly! ? 'Yes' : 'No',
                      style: const TextStyle(
                        color: AppColors.onCard,
                        fontSize: 13,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
              ],
            ),
            const SizedBox(height: 8),
            Row(
              children: [
                if (order.orderId != null)
                  Text(
                    'order ${order.orderId}',
                    style: const TextStyle(color: AppColors.muted, fontSize: 9),
                  ),
                if (order.clientOrderId != null) ...[
                  const SizedBox(width: 8),
                  Flexible(
                    child: Text(
                      'client ${order.clientOrderId}',
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        color: AppColors.muted,
                        fontSize: 9,
                      ),
                    ),
                  ),
                ],
                const Spacer(),
                if (order.createdAt != null)
                  Text(
                    _stamp(order.createdAt!),
                    style: const TextStyle(color: AppColors.muted, fontSize: 9),
                  ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  static String _stamp(DateTime value) =>
      value.toIso8601String().substring(0, 19).replaceFirst('T', ' ');
}

// ------------------------------------------------------------------ closed positions

/// Positions that have been closed.
///
/// A field the source could not prove is rendered as unavailable. No value is estimated
/// and none is defaulted to zero.
class _ClosedPositionsSection extends ConsumerWidget {
  const _ClosedPositionsSection();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final scope = ref.watch(portfolioScopeProvider);
    final closed = ref.watch(portfolioClosedPositionsProvider);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        PortfolioSectionHeader(
          label: 'CLOSED POSITIONS',
          trailing: closed.asData?.value.positions.isNotEmpty == true
              ? '${closed.asData!.value.positions.length}'
              : null,
        ),
        const SizedBox(height: 10),
        closed.when(
          loading: () => const PortfolioCard(
            child: Text(
              'Loading closed positions...',
              style: TextStyle(color: AppColors.muted, fontSize: 12),
            ),
          ),
          error: (error, stack) {
            if (error is PortfolioAccountModeUnresolved) {
              return const SizedBox.shrink();
            }
            return const PortfolioStatePanel(
              title: 'Closed positions unavailable',
              message: 'The closed-position history could not be read.',
              icon: Icons.cloud_off,
              isError: true,
            );
          },
          data: (data) {
            if (scope == null ||
                data.accountMode != scope.mode ||
                data.accountCategory != scope.category) {
              return const SizedBox.shrink();
            }
            if (data.isEmptyBecauseUnsupported) {
              return PortfolioStatePanel(
                title: data.availability.isUnsupported
                    ? 'No closed positions'
                    : 'Closed positions unavailable',
                message:
                    data.statusMessage ??
                    'This scope does not expose closed positions.',
                icon: Icons.info_outline,
              );
            }
            return Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                if (data.partial)
                  Padding(
                    padding: const EdgeInsets.only(bottom: 8),
                    child: PortfolioStatePanel(
                      title: 'Partial reconstruction',
                      message:
                          data.statusMessage ??
                          'Some round trips were only partly inside the requested window, so their '
                              'entry price, duration or fees are unavailable rather than estimated.',
                      icon: Icons.warning_amber_rounded,
                    ),
                  ),
                if (data.positions.isEmpty)
                  const PortfolioCard(
                    child: Text(
                      'No closed positions in this window.',
                      style: TextStyle(color: AppColors.muted, fontSize: 12),
                    ),
                  )
                else
                  ...data.positions.map(
                    (p) => Padding(
                      padding: const EdgeInsets.only(bottom: 8),
                      child: _ClosedPositionCard(position: p),
                    ),
                  ),
              ],
            );
          },
        ),
      ],
    );
  }
}

class _ClosedPositionCard extends StatelessWidget {
  const _ClosedPositionCard({required this.position});

  final PortfolioClosedPosition position;

  @override
  Widget build(BuildContext context) {
    final isLong = position.side == 'LONG';

    return PortfolioCard(
      padding: const EdgeInsets.all(12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Text(
                position.symbol,
                style: const TextStyle(
                  color: AppColors.onCard,
                  fontSize: 13,
                  fontWeight: FontWeight.w800,
                ),
              ),
              const SizedBox(width: 8),
              if (position.side != null)
                Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 6,
                    vertical: 2,
                  ),
                  decoration: BoxDecoration(
                    color: (isLong ? AppColors.accent : AppColors.loss)
                        .withValues(alpha: 0.16),
                    borderRadius: BorderRadius.circular(5),
                  ),
                  child: Text(
                    position.side!,
                    style: TextStyle(
                      color: isLong ? AppColors.accent : AppColors.loss,
                      fontSize: 9,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                ),
              const Spacer(),
              if (position.closedAt != null)
                Text(
                  _stamp(position.closedAt!),
                  style: const TextStyle(color: AppColors.muted, fontSize: 9),
                ),
            ],
          ),
          const SizedBox(height: 10),
          Wrap(
            spacing: 16,
            runSpacing: 10,
            children: [
              PortfolioKpi(
                label: 'Quantity',
                value: PortfolioValue(value: position.quantity),
              ),
              PortfolioKpi(
                label: 'Entry',
                value: PortfolioValue(value: position.entryPrice),
              ),
              PortfolioKpi(
                label: 'Exit',
                value: PortfolioValue(value: position.exitPrice),
              ),
              PortfolioKpi(
                label: 'Realized P&L',
                value: PortfolioValue(
                  value: position.realizedPnl,
                  signed: true,
                ),
              ),
              PortfolioKpi(
                label: 'Fees',
                value: PortfolioValue(value: position.fees),
              ),
              // Funding is always unavailable on a position: a funding-fee record has no
              // position attribution, so it is never assigned to one.
              PortfolioKpi(
                label: 'Funding',
                value: PortfolioValue(value: position.funding),
              ),
              PortfolioKpi(
                label: 'Duration',
                value: Text(
                  position.duration == null
                      ? PortfolioValue.missing
                      : _duration(position.duration!),
                  style: const TextStyle(
                    color: AppColors.onCard,
                    fontSize: 13,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
            ],
          ),
          if (position.orderIds.isNotEmpty || position.tradeIds.isNotEmpty) ...[
            const SizedBox(height: 8),
            Wrap(
              spacing: 12,
              runSpacing: 4,
              children: [
                if (position.orderIds.isNotEmpty)
                  Text(
                    'orders ${position.orderIds.join(', ')}',
                    style: const TextStyle(color: AppColors.muted, fontSize: 9),
                  ),
                if (position.tradeIds.isNotEmpty)
                  Text(
                    'trades ${position.tradeIds.join(', ')}',
                    style: const TextStyle(color: AppColors.muted, fontSize: 9),
                  ),
              ],
            ),
          ],
        ],
      ),
    );
  }

  static String _duration(Duration value) {
    final hours = value.inHours;
    final minutes = value.inMinutes.remainder(60);
    if (hours > 0) return '${hours}h ${minutes}m';
    if (minutes > 0) return '${minutes}m';
    return '${value.inSeconds}s';
  }

  static String _stamp(DateTime value) =>
      value.toIso8601String().substring(0, 19).replaceFirst('T', ' ');
}

// ------------------------------------------------------------------ history

/// Order, trade and income history over an explicit bounded window, with exchange-style
/// filters.
class _HistorySection extends ConsumerWidget {
  const _HistorySection();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final query = ref.watch(portfolioHistorySelectionProvider);
    final history = ref.watch(portfolioHistoryControllerProvider);
    final scope = ref.watch(portfolioScopeProvider);

    // Income lives on the dedicated transaction section, so it is not offered as a
    // history type here; offering it would duplicate that view.
    const types = [PortfolioHistoryType.order, PortfolioHistoryType.trade];

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        PortfolioSectionHeader(
          label: 'HISTORY',
          trailing: history.asData?.value.entries.isNotEmpty == true
              ? '${history.asData!.value.entries.length} records'
              : null,
        ),
        const SizedBox(height: 8),
        _HistoryTypeTabs(
          selected: query.type,
          types: types,
          onChanged: (type) => ref
              .read(portfolioHistorySelectionProvider.notifier)
              .selectType(type),
        ),
        const SizedBox(height: 8),
        // Spot order and fill history is per symbol on the exchange, so the symbol is
        // requested explicitly rather than silently omitted.
        if (scope?.category == PortfolioCategory.spot)
          Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: _SymbolField(
              initial: query.symbol,
              onSubmitted: (value) => ref
                  .read(portfolioHistorySelectionProvider.notifier)
                  .setSymbol(value),
            ),
          ),
        const _HistoryFilters(),
        const SizedBox(height: 8),
        history.when(
          loading: () => const PortfolioCard(
            child: Text(
              'Loading history...',
              style: TextStyle(color: AppColors.muted, fontSize: 12),
            ),
          ),
          error: (error, stack) {
            if (error is PortfolioAccountModeUnresolved) {
              return const SizedBox.shrink();
            }
            return const PortfolioStatePanel(
              title: 'History unavailable',
              message:
                  'The exchange history could not be read. This is a transport or server '
                  'problem, not an account with no activity.',
              icon: Icons.cloud_off,
              isError: true,
            );
          },
          data: (data) {
            if (scope == null ||
                data.accountMode != scope.mode ||
                data.accountCategory != scope.category) {
              return const SizedBox.shrink();
            }
            if (data.isEmptyBecauseUnsupported) {
              return PortfolioStatePanel(
                title: 'No history available',
                message:
                    data.statusMessage ??
                    'This scope does not expose exchange history.',
                icon: Icons.info_outline,
              );
            }
            return Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                if (data.isPartial)
                  Padding(
                    padding: const EdgeInsets.only(bottom: 8),
                    child: PortfolioStatePanel(
                      title: 'Partial window',
                      message:
                          data.statusMessage ??
                          'The record cap was reached, so this is not the whole period.',
                      icon: Icons.warning_amber_rounded,
                      isError: true,
                    ),
                  ),
                if (data.windowFrom != null && data.windowTo != null)
                  Padding(
                    padding: const EdgeInsets.only(bottom: 8),
                    child: Text(
                      '${_stamp(data.windowFrom!)} to ${_stamp(data.windowTo!)}',
                      style: const TextStyle(
                        color: AppColors.muted,
                        fontSize: 10,
                      ),
                    ),
                  ),
                if (data.entries.isEmpty)
                  const PortfolioCard(
                    child: Text(
                      'No records in this window.',
                      style: TextStyle(color: AppColors.muted, fontSize: 12),
                    ),
                  )
                else
                  ...data.entries.map(
                    (entry) => Padding(
                      padding: const EdgeInsets.only(bottom: 8),
                      child: _HistoryCard(entry: entry),
                    ),
                  ),
              ],
            );
          },
        ),
      ],
    );
  }

  static String _stamp(DateTime value) =>
      value.toIso8601String().substring(0, 19);
}

/// Bounded date-range and narrowing filters.
class _HistoryFilters extends ConsumerWidget {
  const _HistoryFilters();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final query = ref.watch(portfolioHistorySelectionProvider);
    final notifier = ref.read(portfolioHistorySelectionProvider.notifier);
    final scope = ref.watch(portfolioScopeProvider);
    final futures = scope?.category == PortfolioCategory.futures;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          child: Row(
            children: PortfolioHistoryRange.values.map((range) {
              return Padding(
                padding: const EdgeInsets.only(right: 6),
                child: _FilterChip(
                  label: range.label,
                  onTap: () => notifier.setRange(range),
                ),
              );
            }).toList(),
          ),
        ),
        const SizedBox(height: 6),
        SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          child: Row(
            children: [
              // Position-side narrowing is a futures-only concept; spot has no position
              // side, so offering the filter there would be meaningless.
              if (futures) ...[
                _FilterChip(
                  key: const ValueKey('portfolio-filter-position-long'),
                  label: 'LONG',
                  active: query.positionSide == 'LONG',
                  onTap: () => notifier.setPositionSide(
                    query.positionSide == 'LONG' ? null : 'LONG',
                  ),
                ),
                const SizedBox(width: 6),
                _FilterChip(
                  key: const ValueKey('portfolio-filter-position-short'),
                  label: 'SHORT',
                  active: query.positionSide == 'SHORT',
                  onTap: () => notifier.setPositionSide(
                    query.positionSide == 'SHORT' ? null : 'SHORT',
                  ),
                ),
                const SizedBox(width: 6),
              ],
              for (final status in PortfolioOrderStatus.values) ...[
                _FilterChip(
                  key: ValueKey('portfolio-filter-status-${status.apiValue}'),
                  label: status.apiValue,
                  active: query.status == status.apiValue,
                  onTap: () => notifier.setStatus(
                    query.status == status.apiValue ? null : status.apiValue,
                  ),
                ),
                const SizedBox(width: 6),
              ],
            ],
          ),
        ),
      ],
    );
  }
}

class _FilterChip extends StatelessWidget {
  const _FilterChip({
    super.key,
    required this.label,
    required this.onTap,
    this.active = false,
  });

  final String label;
  final VoidCallback onTap;
  final bool active;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
        decoration: BoxDecoration(
          color: active
              ? AppColors.accent.withValues(alpha: 0.16)
              : AppColors.card,
          borderRadius: BorderRadius.circular(6),
          border: Border.all(
            color: active ? AppColors.accent : const Color(0xFF2A2A2A),
          ),
        ),
        // Flexible + ellipsis: a long status such as PARTIALLY_FILLED must never force
        // the horizontal row wider than the screen on a narrow device.
        child: Text(
          label,
          overflow: TextOverflow.ellipsis,
          maxLines: 1,
          softWrap: false,
          style: TextStyle(
            color: active ? AppColors.accent : AppColors.muted,
            fontSize: 10,
            fontWeight: FontWeight.w800,
          ),
        ),
      ),
    );
  }
}

class _HistoryTypeTabs extends StatelessWidget {
  const _HistoryTypeTabs({
    required this.selected,
    required this.types,
    required this.onChanged,
  });

  final PortfolioHistoryType selected;
  final List<PortfolioHistoryType> types;
  final ValueChanged<PortfolioHistoryType> onChanged;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: types.map((type) {
        final active = type == selected;
        return Expanded(
          child: Padding(
            padding: const EdgeInsets.only(right: 6),
            child: InkWell(
              onTap: () => onChanged(type),
              child: Container(
                padding: const EdgeInsets.symmetric(vertical: 7),
                alignment: Alignment.center,
                decoration: BoxDecoration(
                  color: active
                      ? AppColors.accent.withValues(alpha: 0.16)
                      : Colors.transparent,
                  borderRadius: BorderRadius.circular(6),
                  border: Border.all(
                    color: active ? AppColors.accent : const Color(0xFF2A2A2A),
                  ),
                ),
                child: Text(
                  type.apiValue,
                  style: TextStyle(
                    color: active ? AppColors.accent : AppColors.muted,
                    fontSize: 10,
                    fontWeight: FontWeight.w800,
                    letterSpacing: 0.6,
                  ),
                ),
              ),
            ),
          ),
        );
      }).toList(),
    );
  }
}

class _SymbolField extends StatefulWidget {
  const _SymbolField({required this.initial, required this.onSubmitted});

  final String? initial;
  final ValueChanged<String?> onSubmitted;

  @override
  State<_SymbolField> createState() => _SymbolFieldState();
}

class _SymbolFieldState extends State<_SymbolField> {
  late final TextEditingController _controller = TextEditingController(
    text: widget.initial ?? '',
  );

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return TextField(
      controller: _controller,
      style: const TextStyle(color: AppColors.onCard, fontSize: 12),
      textInputAction: TextInputAction.search,
      onSubmitted: (value) {
        final trimmed = value.trim();
        widget.onSubmitted(trimmed.isEmpty ? null : trimmed.toUpperCase());
      },
      decoration: InputDecoration(
        isDense: true,
        hintText: 'Symbol, e.g. BTCUSDT',
        hintStyle: const TextStyle(color: AppColors.muted, fontSize: 12),
        prefixIcon: const Icon(Icons.search, color: AppColors.muted, size: 18),
        enabledBorder: OutlineInputBorder(
          borderSide: const BorderSide(color: Color(0xFF2A2A2A)),
          borderRadius: BorderRadius.circular(6),
        ),
        focusedBorder: OutlineInputBorder(
          borderSide: const BorderSide(color: AppColors.accent),
          borderRadius: BorderRadius.circular(6),
        ),
      ),
    );
  }
}

class _HistoryCard extends StatelessWidget {
  const _HistoryCard({required this.entry});

  final PortfolioHistoryEntry entry;

  @override
  Widget build(BuildContext context) {
    // An order row shows its own exchange state; a fill or an income row has none, so no
    // status chip is rendered rather than an empty or invented one.
    final status = entry.entryType == 'ORDER'
        ? PortfolioOrderStatus.parse(entry.status)
        : null;

    return GestureDetector(
      key: Key('trade-card-${entry.tradeId ?? entry.orderId ?? entry.symbol}'),
      // Full-width tap target; the detail sheet shows only fields the source
      // actually reported.
      onTap: () => PortfolioOrderSheets.showTradeDetail(context, entry),
      child: PortfolioCard(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Text(
                  entry.symbol ?? entry.entryType,
                  style: const TextStyle(
                    color: AppColors.onCard,
                    fontSize: 13,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(width: 8),
                if (entry.side != null)
                  Text(
                    entry.side!,
                    style: const TextStyle(
                      color: AppColors.muted,
                      fontSize: 9,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                if (entry.positionSide != null) ...[
                  const SizedBox(width: 6),
                  Text(
                    entry.positionSide!,
                    style: const TextStyle(
                      color: AppColors.muted,
                      fontSize: 9,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ],
                if (entry.orderType != null) ...[
                  const SizedBox(width: 6),
                  Text(
                    entry.orderType!,
                    style: const TextStyle(color: AppColors.muted, fontSize: 9),
                  ),
                ],
                const Spacer(),
                if (status != null)
                  PortfolioOrderStatusChip(status: status)
                else if (entry.status != null)
                  Text(
                    entry.status!,
                    style: const TextStyle(color: AppColors.muted, fontSize: 9),
                  ),
              ],
            ),
            const SizedBox(height: 10),
            Wrap(
              spacing: 16,
              runSpacing: 10,
              children: [
                PortfolioKpi(
                  label: 'Price',
                  value: PortfolioValue(value: entry.price),
                ),
                PortfolioKpi(
                  label: 'Quantity',
                  value: PortfolioValue(value: entry.quantity),
                ),
                if (entry.quoteQuantity != null)
                  PortfolioKpi(
                    label: 'Quote Qty',
                    value: PortfolioValue(value: entry.quoteQuantity),
                  ),
                // Only rendered when the source actually reported a commission; an absent
                // commission stays unavailable and is never shown as zero.
                if (entry.fee != null)
                  PortfolioKpi(
                    label:
                        'Fee${entry.feeAsset != null ? ' (${entry.feeAsset})' : ''}',
                    value: PortfolioValue(value: entry.fee),
                  ),
                if (entry.realizedPnl != null)
                  PortfolioKpi(
                    label:
                        entry.entryType == 'INCOME' ||
                            entry.entryType == 'FUNDING_FEE'
                        ? 'Amount'
                        : 'Realized P&L',
                    value: PortfolioValue(
                      value: entry.realizedPnl,
                      signed: true,
                    ),
                  ),
              ],
            ),
            const SizedBox(height: 8),
            Row(
              children: [
                if (entry.orderId != null)
                  Text(
                    'order ${entry.orderId}',
                    style: const TextStyle(color: AppColors.muted, fontSize: 9),
                  ),
                if (entry.tradeId != null) ...[
                  const SizedBox(width: 8),
                  Text(
                    'trade ${entry.tradeId}',
                    style: const TextStyle(color: AppColors.muted, fontSize: 9),
                  ),
                ],
                const Spacer(),
                if (entry.occurredAt != null)
                  Text(
                    _stamp(entry.occurredAt!),
                    style: const TextStyle(color: AppColors.muted, fontSize: 9),
                  ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  static String _stamp(DateTime value) =>
      value.toIso8601String().substring(0, 19).replaceFirst('T', ' ');
}

// ------------------------------------------------------------------ transactions

/// Account income records, keeping the exchange's own income type as the row label.
class _TransactionHistorySection extends ConsumerWidget {
  const _TransactionHistorySection();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final scope = ref.watch(portfolioScopeProvider);
    final transactions = ref.watch(portfolioTransactionControllerProvider);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        PortfolioSectionHeader(
          label: 'TRANSACTION HISTORY',
          trailing: transactions.asData?.value.entries.isNotEmpty == true
              ? '${transactions.asData!.value.entries.length} records'
              : null,
        ),
        const SizedBox(height: 10),
        transactions.when(
          loading: () => const PortfolioCard(
            child: Text(
              'Loading transactions...',
              style: TextStyle(color: AppColors.muted, fontSize: 12),
            ),
          ),
          error: (error, stack) {
            if (error is PortfolioAccountModeUnresolved) {
              return const SizedBox.shrink();
            }
            return const PortfolioStatePanel(
              title: 'Transactions unavailable',
              message: 'The account income records could not be read.',
              icon: Icons.cloud_off,
              isError: true,
            );
          },
          data: (data) {
            if (scope == null ||
                data.accountMode != scope.mode ||
                data.accountCategory != scope.category) {
              return const SizedBox.shrink();
            }
            if (data.isEmptyBecauseUnsupported) {
              return PortfolioStatePanel(
                title: data.availability.isUnsupported
                    ? 'Not available'
                    : 'Transactions unavailable',
                message:
                    data.statusMessage ??
                    'This account publishes no transaction records.',
                icon: Icons.info_outline,
              );
            }
            if (data.entries.isEmpty) {
              return const PortfolioCard(
                child: Text(
                  'No transactions in this window.',
                  style: TextStyle(color: AppColors.muted, fontSize: 12),
                ),
              );
            }
            return Column(
              children: data.entries
                  .map(
                    (e) => Padding(
                      padding: const EdgeInsets.only(bottom: 8),
                      child: _HistoryCard(entry: e),
                    ),
                  )
                  .toList(),
            );
          },
        ),
      ],
    );
  }
}

/// Funding fees, kept separate from other income because they are their own exchange
/// income type and are never summed into realized P&L.
class _FundingSection extends ConsumerWidget {
  const _FundingSection();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final scope = ref.watch(portfolioScopeProvider);
    final funding = ref.watch(portfolioFundingControllerProvider);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // The trailing count is a bare number rather than a timestamp or a phrase, so it
        // cannot exceed the width available on a narrow screen.
        PortfolioSectionHeader(
          label: 'FUNDING FEES',
          trailing: funding.asData?.value.entries.isNotEmpty == true
              ? '${funding.asData!.value.entries.length}'
              : null,
        ),
        const SizedBox(height: 10),
        funding.when(
          loading: () => const PortfolioCard(
            child: Text(
              'Loading funding fees...',
              style: TextStyle(color: AppColors.muted, fontSize: 12),
            ),
          ),
          error: (error, stack) {
            if (error is PortfolioAccountModeUnresolved) {
              return const SizedBox.shrink();
            }
            return const PortfolioStatePanel(
              title: 'Funding fees unavailable',
              message: 'The funding-fee records could not be read.',
              icon: Icons.cloud_off,
              isError: true,
            );
          },
          data: (data) {
            if (scope == null ||
                data.accountMode != scope.mode ||
                data.accountCategory != scope.category) {
              return const SizedBox.shrink();
            }
            if (data.isEmptyBecauseUnsupported) {
              return PortfolioStatePanel(
                title: data.availability.isUnsupported
                    ? 'No funding fees'
                    : 'Funding fees unavailable',
                message:
                    data.statusMessage ??
                    'This account publishes no funding-fee records.',
                icon: Icons.info_outline,
              );
            }
            if (data.entries.isEmpty) {
              return const PortfolioCard(
                child: Text(
                  'No funding fees in this window.',
                  style: TextStyle(color: AppColors.muted, fontSize: 12),
                ),
              );
            }
            return Column(
              children: data.entries
                  .map(
                    (e) => Padding(
                      padding: const EdgeInsets.only(bottom: 8),
                      child: _HistoryCard(entry: e),
                    ),
                  )
                  .toList(),
            );
          },
        ),
      ],
    );
  }
}

// ------------------------------------------------------------------ sync

/// Connection state, freshness and why a scope is not current.
///
/// A stale scope is always labelled as stale; the figures above it are never silently
/// presented as current.
class _SyncStatusSection extends ConsumerWidget {
  const _SyncStatusSection();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final scope = ref.watch(portfolioScopeProvider);
    final status = ref.watch(portfolioSyncStatusControllerProvider);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const PortfolioSectionHeader(label: 'SYNC STATUS'),
        const SizedBox(height: 10),
        status.when(
          loading: () => const SizedBox.shrink(),
          error: (error, stack) {
            if (error is PortfolioAccountModeUnresolved) {
              return const SizedBox.shrink();
            }
            return const PortfolioStatePanel(
              title: 'Sync status unavailable',
              message:
                  'The synchronization state of this scope could not be read.',
              icon: Icons.cloud_off,
              isError: true,
            );
          },
          data: (data) {
            if (scope == null ||
                data.accountMode != scope.mode ||
                data.accountCategory != scope.category) {
              return const SizedBox.shrink();
            }
            final rows = <Widget>[
              if (data.connectionStatus != null)
                _StatusRow(label: 'Connection', value: data.connectionStatus!),
              if (data.lastRestSync != null)
                _StatusRow(
                  label: 'Last REST sync',
                  value: _stamp(data.lastRestSync!),
                ),
              if (data.lastEvent != null)
                _StatusRow(
                  label: 'Last stream event',
                  value: _stamp(data.lastEvent!),
                ),
            ];

            return Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                if (data.isStale)
                  const Padding(
                    padding: EdgeInsets.only(bottom: 8),
                    child: PortfolioStatePanel(
                      title: 'Data is stale',
                      message:
                          'The values above are the last known ones and are past the freshness '
                          'window. They must not be read as current.',
                      icon: Icons.history_toggle_off,
                      isError: true,
                    ),
                  ),
                if (rows.isEmpty &&
                    data.message != null &&
                    data.message!.isNotEmpty)
                  PortfolioStatePanel(
                    title: 'Not synchronised',
                    message: data.message!,
                    icon: Icons.info_outline,
                  )
                else if (rows.isNotEmpty)
                  PortfolioCard(
                    padding: const EdgeInsets.all(12),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: rows,
                    ),
                  ),
              ],
            );
          },
        ),
      ],
    );
  }

  static String _stamp(DateTime value) =>
      '${value.toIso8601String().substring(0, 19)}Z';
}

class _StatusRow extends StatelessWidget {
  const _StatusRow({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 3),
      // The value is the long part here (a full UTC timestamp), so it takes the
      // remaining width and wraps instead of forcing the row to overflow.
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            label,
            style: const TextStyle(color: AppColors.muted, fontSize: 11),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              value,
              textAlign: TextAlign.right,
              style: const TextStyle(
                color: AppColors.onCard,
                fontSize: 11,
                fontWeight: FontWeight.w700,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
