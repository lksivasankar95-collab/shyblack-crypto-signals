package com.shyblack.cryptosignals.security.oauth2;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.shyblack.cryptosignals.exception.InvalidTokenException;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class GoogleTokenVerifier {

	private final String clientId;

	public GoogleTokenVerifier(@Value("${app.google.client-id:}") String clientId) {
		this.clientId = clientId;
	}

	public GoogleUserInfo verify(String idToken) {
		if (!StringUtils.hasText(clientId) || "replace-me".equals(clientId)) {
			throw new InvalidTokenException("Google login is not configured");
		}
		try {
			GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
					new NetHttpTransport(),
					GsonFactory.getDefaultInstance()
			)
					.setAudience(Collections.singletonList(clientId))
					.setIssuers(List.of("https://accounts.google.com", "accounts.google.com"))
					.build();
			GoogleIdToken token = verifier.verify(idToken);
			if (token == null) {
				throw new InvalidTokenException("Invalid Google ID token");
			}
			GoogleIdToken.Payload payload = token.getPayload();
			String email = payload.getEmail();
			if (!StringUtils.hasText(email)) {
				throw new InvalidTokenException("Google account has no email");
			}
			if (Boolean.FALSE.equals(payload.getEmailVerified())) {
				throw new InvalidTokenException("Google email is not verified");
			}
			String name = (String) payload.get("name");
			if (!StringUtils.hasText(name)) {
				name = email;
			}
			return new GoogleUserInfo(payload.getSubject(), email, name);
		} catch (InvalidTokenException ex) {
			throw ex;
		} catch (Exception ex) {
			throw new InvalidTokenException("Invalid Google ID token");
		}
	}
}
