package com.shyblack.cryptosignals.controller;

import com.shyblack.cryptosignals.dto.auth.AccessTokenResponse;
import com.shyblack.cryptosignals.dto.auth.GoogleLoginRequest;
import com.shyblack.cryptosignals.dto.auth.LoginRequest;
import com.shyblack.cryptosignals.dto.auth.MessageResponse;
import com.shyblack.cryptosignals.dto.auth.RefreshTokenRequest;
import com.shyblack.cryptosignals.dto.auth.SignupRequest;
import com.shyblack.cryptosignals.dto.auth.TokenResponse;
import com.shyblack.cryptosignals.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@SecurityRequirements
@Tag(name = "Auth", description = "Signup, login, token refresh, and Google sign-in")
public class AuthController {

	private final AuthService authService;

	@Operation(summary = "Create an account with email and password")
	@PostMapping("/signup")
	@ResponseStatus(HttpStatus.CREATED)
	public MessageResponse signup(@Valid @RequestBody SignupRequest request) {
		return authService.signup(request);
	}

	@Operation(summary = "Login with email and password")
	@PostMapping("/login")
	public TokenResponse login(@Valid @RequestBody LoginRequest request) {
		return authService.login(request);
	}

	@Operation(summary = "Issue a new access token from a refresh token")
	@PostMapping("/refresh")
	public AccessTokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
		return authService.refresh(request);
	}

	@Operation(summary = "Login or register with a Google ID token")
	@PostMapping("/google")
	public TokenResponse google(@Valid @RequestBody GoogleLoginRequest request) {
		return authService.googleLogin(request);
	}
}
