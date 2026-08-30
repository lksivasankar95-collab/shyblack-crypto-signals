package com.shyblack.cryptosignals.dto.auth;

public record AccessTokenResponse(
		String accessToken,
		String tokenType,
		long expiresInSeconds
) {
}
