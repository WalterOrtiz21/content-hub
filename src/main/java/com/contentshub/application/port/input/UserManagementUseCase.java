package com.contentshub.application.port.input;

import com.contentshub.domain.model.User;
import com.contentshub.domain.valueobject.Email;
import com.contentshub.domain.valueobject.UserId;
import com.contentshub.domain.valueobject.Username;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Puerto de entrada para casos de uso de gestión de usuarios
 */
public interface UserManagementUseCase {

    /**
     * Crear nuevo usuario
     */
    Mono<UserResponse> createUser(CreateUserCommand command);

    /**
     * Obtener usuario por ID
     */
    Mono<UserResponse> getUserById(UserId userId);

    /**
     * Obtener usuario por username
     */
    Mono<UserResponse> getUserByUsername(Username username);

    /**
     * Obtener usuario por email
     */
    Mono<UserResponse> getUserByEmail(Email email);

    /**
     * Actualizar perfil de usuario
     */
    Mono<UserResponse> updateUserProfile(UpdateUserProfileCommand command);

    /**
     * Cambiar contraseña
     */
    Mono<Void> changePassword(ChangePasswordCommand command);

    /**
     * Activar usuario
     */
    Mono<UserResponse> activateUser(UserId userId, String activatedBy);

    /**
     * Desactivar usuario
     */
    Mono<UserResponse> deactivateUser(UserId userId, String deactivatedBy, String reason);

    /**
     * Asignar roles a usuario
     */
    Mono<UserResponse> assignRoles(UserId userId, Set<String> roleNames, String assignedBy);

    /**
     * Remover roles de usuario
     */
    Mono<UserResponse> removeRoles(UserId userId, Set<String> roleNames, String removedBy);

    /**
     * Obtener todos los usuarios con paginación
     */
    Flux<UserResponse> getAllUsers(int page, int size);

    /**
     * Buscar usuarios
     */
    Flux<UserResponse> searchUsers(UserSearchCriteria criteria);

    /**
     * Eliminar usuario (soft delete)
     */
    Mono<Void> deleteUser(UserId userId, String deletedBy);

    /**
     * Obtener estadísticas de usuarios
     */
    Mono<UserStatistics> getUserStatistics();

    /**
     * Commands (DTOs de entrada)
     */
    record CreateUserCommand(
            String username,
            String email,
            String password,
            String firstName,
            String lastName,
            String createdBy
    ) {
    }

    record UpdateUserProfileCommand(
            UserId userId,
            String firstName,
            String lastName,
            String profilePictureUrl,
            String updatedBy
    ) {
    }

    record ChangePasswordCommand(
            UserId userId,
            String currentPassword,
            String newPassword,
            String changedBy
    ) {
    }

    /**
     * Criterios de búsqueda
     */
    record UserSearchCriteria(
            String usernamePattern,
            String emailPattern,
            String namePattern,
            Boolean isActive,
            Set<String> roles,
            int page,
            int size
    ) {
    }

    /**
     * Response DTO
     */
    record UserResponse(
            UserId userId,
            String uuid,
            String username,
            String email,
            String firstName,
            String lastName,
            String profilePictureUrl,
            boolean isEnabled,
            boolean isAccountNonExpired,
            boolean isAccountNonLocked,
            boolean isCredentialsNonExpired,
            boolean isActive,
            LocalDateTime lastLoginAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            Set<String> roles,
            Set<String> permissions
    ) {
        /**
         * Factory method para crear desde entidad User
         */
        public static UserResponse fromUser(User user) {
            return new UserResponse(
                    user.getUserId(),
                    user.getUuid() != null ? user.getUuid().toString() : null,
                    user.getUsername().getValue(),
                    user.getEmail().getValue(),
                    user.getFirstName(),
                    user.getLastName(),
                    user.getProfilePictureUrl(),
                    user.getIsEnabled(),
                    user.getIsAccountNonExpired(),
                    user.getIsAccountNonLocked(),
                    user.getIsCredentialsNonExpired(),
                    user.isActive(),
                    user.getLastLoginAt(),
                    user.getCreatedAt(),
                    user.getUpdatedAt(),
                    user.getRoles() != null ?
                            user.getRoles().stream()
                                    .map(role -> role.getName())
                                    .collect(java.util.stream.Collectors.toSet()) :
                            Set.of(),
                    user.getAllPermissions()
            );
        }

        /**
         * Obtener nombre completo
         */
        public String getFullName() {
            if (firstName == null && lastName == null) {
                return username;
            }
            return String.format("%s %s",
                    firstName != null ? firstName : "",
                    lastName != null ? lastName : "").trim();
        }

        /**
         * Verificar si tiene rol específico
         */
        public boolean hasRole(String roleName) {
            return roles.contains(roleName);
        }

        /**
         * Verificar si tiene permiso específico
         */
        public boolean hasPermission(String permissionName) {
            return permissions.contains(permissionName);
        }

        /**
         * Verificar si es administrador
         */
        public boolean isAdmin() {
            return hasRole("ROLE_ADMIN") || hasRole("ADMIN");
        }

        /**
         * Obtener representación mascarada para logs públicos
         */
        public String getMaskedEmail() {
            if (email == null || email.length() < 3) {
                return "***@***.***";
            }
            int atIndex = email.indexOf('@');
            if (atIndex < 2) {
                return "***@***.***";
            }
            return email.charAt(0) + "***" + email.substring(atIndex);
        }
    }

    /**
     * Estadísticas de usuarios
     */
    record UserStatistics(
            long totalUsers,
            long activeUsers,
            long inactiveUsers,
            long lockedUsers,
            java.util.Map<String, Long> usersByRole,
            LocalDateTime lastUserCreated,
            double averageLoginFrequency
    ) {
    }

    /**
     * Excepciones específicas de usuarios
     */
    class UserException extends RuntimeException {
        public UserException(String message) {
            super(message);
        }

        public UserException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    class UserNotFoundException extends UserException {
        public UserNotFoundException(String identifier) {
            super("User not found: " + identifier);
        }
    }

    class UserAlreadyExistsException extends UserException {
        public UserAlreadyExistsException(String field, String value) {
            super(String.format("User with %s '%s' already exists", field, value));
        }
    }

    class InvalidPasswordException extends UserException {
        public InvalidPasswordException(String message) {
            super(message);
        }
    }

    class UserAccountLockedException extends UserException {
        public UserAccountLockedException(String username) {
            super("User account is locked: " + username);
        }
    }

    class UserAccountDisabledException extends UserException {
        public UserAccountDisabledException(String username) {
            super("User account is disabled: " + username);
        }
    }

    class InsufficientPrivilegesException extends UserException {
        public InsufficientPrivilegesException(String operation) {
            super("Insufficient privileges to perform operation: " + operation);
        }
    }

    class RoleNotFoundException extends UserException {
        public RoleNotFoundException(String roleName) {
            super("Role not found: " + roleName);
        }
    }

    class InvalidRoleAssignmentException extends UserException {
        public InvalidRoleAssignmentException(String message) {
            super(message);
        }
    }
}
