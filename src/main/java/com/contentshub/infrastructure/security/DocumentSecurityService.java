package com.contentshub.infrastructure.security;

import com.contentshub.application.port.output.DocumentRepositoryPort;
import com.contentshub.application.port.output.UserRepositoryPort;
import com.contentshub.domain.valueobject.UserId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Servicio de seguridad para documentos
 */
@Service("documentSecurityService")
@RequiredArgsConstructor
@Slf4j
public class DocumentSecurityService {

    private final DocumentRepositoryPort documentRepository;

    /**
     * Verificar si el usuario puede ver el documento
     */
    public Mono<Boolean> canViewDocument(Authentication authentication, String documentId) {
        UserId userId = extractUserIdFromAuthentication(authentication);

        return documentRepository.canUserAccessDocument(documentId, userId)
                .doOnNext(canAccess -> log.debug("User {} can view document {}: {}",
                        userId, documentId, canAccess))
                .onErrorReturn(false);
    }

    /**
     * Verificar si el usuario puede editar el documento
     */
    public Mono<Boolean> canEditDocument(Authentication authentication, String documentId) {
        UserId userId = extractUserIdFromAuthentication(authentication);

        return documentRepository.findById(documentId)
                .map(document -> document.canWrite(userId))
                .doOnNext(canEdit -> log.debug("User {} can edit document {}: {}",
                        userId, documentId, canEdit))
                .onErrorReturn(false);
    }

    /**
     * Verificar si el usuario puede eliminar el documento
     */
    public Mono<Boolean> canDeleteDocument(Authentication authentication, String documentId) {
        UserId userId = extractUserIdFromAuthentication(authentication);

        return documentRepository.findById(documentId)
                .map(document -> document.getOwnerId().equals(userId) || isAdmin(authentication))
                .doOnNext(canDelete -> log.debug("User {} can delete document {}: {}",
                        userId, documentId, canDelete))
                .onErrorReturn(false);
    }

    /**
     * Verificar si el usuario puede compartir el documento
     */
    public Mono<Boolean> canShareDocument(Authentication authentication, String documentId) {
        // Por ahora, mismo logic que editar
        return canEditDocument(authentication, documentId);
    }

    private UserId extractUserIdFromAuthentication(Authentication authentication) {
        if (authentication.getPrincipal() instanceof JwtAuthenticationConverter.JwtUserPrincipal principal) {
            return UserId.of(principal.getUserId());
        }
        throw new IllegalStateException("Invalid authentication principal type");
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }
}

/**
 * Servicio de seguridad para usuarios
 */
@Service("userSecurityService")
@RequiredArgsConstructor
@Slf4j
class UserSecurityService {

    private final UserRepositoryPort userRepository;

    /**
     * Verificar si el usuario puede acceder a información de otro usuario
     */
    public Mono<Boolean> canAccessUser(Authentication authentication, Long targetUserId) {
        UserId currentUserId = extractUserIdFromAuthentication(authentication);

        // Un usuario siempre puede acceder a su propia información
        if (currentUserId.getValue().equals(targetUserId)) {
            return Mono.just(true);
        }

        // Los admins pueden acceder a cualquier usuario
        if (isAdmin(authentication)) {
            return Mono.just(true);
        }

        // Para otros casos, verificar permisos específicos
        return userRepository.hasPermission(currentUserId, "USER_READ")
                .doOnNext(hasPermission -> log.debug("User {} can access user {}: {}",
                        currentUserId, targetUserId, hasPermission));
    }

    /**
     * Verificar si el usuario puede modificar a otro usuario
     */
    public Mono<Boolean> canModifyUser(Authentication authentication, Long targetUserId) {
        UserId currentUserId = extractUserIdFromAuthentication(authentication);

        // Un usuario siempre puede modificar su propia información
        if (currentUserId.getValue().equals(targetUserId)) {
            return Mono.just(true);
        }

        // Los admins pueden modificar cualquier usuario
        if (isAdmin(authentication)) {
            return Mono.just(true);
        }

        // Para otros casos, verificar permisos específicos
        return userRepository.hasPermission(currentUserId, "USER_WRITE")
                .doOnNext(hasPermission -> log.debug("User {} can modify user {}: {}",
                        currentUserId, targetUserId, hasPermission));
    }

    /**
     * Verificar si el usuario puede eliminar a otro usuario
     */
    public Mono<Boolean> canDeleteUser(Authentication authentication, Long targetUserId) {
        UserId currentUserId = extractUserIdFromAuthentication(authentication);

        // Un usuario no puede eliminarse a sí mismo
        if (currentUserId.getValue().equals(targetUserId)) {
            return Mono.just(false);
        }

        // Solo los admins pueden eliminar usuarios
        return Mono.just(isAdmin(authentication))
                .doOnNext(canDelete -> log.debug("User {} can delete user {}: {}",
                        currentUserId, targetUserId, canDelete));
    }

    private UserId extractUserIdFromAuthentication(Authentication authentication) {
        if (authentication.getPrincipal() instanceof JwtAuthenticationConverter.JwtUserPrincipal principal) {
            return UserId.of(principal.getUserId());
        }
        throw new IllegalStateException("Invalid authentication principal type");
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }
}

/**
 * Servicio de seguridad general
 */
@Service("securityService")
@RequiredArgsConstructor
@Slf4j
class GeneralSecurityService {

    /**
     * Verificar si el usuario actual es administrador
     */
    public boolean isCurrentUserAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }

    /**
     * Verificar si el usuario actual es moderador
     */
    public boolean isCurrentUserModerator(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_MODERATOR"));
    }

    /**
     * Verificar si el usuario tiene un permiso específico
     */
    public boolean hasPermission(Authentication authentication, String permission) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(permission));
    }

    /**
     * Obtener ID del usuario actual
     */
    public UserId getCurrentUserId(Authentication authentication) {
        if (authentication.getPrincipal() instanceof JwtAuthenticationConverter.JwtUserPrincipal principal) {
            return UserId.of(principal.getUserId());
        }
        throw new IllegalStateException("Invalid authentication principal type");
    }

    /**
     * Obtener username del usuario actual
     */
    public String getCurrentUsername(Authentication authentication) {
        if (authentication.getPrincipal() instanceof JwtAuthenticationConverter.JwtUserPrincipal principal) {
            return principal.getUsername();
        }
        throw new IllegalStateException("Invalid authentication principal type");
    }

    /**
     * Obtener email del usuario actual
     */
    public String getCurrentUserEmail(Authentication authentication) {
        if (authentication.getPrincipal() instanceof JwtAuthenticationConverter.JwtUserPrincipal principal) {
            return principal.getEmail();
        }
        throw new IllegalStateException("Invalid authentication principal type");
    }

    /**
     * Verificar si el usuario actual es propietario del recurso
     */
    public boolean isResourceOwner(Authentication authentication, UserId ownerId) {
        UserId currentUserId = getCurrentUserId(authentication);
        return currentUserId.equals(ownerId);
    }

    /**
     * Verificar si el usuario puede realizar operación administrativa
     */
    public boolean canPerformAdminOperation(Authentication authentication) {
        return isCurrentUserAdmin(authentication) ||
                hasPermission(authentication, "ADMIN_OPERATIONS");
    }

    /**
     * Verificar si el usuario puede realizar operación de moderación
     */
    public boolean canPerformModerationOperation(Authentication authentication) {
        return isCurrentUserAdmin(authentication) ||
                isCurrentUserModerator(authentication) ||
                hasPermission(authentication, "MODERATION_OPERATIONS");
    }
}
