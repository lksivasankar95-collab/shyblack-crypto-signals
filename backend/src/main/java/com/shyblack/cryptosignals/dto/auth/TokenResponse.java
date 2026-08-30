package com.shyblack.cryptosignals.dto.auth;

public record TokenResponse(
		String accessToken,
		String refreshToken,
		String tokenType,
		long expiresInSeconds
) {
}
