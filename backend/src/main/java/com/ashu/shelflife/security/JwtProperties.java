package com.ashu.shelflife.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT configuration, bound from {@code security.jwt.*} in application.yml.
 *
 * @param secret                       Base64-encoded HMAC-SHA256 signing key (>= 256 bits).
 * @param accessTokenExpirationMinutes access-token lifetime in minutes.
 * @param refreshTokenExpirationDays   refresh-token lifetime in days.
 * @param issuer                       the {@code iss} claim value.
 */
@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(
        String secret,
        long accessTokenExpirationMinutes,
        long refreshTokenExpirationDays,
        String issuer) {
}
