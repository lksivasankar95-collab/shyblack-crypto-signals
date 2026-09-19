package com.shyblack.cryptosignals.exchange.binance;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** HMAC-SHA256 helper for Binance signed REST requests. */
final class BinanceSignatureUtil {

	private BinanceSignatureUtil() {}

	static String hmacSha256Hex(String secret, String payload) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(raw);
		} catch (Exception ex) {
			throw new IllegalStateException("Failed to compute HMAC-SHA256", ex);
		}
	}
}
