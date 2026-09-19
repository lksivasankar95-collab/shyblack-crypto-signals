package com.shyblack.cryptosignals.service.futures;

import com.shyblack.cryptosignals.entity.enums.FuturesOrderPurpose;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Deterministic Futures clientOrderId. Include tradingMode so a Spot and a
 * Futures signal that happen to share (user, signal, symbol, side, purpose)
 * still produce distinct exchange orders.
 */
@Component
public class FuturesClientOrderIdGenerator {

	public String forSignal(UUID userId, UUID signalId, String symbol,
			PositionSide positionSide, FuturesOrderPurpose purpose) {
		String canonical = "F|" + userId + "|" + signalId + "|" + symbol + "|" + positionSide + "|" + purpose;
		return "SBF-" + digest(canonical);
	}

	public String forManualClose(UUID userId, UUID positionId, PositionSide positionSide) {
		String canonical = "F|manual|" + userId + "|" + positionId + "|" + positionSide;
		return "SBF-" + digest(canonical);
	}

	private static String digest(String input) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
			String b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
			return b64.substring(0, Math.min(23, b64.length()));
		} catch (Exception ex) {
			throw new IllegalStateException("SHA-256 unavailable", ex);
		}
	}
}
