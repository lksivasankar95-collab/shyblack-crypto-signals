package com.shyblack.cryptosignals.service.execution;

import com.shyblack.cryptosignals.entity.enums.AccountCategory;
import com.shyblack.cryptosignals.entity.enums.AccountMode;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import java.util.UUID;

/**
 * The deterministic identity of one intended execution.
 *
 * <p>Derived only from stable business facts — never from a timestamp, a random
 * UUID, or an incrementing counter — so the same signal routed twice to the same
 * scope produces the same key. That is what makes duplicate suppression work
 * across duplicate events, application restarts and concurrent consumers.
 *
 * <p>The identity deliberately includes {@code tradingMode} as well as the
 * category. A SPOT and a FUTURES signal that happen to share a symbol still
 * produce different keys, so one can never suppress the other.
 */
public record ExecutionIdentity(
		UUID userId,
		UUID signalId,
		AccountMode accountMode,
		AccountCategory accountCategory,
		TradingMode tradingMode) {

	/**
	 * Canonical string form, used as the database uniqueness key.
	 *
	 * <p>Field-separated with a character that cannot occur inside a UUID or an
	 * enum name, so no two distinct identities can produce the same string.
	 */
	public String canonical() {
		return userId + "|" + signalId + "|" + accountMode + "|" + accountCategory + "|" + tradingMode;
	}

	public static ExecutionIdentity of(
			UUID userId,
			UUID signalId,
			AccountMode accountMode,
			AccountCategory accountCategory,
			TradingMode tradingMode) {
		return new ExecutionIdentity(userId, signalId, accountMode, accountCategory, tradingMode);
	}
}