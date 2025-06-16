package com.contentshub.infrastructure.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Servicio reactivo para cargar detalles de usuario desde la base de datos
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomReactiveUserDetailsService implements ReactiveUserDetailsService {

    private final DatabaseClient databaseClient;

    @Override
    public Mono<UserDetails> findByUsername(String username) {
        log.debug("Buscando usuario: {}", username);

        return findUserWithRoles(username)
                .cast(UserDetails.class)
                .doOnNext(user -> log.debug("Usuario encontrado: {}", user.getUsername()))
                .doOnError(error -> log.error("Error buscando usuario {}: {}", username, error.getMessage()))
                .switchIfEmpty(Mono.error(new UsernameNotFoundException("Usuario no encontrado: " + username)));
    }

    /**
     * Busca un usuario con sus roles y permisos
     */
    private Mono<UserDetails> findUserWithRoles(String username) {
        // Consulta para obtener usuario básico
        String userQuery = """
            SELECT u.id, u.username, u.email, u.password_hash, 
                   u.is_enabled, u.is_account_non_expired, 
                   u.is_account_non_locked, u.is_credentials_non_expired
            FROM users u 
            WHERE u.username = :username OR u.email = :username
            """;

        return databaseClient.sql(userQuery)
                .bind("username", username)
                .map((row, metadata) -> UserInfo.builder()
                        .id(row.get("id", Long.class))
                        .username(row.get("username", String.class))
                        .email(row.get("email", String.class))
                        .passwordHash(row.get("password_hash", String.class))
                        .enabled(row.get("is_enabled", Boolean.class))
                        .accountNonExpired(row.get("is_account_non_expired", Boolean.class))
                        .accountNonLocked(row.get("is_account_non_locked", Boolean.class))
                        .credentialsNonExpired(row.get("is_credentials_non_expired", Boolean.class))
                        .build())
                .one()
                .flatMap(this::loadUserAuthorities);
    }

    /**
     * Carga las autoridades (roles y permisos) del usuario
     */
    private Mono<UserDetails> loadUserAuthorities(UserInfo userInfo) {
        // Consulta para obtener roles y permisos del usuario
        String authoritiesQuery = """
            SELECT DISTINCT r.name as role_name, p.name as permission_name
            FROM users u
            LEFT JOIN user_roles ur ON u.id = ur.user_id
            LEFT JOIN roles r ON ur.role_id = r.id AND r.is_active = true
            LEFT JOIN role_permissions rp ON r.id = rp.role_id
            LEFT JOIN permissions p ON rp.permission_id = p.id
            WHERE u.id = :userId
            """;

        return databaseClient.sql(authoritiesQuery)
                .bind("userId", userInfo.getId())
                .map((row, metadata) -> AuthorityInfo.builder()
                        .roleName(row.get("role_name", String.class))
                        .permissionName(row.get("permission_name", String.class))
                        .build())
                .all()
                .collectList()
                .map(authorities -> buildUserDetails(userInfo, authorities));
    }

    /**
     * Construye el objeto UserDetails final
     */
    private UserDetails buildUserDetails(UserInfo userInfo, List<AuthorityInfo> authorities) {
        Collection<GrantedAuthority> grantedAuthorities = authorities.stream()
                .filter(auth -> auth.getRoleName() != null || auth.getPermissionName() != null)
                .flatMap(auth -> {
                    return List.of(
                            auth.getRoleName() != null ? new SimpleGrantedAuthority(auth.getRoleName()) : null,
                            auth.getPermissionName() != null ? new SimpleGrantedAuthority(auth.getPermissionName()) : null
                    ).stream().filter(a -> a != null);
                })
                .collect(Collectors.toSet());

        // Si no tiene roles explícitos, asignar ROLE_USER por defecto
        if (grantedAuthorities.isEmpty()) {
            grantedAuthorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        }

        log.debug("Autoridades cargadas para {}: {}", userInfo.getUsername(), grantedAuthorities);

        return User.builder()
                .username(userInfo.getUsername())
                .password(userInfo.getPasswordHash())
                .authorities(grantedAuthorities)
                .accountExpired(!userInfo.isAccountNonExpired())
                .accountLocked(!userInfo.isAccountNonLocked())
                .credentialsExpired(!userInfo.isCredentialsNonExpired())
                .disabled(!userInfo.isEnabled())
                .build();
    }

    /**
     * DTO para información básica del usuario
     */
    @lombok.Builder
    @lombok.Data
    private static class UserInfo {
        private Long id;
        private String username;
        private String email;
        private String passwordHash;
        private boolean enabled;
        private boolean accountNonExpired;
        private boolean accountNonLocked;
        private boolean credentialsNonExpired;
    }

    /**
     * DTO para información de autoridades
     */
    @lombok.Builder
    @lombok.Data
    private static class AuthorityInfo {
        private String roleName;
        private String permissionName;
    }

    /**
     * Método auxiliar para obtener información extendida del usuario por ID
     */
    public Mono<ExtendedUserInfo> findExtendedUserInfo(Long userId) {
        String query = """
            SELECT u.id, u.uuid, u.username, u.email, u.first_name, u.last_name, 
                   u.profile_picture_url, u.last_login_at, u.created_at
            FROM users u 
            WHERE u.id = :userId AND u.is_enabled = true
            """;

        return databaseClient.sql(query)
                .bind("userId", userId)
                .map((row, metadata) -> ExtendedUserInfo.builder()
                        .id(row.get("id", Long.class))
                        .uuid(row.get("uuid", java.util.UUID.class))
                        .username(row.get("username", String.class))
                        .email(row.get("email", String.class))
                        .firstName(row.get("first_name", String.class))
                        .lastName(row.get("last_name", String.class))
                        .profilePictureUrl(row.get("profile_picture_url", String.class))
                        .lastLoginAt(row.get("last_login_at", java.time.LocalDateTime.class))
                        .createdAt(row.get("created_at", java.time.LocalDateTime.class))
                        .build())
                .one();
    }

    /**
     * DTO para información extendida del usuario
     */
    @lombok.Builder
    @lombok.Data
    public static class ExtendedUserInfo {
        private Long id;
        private java.util.UUID uuid;
        private String username;
        private String email;
        private String firstName;
        private String lastName;
        private String profilePictureUrl;
        private java.time.LocalDateTime lastLoginAt;
        private java.time.LocalDateTime createdAt;
    }
}
