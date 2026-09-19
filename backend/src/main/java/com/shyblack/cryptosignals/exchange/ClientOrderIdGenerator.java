package com.shyblack.cryptosignals.exchange;

import com.shyblack.cryptosignals.entity.enums.LiveOrderPurpose;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Produces the deterministic clientOrderId used as the idempotency key for
 * live orders. Same (user, signal, symbol, side, purpose) always yields the
 * same id — so duplicate signal events, restarts, and retries never create
 * two exchange orders.
 *
 * Format: {@code SB-<24-char base64url of sha256(...)>} (≤ 32 chars, satisfies
 * Binance's newClientOrderId constraints).
 */
@Component
public class ClientOrderIdGenerator {

	public String forSignal(UUID userId, UUID signalId, String symbol,
			PositionSide side, LiveOrderPurpose purpose) {
		String canonical = userId + "|" + signalId + "|" + symbol + "|" + side + "|" + purpose;
		return "SB-" + digest(canonical);
	}

	public String forManualClose(UUID userId, UUID positionId, LiveOrderPurpose purpose) {
		String canonical = userId + "|manual|" + positionId + "|" + purpose;
		return "SB-" + digest(canonical);
	}

	private static String digest(String input) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
			String b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
			return b64.substring(0, Math.min(24, b64.length()));
		} catch (Exception ex) {
			throw new IllegalStateException("SHA-256 unavailable", ex);
		}
	}
}
