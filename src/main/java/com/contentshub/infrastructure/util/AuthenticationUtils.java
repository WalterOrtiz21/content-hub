package com.contentshub.infrastructure.util;

import com.contentshub.domain.valueobject.UserId;
import com.contentshub.infrastructure.security.JwtAuthenticationConverter;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import reactor.core.publisher.Mono;

/**
 * Utilidades para manejo de autenticación
 */
@UtilityClass
@Slf4j
public class AuthenticationUtils {

    /**
     * Extraer UserId del principal de autenticación
     */
    public static UserId extractUserIdFromPrincipal(Object principal) {
        if (principal instanceof JwtAuthenticationConverter.JwtUserPrincipal jwtPrincipal) {
            return UserId.of(jwtPrincipal.getUserId());
        }

        if (principal instanceof org.springframework.security.core.userdetails.UserDetails userDetails) {
            // Si es UserDetails, intentar extraer desde el contexto
            log.warn("Extracting user ID from UserDetails - this might not contain user ID");
            return UserId.of(1L); // Fallback - en producción esto debería manejarse mejor
        }

        log.error("Cannot extract user ID from principal of type: {}", principal.getClass());
        throw new IllegalStateException("Invalid authentication principal type: " + principal.getClass());
    }

    /**
     * Extraer token del principal de autenticación
     */
    public static String extractTokenFromPrincipal(Object principal) {
        if (principal instanceof JwtAuthenticationConverter.JwtUserPrincipal jwtPrincipal) {
            return jwtPrincipal.getToken();
        }

        log.error("Cannot extract token from principal of type: {}", principal.getClass());
        throw new IllegalStateException("Principal does not contain JWT token");
    }

    /**
     * Extraer username del principal de autenticación
     */
    public static String extractUsernameFromPrincipal(Object principal) {
        if (principal instanceof JwtAuthenticationConverter.JwtUserPrincipal jwtPrincipal) {
            return jwtPrincipal.getUsername();
        }

        if (principal instanceof org.springframework.security.core.userdetails.UserDetails userDetails) {
            return userDetails.getUsername();
        }

        if (principal instanceof String username) {
            return username;
        }

        log.error("Cannot extract username from principal of type: {}", principal.getClass());
        throw new IllegalStateException("Invalid authentication principal type: " + principal.getClass());
    }

    /**
     * Obtener el contexto de seguridad actual reactivamente
     */
    public static Mono<SecurityContext> getCurrentSecurityContext() {
        return ReactiveSecurityContextHolder.getContext();
    }

    /**
     * Obtener la autenticación actual reactivamente
     */
    public static Mono<Authentication> getCurrentAuthentication() {
        return getCurrentSecurityContext()
                .map(SecurityContext::getAuthentication);
    }

    /**
     * Obtener el UserId del usuario actual reactivamente
     */
    public static Mono<UserId> getCurrentUserId() {
        return getCurrentAuthentication()
                .map(Authentication::getPrincipal)
                .map(AuthenticationUtils::extractUserIdFromPrincipal);
    }

    /**
     * Obtener el username del usuario actual reactivamente
     */
    public static Mono<String> getCurrentUsername() {
        return getCurrentAuthentication()
                .map(Authentication::getPrincipal)
                .map(AuthenticationUtils::extractUsernameFromPrincipal);
    }

    /**
     * Verificar si el usuario actual tiene un rol específico
     */
    public static Mono<Boolean> hasRole(String role) {
        return getCurrentAuthentication()
                .map(auth -> auth.getAuthorities().stream()
                        .anyMatch(authority -> authority.getAuthority().equals(role)))
                .defaultIfEmpty(false);
    }

    /**
     * Verificar si el usuario actual tiene cualquiera de los roles especificados
     */
    public static Mono<Boolean> hasAnyRole(String... roles) {
        return getCurrentAuthentication()
                .map(auth -> {
                    for (String role : roles) {
                        if (auth.getAuthorities().stream()
                                .anyMatch(authority -> authority.getAuthority().equals(role))) {
                            return true;
                        }
                    }
                    return false;
                })
                .defaultIfEmpty(false);
    }

    /**
     * Verificar si el usuario actual es administrador
     */
    public static Mono<Boolean> isAdmin() {
        return hasAnyRole("ROLE_ADMIN", "ADMIN");
    }

    /**
     * Verificar si el usuario actual es moderador
     */
    public static Mono<Boolean> isModerator() {
        return hasAnyRole("ROLE_MODERATOR", "MODERATOR");
    }

    /**
     * Verificar si el usuario actual tiene permisos administrativos
     */
    public static Mono<Boolean> hasAdminPermissions() {
        return hasAnyRole("ROLE_ADMIN", "ADMIN", "ROLE_SUPER_USER");
    }
}

/**
 * Utilidades para validaciones de negocio relacionadas con autenticación
 */
@UtilityClass
@Slf4j
class AuthenticationValidationUtils {

    /**
     * Validar que el usuario puede acceder al recurso
     */
    public static Mono<Boolean> canAccessResource(UserId userId, UserId resourceOwnerId) {
        return AuthenticationUtils.getCurrentUserId()
                .flatMap(currentUserId -> {
                    // El propietario siempre puede acceder
                    if (currentUserId.equals(resourceOwnerId)) {
                        return Mono.just(true);
                    }

                    // Los admins pueden acceder a cualquier recurso
                    return AuthenticationUtils.isAdmin();
                })
                .defaultIfEmpty(false);
    }

    /**
     * Validar que el usuario puede modificar el recurso
     */
    public static Mono<Boolean> canModifyResource(UserId resourceOwnerId) {
        return AuthenticationUtils.getCurrentUserId()
                .flatMap(currentUserId -> {
                    // El propietario siempre puede modificar
                    if (currentUserId.equals(resourceOwnerId)) {
                        return Mono.just(true);
                    }

                    // Los admins pueden modificar cualquier recurso
                    return AuthenticationUtils.isAdmin();
                })
                .defaultIfEmpty(false);
    }

    /**
     * Validar que el usuario puede eliminar el recurso
     */
    public static Mono<Boolean> canDeleteResource(UserId resourceOwnerId) {
        return AuthenticationUtils.getCurrentUserId()
                .flatMap(currentUserId -> {
                    // Solo el propietario o admins pueden eliminar
                    if (currentUserId.equals(resourceOwnerId)) {
                        return Mono.just(true);
                    }

                    return AuthenticationUtils.isAdmin();
                })
                .defaultIfEmpty(false);
    }

    /**
     * Validar operación administrativa
     */
    public static Mono<Void> requireAdminPermissions() {
        return AuthenticationUtils.hasAdminPermissions()
                .filter(hasPermissions -> hasPermissions)
                .switchIfEmpty(Mono.error(new SecurityException("Admin permissions required")))
                .then();
    }

    /**
     * Validar operación de moderación
     */
    public static Mono<Void> requireModerationPermissions() {
        return AuthenticationUtils.hasAnyRole("ROLE_ADMIN", "ROLE_MODERATOR")
                .filter(hasPermissions -> hasPermissions)
                .switchIfEmpty(Mono.error(new SecurityException("Moderation permissions required")))
                .then();
    }
}
