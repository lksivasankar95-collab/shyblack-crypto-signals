package com.shyblack.cryptosignals.service;

import com.shyblack.cryptosignals.dto.auth.AccessTokenResponse;
import com.shyblack.cryptosignals.dto.auth.GoogleLoginRequest;
import com.shyblack.cryptosignals.dto.auth.LoginRequest;
import com.shyblack.cryptosignals.dto.auth.MessageResponse;
import com.shyblack.cryptosignals.dto.auth.RefreshTokenRequest;
import com.shyblack.cryptosignals.dto.auth.SignupRequest;
import com.shyblack.cryptosignals.dto.auth.TokenResponse;
import com.shyblack.cryptosignals.entity.RefreshToken;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.AccountType;
import com.shyblack.cryptosignals.entity.enums.AuthProvider;
import com.shyblack.cryptosignals.entity.enums.RiskProfile;
import com.shyblack.cryptosignals.entity.enums.Role;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import com.shyblack.cryptosignals.exception.DuplicateEmailException;
import com.shyblack.cryptosignals.exception.InvalidCredentialsException;
import com.shyblack.cryptosignals.exception.InvalidTokenException;
import com.shyblack.cryptosignals.repository.RefreshTokenRepository;
import com.shyblack.cryptosignals.repository.UserRepository;
import com.shyblack.cryptosignals.security.jwt.JwtService;
import com.shyblack.cryptosignals.security.oauth2.GoogleTokenVerifier;
import com.shyblack.cryptosignals.security.oauth2.GoogleUserInfo;
import java.time.Instant;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

	private final UserRepository userRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final JwtService jwtService;
	private final PasswordEncoder passwordEncoder;
	private final GoogleTokenVerifier googleTokenVerifier;

	@Transactional
	public MessageResponse signup(SignupRequest request) {
		String email = normalizeEmail(request.email());
		if (userRepository.existsByEmail(email)) {
			throw new DuplicateEmailException(email);
		}
		User user = new User();
		user.setEmail(email);
		user.setFullName(request.fullName().trim());
		user.setPasswordHash(passwordEncoder.encode(request.password()));
		user.setAuthProvider(AuthProvider.LOCAL);
		user.setRole(Role.USER);
		user.setAccountType(AccountType.PAPER);
		user.setTradingMode(TradingMode.SPOT);
		user.setRiskProfile(RiskProfile.MODERATE);
		user.setTimezone("UTC");
		user.setEnabled(true);
		userRepository.save(user);
		return new MessageResponse("Account created successfully");
	}

	@Transactional
	public TokenResponse login(LoginRequest request) {
		String email = normalizeEmail(request.email());
		User user = userRepository.findByEmail(email).orElseThrow(InvalidCredentialsException::new);
		if (user.getPasswordHash() == null
				|| !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw new InvalidCredentialsException();
		}
		if (!user.isEnabled()) {
			throw new InvalidCredentialsException();
		}
		return issueTokens(user);
	}

	@Transactional
	public AccessTokenResponse refresh(RefreshTokenRequest request) {
		jwtService.parseRefreshToken(request.refreshToken());
		String hash = jwtService.hashToken(request.refreshToken());
		RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
				.orElseThrow(() -> new InvalidTokenException("Invalid token"));
		if (stored.isRevoked() || stored.getExpiresAt().isBefore(Instant.now())) {
			throw new InvalidTokenException("Token expired");
		}
		User user = stored.getUser();
		String accessToken = jwtService.createAccessToken(user.getId(), user.getEmail());
		return new AccessTokenResponse(accessToken, "Bearer", jwtService.accessTokenExpirationSeconds());
	}

	@Transactional
	public TokenResponse googleLogin(GoogleLoginRequest request) {
		GoogleUserInfo googleUser = googleTokenVerifier.verify(request.idToken());
		String email = normalizeEmail(googleUser.email());
		User user = userRepository.findByGoogleSub(googleUser.subject())
				.or(() -> userRepository.findByEmail(email))
				.orElseGet(User::new);

		if (user.getId() == null) {
			user.setEmail(email);
			user.setFullName(googleUser.fullName());
			user.setAuthProvider(AuthProvider.GOOGLE);
			user.setRole(Role.USER);
			user.setAccountType(AccountType.PAPER);
			user.setTradingMode(TradingMode.SPOT);
			user.setRiskProfile(RiskProfile.MODERATE);
			user.setTimezone("UTC");
			user.setEnabled(true);
		}
		user.setGoogleSub(googleUser.subject());
		if (user.getAuthProvider() == AuthProvider.LOCAL) {
			user.setAuthProvider(AuthProvider.GOOGLE);
		}
		userRepository.save(user);
		return issueTokens(user);
	}

	private TokenResponse issueTokens(User user) {
		String accessToken = jwtService.createAccessToken(user.getId(), user.getEmail());
		String refreshToken = jwtService.createRefreshToken(user.getId(), user.getEmail());

		RefreshToken stored = new RefreshToken();
		stored.setUser(user);
		stored.setTokenHash(jwtService.hashToken(refreshToken));
		stored.setExpiresAt(jwtService.refreshTokenExpiry());
		stored.setRevoked(false);
		refreshTokenRepository.save(stored);

		return new TokenResponse(
				accessToken,
				refreshToken,
				"Bearer",
				jwtService.accessTokenExpirationSeconds()
		);
	}

	private static String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}
}
