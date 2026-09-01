package com.shyblack.cryptosignals.security.oauth2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.shyblack.cryptosignals.exception.InvalidTokenException;
import org.junit.jupiter.api.Test;

class GoogleTokenVerifierTest {

	@Test
	void rejectsWhenClientIdIsMissing() {
		GoogleTokenVerifier verifier = new GoogleTokenVerifier("");
		InvalidTokenException ex = assertThrows(InvalidTokenException.class, () -> verifier.verify("any-token"));
		assertEquals("Google login is not configured", ex.getMessage());
	}

	@Test
	void rejectsPlaceholderClientId() {
		GoogleTokenVerifier verifier = new GoogleTokenVerifier("replace-me");
		InvalidTokenException ex = assertThrows(InvalidTokenException.class, () -> verifier.verify("any-token"));
		assertEquals("Google login is not configured", ex.getMessage());
	}
}
