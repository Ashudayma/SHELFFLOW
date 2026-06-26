package com.ashu.shelflife.auth;

import com.ashu.shelflife.audit.AuditAction;
import com.ashu.shelflife.audit.AuditEvent;
import com.ashu.shelflife.auth.dto.TokenResponse;
import com.ashu.shelflife.security.JwtService;
import com.ashu.shelflife.users.User;
import com.ashu.shelflife.users.UserRepository;
import com.ashu.shelflife.users.UserStatus;
import com.ashu.shelflife.warehouse.PickerWarehouseMappingRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authentication operations: login (issue token pair), refresh (rotate), logout (revoke).
 *
 * <p>Refresh tokens are opaque 256-bit random strings; only their SHA-256 hash is stored,
 * so revocation and rotation are enforced server-side via {@code refresh_tokens}.
 */
@Service
public class AuthService {

    private static final String ROLE_HUB_PICKER = "HUB_PICKER";

    private final UserRepository userRepository;
    private final PickerWarehouseMappingRepository mappingRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final long refreshTokenExpirationDays;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(UserRepository userRepository,
                       PickerWarehouseMappingRepository mappingRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       JwtService jwtService,
                       PasswordEncoder passwordEncoder,
                       ApplicationEventPublisher eventPublisher,
                       com.ashu.shelflife.security.JwtProperties jwtProperties) {
        this.userRepository = userRepository;
        this.mappingRepository = mappingRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
        this.refreshTokenExpirationDays = jwtProperties.refreshTokenExpirationDays();
    }

    /**
     * Verify credentials and issue an access token + a persistent refresh token.
     *
     * @throws BadCredentialsException if the identifier/password do not match
     * @throws DisabledException       if the account is not ACTIVE
     */
    @Transactional
    public TokenResponse login(String usernameOrEmail, String rawPassword) {
        User user = userRepository.findByEmailOrName(usernameOrEmail, usernameOrEmail)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials."));

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials.");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new DisabledException("Account is not active.");
        }

        String accessToken = jwtService.generateAccessToken(user, warehouseIdsFor(user));
        String refreshToken = issueRefreshToken(user.getId());
        eventPublisher.publishEvent(AuditEvent.of(AuditAction.LOGIN, user.getId()));
        return TokenResponse.bearer(accessToken, refreshToken, jwtService.getAccessTokenExpirationSeconds());
    }

    /**
     * Exchange a valid refresh token for a new access token, rotating the refresh token
     * (the presented one is revoked and a fresh one is issued).
     *
     * @throws BadCredentialsException if the token is unknown, revoked, or expired
     */
    @Transactional
    public TokenResponse refresh(String rawRefreshToken) {
        RefreshToken stored = refreshTokenRepository.findByTokenHash(sha256Hex(rawRefreshToken))
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token."));

        if (stored.isRevoked() || stored.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new BadCredentialsException("Refresh token is expired or revoked.");
        }

        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token."));

        // Rotate: revoke the presented token, issue a new pair.
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        String accessToken = jwtService.generateAccessToken(user, warehouseIdsFor(user));
        String newRefreshToken = issueRefreshToken(user.getId());
        return TokenResponse.bearer(accessToken, newRefreshToken, jwtService.getAccessTokenExpirationSeconds());
    }

    /**
     * Revoke the presented refresh token. Idempotent and scoped to the caller: a token is
     * only revoked if it belongs to {@code currentUserId}.
     */
    @Transactional
    public void logout(String rawRefreshToken, Long currentUserId) {
        refreshTokenRepository.findByTokenHash(sha256Hex(rawRefreshToken))
                .filter(token -> token.getUserId().equals(currentUserId))
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                });
        eventPublisher.publishEvent(AuditEvent.of(AuditAction.LOGOUT, currentUserId));
    }

    private List<Long> warehouseIdsFor(User user) {
        if (ROLE_HUB_PICKER.equals(user.getRole().getName())) {
            return mappingRepository.findWarehouseIdsByPickerId(user.getId());
        }
        // CENTRAL_ADMIN has global, cross-warehouse scope: no warehouse restriction.
        return List.of();
    }

    private String issueRefreshToken(Long userId) {
        byte[] random = new byte[32];
        secureRandom.nextBytes(random);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(random);

        RefreshToken entity = new RefreshToken();
        entity.setUserId(userId);
        entity.setTokenHash(sha256Hex(rawToken));
        entity.setExpiresAt(OffsetDateTime.now().plusDays(refreshTokenExpirationDays));
        entity.setRevoked(false);
        refreshTokenRepository.save(entity);

        return rawToken;
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
