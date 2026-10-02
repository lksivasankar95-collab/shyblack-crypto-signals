package com.shyblack.cryptosignals.service.execution;

import com.shyblack.cryptosignals.entity.enums.AccountCategory;
import com.shyblack.cryptosignals.entity.enums.AccountMode;
import com.shyblack.cryptosignals.entity.enums.TradingMode;

/**
 * Maps a signal's {@link TradingMode} onto the {@link AccountCategory} that would
 * execute it, and reports the capability limits honestly.
 *
 * <p>This is the single place the TradingMode → AccountCategory relationship is
 * defined. Nothing else in the codebase performs this mapping, so a mismatched
 * combination cannot be silently coerced somewhere else.
 *
 * <p>The three dimensions are kept strictly separate, because conflating them is
 * the original defect this portfolio architecture was built to remove:
 * <ul>
 *   <li>{@link AccountMode} — PAPER or LIVE. Whose money.</li>
 *   <li>{@link TradingMode} — the market dimension carried by a signal.</li>
 *   <li>{@link AccountCategory} — SPOT / FUTURES / OPTIONS / MAIN. Which wallet.</li>
 * </ul>
 */
public final class ExecutionRouting {

	private ExecutionRouting() {
	}

	/**
	 * The category that would execute a signal in the given trading mode.
	 *
	 * @return the mapped category, or null when the mode has no execution path.
	 *     Null rather than a fabricated default: OPTIONS has no category that
	 *     executes it, and MAIN would silently merge two wallets.
	 */
	public static AccountCategory categoryFor(TradingMode tradingMode) {
		if (tradingMode == null) {
			return null;
		}
		return switch (tradingMode) {
			case SPOT -> AccountCategory.SPOT;
			case FUTURES -> AccountCategory.FUTURES;
			// Options is a reserved capability. It has an AccountCategory because the
			// read model reserves it, but no engine, so it never routes.
			case OPTIONS -> null;
		};
	}

	/** True when this category has an execution engine at all. */
	public static boolean isExecutableCategory(AccountCategory category) {
		return category == AccountCategory.SPOT || category == AccountCategory.FUTURES;
	}

	/** True when this trading mode has an execution path. */
	public static boolean isSupportedTradingMode(TradingMode tradingMode) {
		return tradingMode == TradingMode.SPOT || tradingMode == TradingMode.FUTURES;
	}

	/**
	 * True when paper execution for this trading mode is meaningful.
	 *
	 * <p>Paper is mode-agnostic by existing design: {@code PaperTradingEngineService}
	 * never inspects {@code tradingMode}, so a paper account mirrors both the spot
	 * and futures universes. Phase 8 preserves that exactly rather than narrowing
	 * it, because changing it would alter paper accounting behaviour.
	 */
	public static boolean paperExecutes(TradingMode tradingMode) {
		return isSupportedTradingMode(tradingMode);
	}

	/** True when a live account of this category could ever execute. */
	public static boolean liveExecutes(AccountCategory category) {
		return isExecutableCategory(category);
	}
}