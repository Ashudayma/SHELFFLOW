package com.ashu.shelflife.users;

import com.ashu.shelflife.security.AuthenticatedUser;
import com.ashu.shelflife.users.dto.CreateUserRequest;
import com.ashu.shelflife.users.dto.UpdateUserRequest;
import com.ashu.shelflife.users.dto.UserResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * User management for HUB_PICKER and CENTRAL_ADMIN accounts. CRUD is CENTRAL_ADMIN-only
 * (global scope); {@code /users/me} is available to any authenticated user. Every method
 * carries an explicit {@code @PreAuthorize} per the project RBAC policy.
 */
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasRole('CENTRAL_ADMIN')")
    public List<UserResponse> list() {
        return userService.listAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('CENTRAL_ADMIN')")
    public UserResponse get(@PathVariable Long id) {
        return userService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('CENTRAL_ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return userService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('CENTRAL_ADMIN')")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return userService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('CENTRAL_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                       @AuthenticationPrincipal AuthenticatedUser principal) {
        userService.delete(id, principal.id());
        return ResponseEntity.noContent().build();
    }

    /** Any authenticated user: details of the current principal. */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return Map.of(
                "id", principal.id(),
                "email", principal.email(),
                "role", principal.role(),
                "warehouseIds", principal.warehouseIds());
    }
}
