package com.cosmetics.inventory.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Date;
import java.util.List;

@Service
public class JwtService {
	private final JwtProperties props;
	private final SecretKey key;

	public JwtService(JwtProperties props) {
		this.props = props;
		this.key = Keys.hmacShaKeyFor(sha256(props.secret()));
	}

	private static byte[] sha256(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return digest.digest(value.getBytes(StandardCharsets.UTF_8));
		} catch (Exception e) {
			throw new IllegalStateException("Unable to initialize JWT secret", e);
		}
	}

	public String createAccessToken(String subject, List<String> roles) {
		Instant now = Instant.now();
		Instant exp = now.plusSeconds(props.accessTokenMinutes() * 60L);

		return Jwts.builder()
				.subject(subject)
				.issuedAt(Date.from(now))
				.expiration(Date.from(exp))
				.claim("roles", roles)
				.signWith(key)
				.compact();
	}

	public JwtPrincipal parseAccessToken(String token) {
		var claims = Jwts.parser()
				.verifyWith(key)
				.build()
				.parseSignedClaims(token)
				.getPayload();

		String subject = claims.getSubject();
		Object rolesObj = claims.get("roles");
		List<String> roles = rolesObj instanceof List<?> list
				? list.stream().map(String::valueOf).toList()
				: List.of();

		return new JwtPrincipal(subject, roles);
	}
}
