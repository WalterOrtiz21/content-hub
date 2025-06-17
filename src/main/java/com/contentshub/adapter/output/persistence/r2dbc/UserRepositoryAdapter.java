package com.contentshub.adapter.output.persistence.r2dbc;

import com.contentshub.application.port.output.UserRepositoryPort;
import com.contentshub.domain.model.Permission;
import com.contentshub.domain.model.Role;
import com.contentshub.domain.model.User;
import com.contentshub.domain.valueobject.Email;
import com.contentshub.domain.valueobject.UserId;
import com.contentshub.domain.valueobject.Username;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementación del repositorio de usuarios usando R2DBC para PostgreSQL
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class UserRepositoryAdapter implements UserRepositoryPort {

    private final DatabaseClient databaseClient;
    private final UserMapper userMapper;

    @Override
    public Mono<User> save(User user) {
        if (user.getId() == null) {
            return createUser(user);
        } else {
            return updateUser(user);
        }
    }

    private Mono<User> createUser(User user) {
        String sql = """
                INSERT INTO users (uuid, username, email, password_hash, first_name, last_name, 
                                 profile_picture_url, is_enabled, is_account_non_expired, 
                                 is_account_non_locked, is_credentials_non_expired, 
                                 created_by, updated_by)
                VALUES (:uuid, :username, :email, :passwordHash, :firstName, :lastName, 
                        :profilePictureUrl, :isEnabled, :isAccountNonExpired, 
                        :isAccountNonLocked, :isCredentialsNonExpired, 
                        :createdBy, :updatedBy)
                RETURNING *
                """;

        return databaseClient.sql(sql)
                .bind("uuid", user.getUuid())
                .bind("username", user.getUsername().getValue())
                .bind("email", user.getEmail().getValue())
                .bind("passwordHash", user.getPasswordHash())
                .bind("firstName", user.getFirstName())
                .bind("lastName", user.getLastName())
                .bind("profilePictureUrl", user.getProfilePictureUrl())
                .bind("isEnabled", user.getIsEnabled())
                .bind("isAccountNonExpired", user.getIsAccountNonExpired())
                .bind("isAccountNonLocked", user.getIsAccountNonLocked())
                .bind("isCredentialsNonExpired", user.getIsCredentialsNonExpired())
                .bind("createdBy", user.getCreatedBy())
                .bind("updatedBy", user.getUpdatedBy())
                .map(userMapper::mapToUser)
                .one()
                .flatMap(this::loadUserWithRoles)
                .doOnSuccess(savedUser -> log.debug("User created: {}", savedUser.getUsername()))
                .doOnError(error -> log.error("Error creating user: {}", error.getMessage()));
    }

    private Mono<User> updateUser(User user) {
        String sql = """
                UPDATE users 
                SET username = :username, email = :email, password_hash = :passwordHash,
                    first_name = :firstName, last_name = :lastName, 
                    profile_picture_url = :profilePictureUrl, is_enabled = :isEnabled,
                    is_account_non_expired = :isAccountNonExpired, 
                    is_account_non_locked = :isAccountNonLocked,
                    is_credentials_non_expired = :isCredentialsNonExpired,
                    last_login_at = :lastLoginAt, updated_by = :updatedBy,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                RETURNING *
                """;

        return databaseClient.sql(sql)
                .bind("id", user.getId())
                .bind("username", user.getUsername().getValue())
                .bind("email", user.getEmail().getValue())
                .bind("passwordHash", user.getPasswordHash())
                .bind("firstName", user.getFirstName())
                .bind("lastName", user.getLastName())
                .bind("profilePictureUrl", user.getProfilePictureUrl())
                .bind("isEnabled", user.getIsEnabled())
                .bind("isAccountNonExpired", user.getIsAccountNonExpired())
                .bind("isAccountNonLocked", user.getIsAccountNonLocked())
                .bind("isCredentialsNonExpired", user.getIsCredentialsNonExpired())
                .bind("lastLoginAt", user.getLastLoginAt())
                .bind("updatedBy", user.getUpdatedBy())
                .map(userMapper::mapToUser)
                .one()
                .flatMap(this::loadUserWithRoles)
                .doOnSuccess(updatedUser -> log.debug("User updated: {}", updatedUser.getUsername()))
                .doOnError(error -> log.error("Error updating user: {}", error.getMessage()));
    }

    @Override
    public Mono<User> findById(UserId userId) {
        String sql = "SELECT * FROM users WHERE id = :id";

        return databaseClient.sql(sql)
                .bind("id", userId.getValue())
                .map(userMapper::mapToUser)
                .one()
                .flatMap(this::loadUserWithRoles)
                .doOnSuccess(user -> log.debug("User found by ID: {}", userId))
                .onErrorResume(error -> {
                    log.debug("User not found by ID: {}", userId);
                    return Mono.empty();
                });
    }

    @Override
    public Mono<User> findByUsername(Username username) {
        String sql = "SELECT * FROM users WHERE username = :username";

        return databaseClient.sql(sql)
                .bind("username", username.getValue())
                .map(userMapper::mapToUser)
                .one()
                .flatMap(this::loadUserWithRoles)
                .doOnSuccess(user -> log.debug("User found by username: {}", username))
                .onErrorResume(error -> {
                    log.debug("User not found by username: {}", username);
                    return Mono.empty();
                });
    }

    @Override
    public Mono<User> findByEmail(Email email) {
        String sql = "SELECT * FROM users WHERE email = :email";

        return databaseClient.sql(sql)
                .bind("email", email.getValue())
                .map(userMapper::mapToUser)
                .one()
                .flatMap(this::loadUserWithRoles)
                .doOnSuccess(user -> log.debug("User found by email: {}", email))
                .onErrorResume(error -> {
                    log.debug("User not found by email: {}", email);
                    return Mono.empty();
                });
    }

    @Override
    public Mono<User> findByUsernameOrEmail(String usernameOrEmail) {
        String sql = "SELECT * FROM users WHERE username = :value OR email = :value";

        return databaseClient.sql(sql)
                .bind("value", usernameOrEmail)
                .map(userMapper::mapToUser)
                .one()
                .flatMap(this::loadUserWithRoles)
                .doOnSuccess(user -> log.debug("User found by username or email: {}", usernameOrEmail))
                .onErrorResume(error -> {
                    log.debug("User not found by username or email: {}", usernameOrEmail);
                    return Mono.empty();
                });
    }

    @Override
    public Mono<Boolean> existsByUsername(Username username) {
        String sql = "SELECT COUNT(*) FROM users WHERE username = :username";

        return databaseClient.sql(sql)
                .bind("username", username.getValue())
                .map((row, metadata) -> row.get(0, Long.class))
                .one()
                .map(count -> count > 0)
                .defaultIfEmpty(false);
    }

    @Override
    public Mono<Boolean> existsByEmail(Email email) {
        String sql = "SELECT COUNT(*) FROM users WHERE email = :email";

        return databaseClient.sql(sql)
                .bind("email", email.getValue())
                .map((row, metadata) -> row.get(0, Long.class))
                .one()
                .map(count -> count > 0)
                .defaultIfEmpty(false);
    }

    @Override
    public Mono<Void> deleteById(UserId userId) {
        String sql = "DELETE FROM users WHERE id = :id";

        return databaseClient.sql(sql)
                .bind("id", userId.getValue())
                .then()
                .doOnSuccess(unused -> log.debug("User deleted: {}", userId))
                .doOnError(error -> log.error("Error deleting user {}: {}", userId, error.getMessage()));
    }

    @Override
    public Flux<User> findAllActive() {
        String sql = """
                SELECT * FROM users 
                WHERE is_enabled = true 
                  AND is_account_non_expired = true 
                  AND is_account_non_locked = true
                ORDER BY created_at DESC
                """;

        return databaseClient.sql(sql)
                .map(userMapper::mapToUser)
                .all()
                .flatMap(this::loadUserWithRoles);
    }

    @Override
    public Flux<User> findByRole(String roleName) {
        String sql = """
                SELECT DISTINCT u.* FROM users u
                JOIN user_roles ur ON u.id = ur.user_id
                JOIN roles r ON ur.role_id = r.id
                WHERE r.name = :roleName AND r.is_active = true
                ORDER BY u.created_at DESC
                """;

        return databaseClient.sql(sql)
                .bind("roleName", roleName)
                .map(userMapper::mapToUser)
                .all()
                .flatMap(this::loadUserWithRoles);
    }

    @Override
    public Flux<User> findByCreatedAtAfter(LocalDateTime date) {
        String sql = "SELECT * FROM users WHERE created_at > :date ORDER BY created_at DESC";

        return databaseClient.sql(sql)
                .bind("date", date)
                .map(userMapper::mapToUser)
                .all()
                .flatMap(this::loadUserWithRoles);
    }

    @Override
    public Flux<User> findByNameOrEmailPattern(String pattern) {
        String sql = """
                SELECT * FROM users 
                WHERE LOWER(first_name) LIKE LOWER(:pattern) 
                   OR LOWER(last_name) LIKE LOWER(:pattern)
                   OR LOWER(email) LIKE LOWER(:pattern)
                   OR LOWER(username) LIKE LOWER(:pattern)
                ORDER BY username
                """;

        String searchPattern = "%" + pattern + "%";

        return databaseClient.sql(sql)
                .bind("pattern", searchPattern)
                .map(userMapper::mapToUser)
                .all()
                .flatMap(this::loadUserWithRoles);
    }

    @Override
    public Mono<Long> countActiveUsers() {
        String sql = """
                SELECT COUNT(*) FROM users 
                WHERE is_enabled = true 
                  AND is_account_non_expired = true 
                  AND is_account_non_locked = true
                """;

        return databaseClient.sql(sql)
                .map((row, metadata) -> row.get(0, Long.class))
                .one()
                .defaultIfEmpty(0L);
    }

    @Override
    public Mono<Long> countByRole(String roleName) {
        String sql = """
                SELECT COUNT(DISTINCT u.id) FROM users u
                JOIN user_roles ur ON u.id = ur.user_id
                JOIN roles r ON ur.role_id = r.id
                WHERE r.name = :roleName AND r.is_active = true
                """;

        return databaseClient.sql(sql)
                .bind("roleName", roleName)
                .map((row, metadata) -> row.get(0, Long.class))
                .one()
                .defaultIfEmpty(0L);
    }

    @Override
    public Mono<User> updateLastLogin(UserId userId, LocalDateTime loginTime) {
        String sql = """
                UPDATE users 
                SET last_login_at = :loginTime, updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                RETURNING *
                """;

        return databaseClient.sql(sql)
                .bind("id", userId.getValue())
                .bind("loginTime", loginTime)
                .map(userMapper::mapToUser)
                .one()
                .flatMap(this::loadUserWithRoles);
    }

    @Override
    public Flux<User> findAllWithPagination(int page, int size) {
        String sql = """
                SELECT * FROM users 
                ORDER BY created_at DESC 
                LIMIT :size OFFSET :offset
                """;

        return databaseClient.sql(sql)
                .bind("size", size)
                .bind("offset", page * size)
                .map(userMapper::mapToUser)
                .all()
                .flatMap(this::loadUserWithRoles);
    }

    @Override
    public Flux<User> findInactiveUsers(LocalDateTime cutoffDate) {
        String sql = """
                SELECT * FROM users 
                WHERE (last_login_at IS NULL OR last_login_at < :cutoffDate)
                  AND created_at < :cutoffDate
                ORDER BY created_at ASC
                """;

        return databaseClient.sql(sql)
                .bind("cutoffDate", cutoffDate)
                .map(userMapper::mapToUser)
                .all()
                .flatMap(this::loadUserWithRoles);
    }

    @Override
    public Mono<User> updateProfile(UserId userId, String firstName, String lastName, String profilePictureUrl) {
        String sql = """
                UPDATE users 
                SET first_name = :firstName, last_name = :lastName, 
                    profile_picture_url = :profilePictureUrl, updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                RETURNING *
                """;

        return databaseClient.sql(sql)
                .bind("id", userId.getValue())
                .bind("firstName", firstName)
                .bind("lastName", lastName)
                .bind("profilePictureUrl", profilePictureUrl)
                .map(userMapper::mapToUser)
                .one()
                .flatMap(this::loadUserWithRoles);
    }

    @Override
    public Mono<User> updateActivationStatus(UserId userId, boolean isActive) {
        String sql = """
                UPDATE users 
                SET is_enabled = :isActive, updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                RETURNING *
                """;

        return databaseClient.sql(sql)
                .bind("id", userId.getValue())
                .bind("isActive", isActive)
                .map(userMapper::mapToUser)
                .one()
                .flatMap(this::loadUserWithRoles);
    }

    @Override
    public Mono<Boolean> hasPermission(UserId userId, String permission) {
        String sql = """
                SELECT COUNT(*) FROM users u
                JOIN user_roles ur ON u.id = ur.user_id
                JOIN role_permissions rp ON ur.role_id = rp.role_id
                JOIN permissions p ON rp.permission_id = p.id
                WHERE u.id = :userId AND p.name = :permission
                """;

        return databaseClient.sql(sql)
                .bind("userId", userId.getValue())
                .bind("permission", permission)
                .map((row, metadata) -> row.get(0, Long.class))
                .one()
                .map(count -> count > 0)
                .defaultIfEmpty(false);
    }

    @Override
    public Flux<String> getUserPermissions(UserId userId) {
        String sql = """
                SELECT DISTINCT p.name FROM users u
                JOIN user_roles ur ON u.id = ur.user_id
                JOIN role_permissions rp ON ur.role_id = rp.role_id
                JOIN permissions p ON rp.permission_id = p.id
                WHERE u.id = :userId
                """;

        return databaseClient.sql(sql)
                .bind("userId", userId.getValue())
                .map((row, metadata) -> row.get("name", String.class))
                .all();
    }

    /**
     * Cargar usuario con sus roles y permisos
     */
    private Mono<User> loadUserWithRoles(User user) {
        if (user.getId() == null) {
            return Mono.just(user);
        }

        return loadUserRoles(user.getId())
                .collect(Collectors.toSet())
                .map(roles -> user.withRoles(roles));
    }

    /**
     * Cargar roles del usuario
     */
    private Flux<Role> loadUserRoles(Long userId) {
        String sql = """
                SELECT r.id, r.name, r.description, r.is_active, r.created_at, r.updated_at
                FROM roles r
                JOIN user_roles ur ON r.id = ur.role_id
                WHERE ur.user_id = :userId AND r.is_active = true
                """;

        return databaseClient.sql(sql)
                .bind("userId", userId)
                .map(userMapper::mapToRole)
                .all()
                .flatMap(this::loadRolePermissions);
    }

    /**
     * Cargar permisos del rol
     */
    private Mono<Role> loadRolePermissions(Role role) {
        String sql = """
                SELECT p.id, p.name, p.description, p.resource, p.action, p.created_at
                FROM permissions p
                JOIN role_permissions rp ON p.id = rp.permission_id
                WHERE rp.role_id = :roleId
                """;

        return databaseClient.sql(sql)
                .bind("roleId", role.getId())
                .map(userMapper::mapToPermission)
                .all()
                .collect(Collectors.toSet())
                .map(role::withPermissions);
    }
}
