package com.ashu.shelflife.users;

import com.ashu.shelflife.auth.RefreshTokenRepository;
import com.ashu.shelflife.common.error.ConflictException;
import com.ashu.shelflife.common.error.NotFoundException;
import com.ashu.shelflife.users.dto.CreateUserRequest;
import com.ashu.shelflife.users.dto.UpdateUserRequest;
import com.ashu.shelflife.users.dto.UserResponse;
import com.ashu.shelflife.warehouse.PickerWarehouseMappingRepository;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin user management for HUB_PICKER and CENTRAL_ADMIN accounts.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final PickerWarehouseMappingRepository mappingRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    public UserService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder,
                       PickerWarehouseMappingRepository mappingRepository,
                       RefreshTokenRepository refreshTokenRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.mappingRepository = mappingRepository;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listAll() {
        return userRepository.findAll().stream().map(UserResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        return UserResponse.from(findOrThrow(id));
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email already in use: " + request.email());
        }
        User user = new User();
        user.setName(request.name());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(resolveRole(request.role()));
        user.setStatus(request.status() != null ? request.status() : UserStatus.ACTIVE);
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request) {
        User user = findOrThrow(id);
        if (userRepository.existsByEmailAndIdNot(request.email(), id)) {
            throw new ConflictException("Email already in use: " + request.email());
        }
        user.setName(request.name());
        user.setEmail(request.email());
        user.setRole(resolveRole(request.role()));
        user.setStatus(request.status());
        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        return UserResponse.from(userRepository.save(user));
    }

    /**
     * Hard-delete a user, first removing dependent rows (warehouse mappings, refresh tokens)
     * so referential constraints do not block the deletion. An admin may not delete their
     * own account.
     */
    @Transactional
    public void delete(Long id, Long currentUserId) {
        if (id.equals(currentUserId)) {
            throw new IllegalArgumentException("You cannot delete your own account.");
        }
        User user = findOrThrow(id);
        mappingRepository.deleteByPickerId(id);
        refreshTokenRepository.deleteByUserId(id);
        userRepository.delete(user);
    }

    private User findOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found: " + id));
    }

    private Role resolveRole(String roleName) {
        return roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalArgumentException("Unknown role: " + roleName));
    }
}
