package com.ashu.shelflife.security;

import com.ashu.shelflife.users.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies signed access tokens (JWT, HMAC-SHA256).
 *
 * <p>Claims carried by an access token:
 * <ul>
 *   <li>{@code sub} — user id</li>
 *   <li>{@code role} — role name (CENTRAL_ADMIN / HUB_PICKER)</li>
 *   <li>{@code email} — user email</li>
 *   <li>{@code warehouseIds} — for HUB_PICKER, the assigned warehouses; empty for CENTRAL_ADMIN</li>
 * </ul>
 */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long accessTokenExpirationMinutes;
    private final String issuer;

    public JwtService(JwtProperties properties) {
        this.signingKey = Keys.hmacShaKeyFor(java.util.Base64.getDecoder().decode(properties.secret()));
        this.accessTokenExpirationMinutes = properties.accessTokenExpirationMinutes();
        this.issuer = properties.issuer();
    }

    /**
     * Build a signed access token for the given user. {@code warehouseIds} should be the
     * picker's mapped warehouses (HUB_PICKER) or empty (CENTRAL_ADMIN — global scope).
     */
    public String generateAccessToken(User user, List<Long> warehouseIds) {
        Instant now = Instant.now();
        Instant expiry = now.plus(accessTokenExpirationMinutes, ChronoUnit.MINUTES);
        return Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(user.getId()))
                .claims(Map.of(
                        "role", user.getRole().getName(),
                        "email", user.getEmail(),
                        "warehouseIds", warehouseIds))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Parse and verify a token, returning its claims. Throws a {@link io.jsonwebtoken.JwtException}
     * subclass if the signature is invalid, the token is malformed, or it has expired.
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getAccessTokenExpirationSeconds() {
        return accessTokenExpirationMinutes * 60;
    }
}
