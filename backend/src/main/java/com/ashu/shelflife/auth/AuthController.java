package com.ashu.shelflife.auth;

import com.ashu.shelflife.auth.dto.LoginRequest;
import com.ashu.shelflife.auth.dto.LogoutRequest;
import com.ashu.shelflife.auth.dto.RefreshRequest;
import com.ashu.shelflife.auth.dto.TokenResponse;
import com.ashu.shelflife.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication endpoints. Login and refresh are public; logout requires a valid access
 * token. Every method carries an explicit {@code @PreAuthorize} per the project RBAC policy.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @PreAuthorize("permitAll()")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request.usernameOrEmail(), request.password()));
    }

    @PostMapping("/refresh")
    @PreAuthorize("permitAll()")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request,
                                       @AuthenticationPrincipal AuthenticatedUser principal) {
        authService.logout(request.refreshToken(), principal.id());
        return ResponseEntity.noContent().build();
    }
}
