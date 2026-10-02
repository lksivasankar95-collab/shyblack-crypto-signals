package com.shyblack.cryptosignals.dto.portfolio;

import com.shyblack.cryptosignals.entity.enums.AccountAvailability;
import com.shyblack.cryptosignals.entity.enums.AccountCategory;
import com.shyblack.cryptosignals.entity.enums.AccountMode;
import com.shyblack.cryptosignals.entity.enums.ExchangeConnectionStatus;
import java.time.Instant;

/**
 * Read-only synchronization and reconciliation status for one account scope.
 *
 * <p>Deliberately limited to what an operator or a user needs: whether the scope is connected, how
 * current the data is, and why it is not current. No credential, listen key, socket URL or internal
 * identifier is exposed, and a failed reconciliation is reported rather than hidden.
 */
public record PortfolioSyncStatusResponse(
		AccountMode accountMode,
		AccountCategory accountCategory,
		ExchangeConnectionStatus connectionStatus,
		AccountAvailability availability,
		/** REST snapshot time, distinct from the user-stream event time. */
		Instant lastRestSync,
		/** Last applied user-data WebSocket event time. Null when no event has been applied. */
		Instant lastEvent,
		/** True when the scope holds data but it is past the freshness window. */
		boolean stale,
		String message) {}
