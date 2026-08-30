package com.shyblack.cryptosignals.security.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

	@Value("${app.oauth2.success-redirect-uri}")
	private String successRedirectUri;

	@Override
	public void onAuthenticationSuccess(
			HttpServletRequest request,
			HttpServletResponse response,
			Authentication authentication
	) throws IOException {
		// TODO: find-or-create User from Google principal, issue access + refresh tokens, then redirect.
		response.sendRedirect(successRedirectUri);
	}
}
