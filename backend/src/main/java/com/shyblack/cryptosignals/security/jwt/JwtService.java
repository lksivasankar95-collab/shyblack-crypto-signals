package com.shyblack.cryptosignals.security.jwt;

import com.shyblack.cryptosignals.config.JwtProperties;
import com.shyblack.cryptosignals.exception.InvalidTokenException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

	private final JwtProperties properties;
	private final SecretKey key;

	public JwtService(JwtProperties properties) {
		this.properties = properties;
		this.key = buildKey(properties.secret());
	}

	public long accessTokenExpirationSeconds() {
		return properties.accessTokenExpirationMs() / 1000;
	}

	public Instant refreshTokenExpiry() {
		return Instant.now().plusMillis(properties.refreshTokenExpirationMs());
	}

	public String createAccessToken(UUID userId, String email) {
		return buildToken(userId, email, "access", properties.accessTokenExpirationMs());
	}

	public String createRefreshToken(UUID userId, String email) {
		return buildToken(userId, email, "refresh", properties.refreshTokenExpirationMs());
	}

	public Claims parseAccessToken(String token) {
		return parseTyped(token, "access");
	}

	public Claims parseRefreshToken(String token) {
		return parseTyped(token, "refresh");
	}

	public String emailFromAccessToken(String token) {
		return parseAccessToken(token).get("email", String.class);
	}

	public String hashToken(String token) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 not available", ex);
		}
	}

	private Claims parseTyped(String token, String expectedType) {
		try {
			Claims claims = Jwts.parser()
					.verifyWith(key)
					.build()
					.parseSignedClaims(token)
					.getPayload();
			if (!expectedType.equals(claims.get("type", String.class))) {
				throw new InvalidTokenException("Invalid token");
			}
			return claims;
		} catch (ExpiredJwtException ex) {
			throw new InvalidTokenException("Token expired");
		} catch (InvalidTokenException ex) {
			throw ex;
		} catch (JwtException | IllegalArgumentException ex) {
			throw new InvalidTokenException("Invalid token");
		}
	}

	private String buildToken(UUID userId, String email, String type, long expirationMs) {
		Date now = new Date();
		return Jwts.builder()
				.subject(userId.toString())
				.claim("email", email)
				.claim("type", type)
				.issuedAt(now)
				.expiration(new Date(now.getTime() + expirationMs))
				.signWith(key)
				.compact();
	}

	private static SecretKey buildKey(String secret) {
		try {
			return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
		} catch (Exception ex) {
			return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		}
	}
}
