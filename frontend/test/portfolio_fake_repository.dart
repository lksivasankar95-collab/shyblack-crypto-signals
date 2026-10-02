import 'package:cryptosignals/domain/entities/portfolio_account.dart';
import 'package:cryptosignals/domain/repositories/portfolio_repository.dart';

/// Deterministic in-memory Portfolio repository for widget and unit tests.
///
/// Records every call so a test can assert the exact mode and category that were
/// requested, and can return different payloads per scope so PAPER and LIVE can
/// never be confused with one another.
class FakePortfolioRepository implements PortfolioRepository {
  FakePortfolioRepository({
    Map<String, PortfolioAccount>? accounts,
    Map<String, PortfolioPositions>? positions,
  })  : accounts = accounts ?? <String, PortfolioAccount>{},
        positions = positions ?? <String, PortfolioPositions>{};

  /// Keyed by "MODE:CATEGORY".
  final Map<String, PortfolioAccount> accounts;
  final Map<String, PortfolioPositions> positions;

  final List<String> accountCalls = [];
  final List<String> positionCalls = [];

  /// When set, the next call throws this instead of returning data.
  Object? failWith;

  static String key(PortfolioMode mode, PortfolioCategory category) =>
      '${mode.apiValue}:${category.apiValue}';

  @override
  Future<PortfolioAccount> getAccount(
    PortfolioMode mode,
    PortfolioCategory category,
  ) async {
    accountCalls.add(key(mode, category));
    if (failWith != null) throw failWith!;
    return accounts[key(mode, category)] ??
        PortfolioAccount(
          accountMode: mode,
          accountCategory: category,
          availability: PortfolioAvailability.available,
        );
  }

  @override
  Future<PortfolioPositions> getPositions(
    PortfolioMode mode,
    PortfolioCategory category,
  ) async {
    positionCalls.add(key(mode, category));
    if (failWith != null) throw failWith!;
    return positions[key(mode, category)] ??
        PortfolioPositions(
          accountMode: mode,
          accountCategory: category,
          availability: PortfolioAvailability.available,
          positions: const [],
        );
  }

  @override
  Future<PortfolioOverview> getOverview(PortfolioMode mode) async {
    final list = <PortfolioAccount>[];
    for (final category in PortfolioCategory.values) {
      list.add(await getAccount(mode, category));
    }
    return PortfolioOverview(accountMode: mode, accounts: list);
  }
}
