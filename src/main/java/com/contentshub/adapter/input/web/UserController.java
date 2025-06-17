package com.contentshub.adapter.input.web;

import com.contentshub.application.port.input.UserManagementUseCase;
import com.contentshub.domain.valueobject.Email;
import com.contentshub.domain.valueobject.UserId;
import com.contentshub.domain.valueobject.Username;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Set;

/**
 * Controlador REST para gestión de usuarios
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "User Management", description = "Endpoints para gestión de usuarios")
@SecurityRequirement(name = "Bearer Authentication")
public class UserController {

    private final UserManagementUseCase userManagementUseCase;

    @PostMapping
    @Operation(
            summary = "Crear nuevo usuario",
            description = "Crea un nuevo usuario en el sistema",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Usuario creado exitosamente"),
                    @ApiResponse(responseCode = "409", description = "Usuario ya existe"),
                    @ApiResponse(responseCode = "400", description = "Datos inválidos")
            }
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('USER_WRITE')")
    public Mono<UserResponse> createUser(
            @Valid @RequestBody CreateUserRequest request,
            @AuthenticationPrincipal Object principal) {

        log.debug("Creating user: {}", request.username());

        String createdBy = extractUsernameFromPrincipal(principal);

        UserManagementUseCase.CreateUserCommand command =
                new UserManagementUseCase.CreateUserCommand(
                        request.username(),
                        request.email(),
                        request.password(),
                        request.firstName(),
                        request.lastName(),
                        createdBy
                );

        return userManagementUseCase.createUser(command)
                .map(this::toUserResponse)
                .doOnSuccess(response -> log.info("User created: {}", response.username()))
                .doOnError(error -> log.error("Error creating user {}: {}", request.username(), error.getMessage()));
    }

    @GetMapping("/{userId}")
    @Operation(
            summary = "Obtener usuario por ID",
            description = "Retorna la información de un usuario específico",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Usuario encontrado"),
                    @ApiResponse(responseCode = "404", description = "Usuario no encontrado"),
                    @ApiResponse(responseCode = "403", description = "Sin permisos para ver este usuario")
            }
    )
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('USER_READ') or @userSecurityService.canAccessUser(authentication, #userId)")
    public Mono<UserResponse> getUserById(
            @Parameter(description = "ID del usuario") @PathVariable Long userId) {

        log.debug("Getting user by ID: {}", userId);

        return userManagementUseCase.getUserById(UserId.of(userId))
                .map(this::toUserResponse)
                .doOnSuccess(response -> log.debug("User retrieved: {}", response.username()))
                .doOnError(error -> log.error("Error getting user {}: {}", userId, error.getMessage()));
    }

    @GetMapping("/username/{username}")
    @Operation(
            summary = "Obtener usuario por username",
            description = "Retorna la información de un usuario por su username",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Usuario encontrado"),
                    @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
            }
    )
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('USER_READ')")
    public Mono<UserResponse> getUserByUsername(
            @Parameter(description = "Username del usuario") @PathVariable String username) {

        log.debug("Getting user by username: {}", username);

        return userManagementUseCase.getUserByUsername(Username.of(username))
                .map(this::toUserResponse)
                .doOnSuccess(response -> log.debug("User retrieved: {}", response.username()))
                .doOnError(error -> log.error("Error getting user by username {}: {}", username, error.getMessage()));
    }

    @GetMapping("/email/{email}")
    @Operation(
            summary = "Obtener usuario por email",
            description = "Retorna la información de un usuario por su email",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Usuario encontrado"),
                    @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
            }
    )
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('USER_READ')")
    public Mono<UserResponse> getUserByEmail(
            @Parameter(description = "Email del usuario") @PathVariable String email) {

        log.debug("Getting user by email: {}", email);

        return userManagementUseCase.getUserByEmail(Email.of(email))
                .map(this::toUserResponse)
                .doOnSuccess(response -> log.debug("User retrieved: {}", response.username()))
                .doOnError(error -> log.error("Error getting user by email {}: {}", email, error.getMessage()));
    }

    @GetMapping
    @Operation(
            summary = "Listar usuarios",
            description = "Retorna una lista paginada de usuarios",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Lista de usuarios"),
                    @ApiResponse(responseCode = "403", description = "Sin permisos para listar usuarios")
            }
    )
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('USER_READ')")
    public Flux<UserResponse> getAllUsers(
            @Parameter(description = "Número de página (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamaño de página") @RequestParam(defaultValue = "20") int size) {

        log.debug("Getting all users with pagination: page={}, size={}", page, size);

        return userManagementUseCase.getAllUsers(page, size)
                .map(this::toUserResponse)
                .doOnNext(user -> log.debug("User in list: {}", user.username()))
                .doOnError(error -> log.error("Error getting users list: {}", error.getMessage()));
    }

    @GetMapping("/search")
    @Operation(
            summary = "Buscar usuarios",
            description = "Busca usuarios por diferentes criterios",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Resultados de búsqueda"),
                    @ApiResponse(responseCode = "403", description = "Sin permisos para buscar usuarios")
            }
    )
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('USER_READ')")
    public Flux<UserResponse> searchUsers(
            @Parameter(description = "Patrón de búsqueda en username") @RequestParam(required = false) String username,
            @Parameter(description = "Patrón de búsqueda en email") @RequestParam(required = false) String email,
            @Parameter(description = "Patrón de búsqueda en nombre") @RequestParam(required = false) String name,
            @Parameter(description = "Filtrar por estado activo") @RequestParam(required = false) Boolean active,
            @Parameter(description = "Filtrar por roles") @RequestParam(required = false) Set<String> roles,
            @Parameter(description = "Número de página") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamaño de página") @RequestParam(defaultValue = "20") int size) {

        log.debug("Searching users with criteria");

        UserManagementUseCase.UserSearchCriteria criteria =
                new UserManagementUseCase.UserSearchCriteria(
                        username, email, name, active, roles, page, size
                );

        return userManagementUseCase.searchUsers(criteria)
                .map(this::toUserResponse)
                .doOnNext(user -> log.debug("User matches search: {}", user.username()))
                .doOnError(error -> log.error("Error searching users: {}", error.getMessage()));
    }

    @PutMapping("/{userId}/profile")
    @Operation(
            summary = "Actualizar perfil de usuario",
            description = "Actualiza la información del perfil del usuario",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Perfil actualizado exitosamente"),
                    @ApiResponse(responseCode = "404", description = "Usuario no encontrado"),
                    @ApiResponse(responseCode = "403", description = "Sin permisos para actualizar este perfil")
            }
    )
    @PreAuthorize("hasRole('ADMIN') or @userSecurityService.canModifyUser(authentication, #userId)")
    public Mono<UserResponse> updateUserProfile(
            @Parameter(description = "ID del usuario") @PathVariable Long userId,
            @Valid @RequestBody UpdateProfileRequest request,
            @AuthenticationPrincipal Object principal) {

        log.debug("Updating profile for user: {}", userId);

        String updatedBy = extractUsernameFromPrincipal(principal);

        UserManagementUseCase.UpdateUserProfileCommand command =
                new UserManagementUseCase.UpdateUserProfileCommand(
                        UserId.of(userId),
                        request.firstName(),
                        request.lastName(),
                        request.profilePictureUrl(),
                        updatedBy
                );

        return userManagementUseCase.updateUserProfile(command)
                .map(this::toUserResponse)
                .doOnSuccess(response -> log.info("Profile updated for user: {}", response.username()))
                .doOnError(error -> log.error("Error updating profile for user {}: {}", userId, error.getMessage()));
    }

    @PutMapping("/{userId}/password")
    @Operation(
            summary = "Cambiar contraseña",
            description = "Cambia la contraseña del usuario",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Contraseña cambiada exitosamente"),
                    @ApiResponse(responseCode = "400", description = "Contraseña actual incorrecta"),
                    @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
            }
    )
    @PreAuthorize("hasRole('ADMIN') or @userSecurityService.canModifyUser(authentication, #userId)")
    public Mono<MessageResponse> changePassword(
            @Parameter(description = "ID del usuario") @PathVariable Long userId,
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal Object principal) {

        log.debug("Changing password for user: {}", userId);

        String changedBy = extractUsernameFromPrincipal(principal);

        UserManagementUseCase.ChangePasswordCommand command =
                new UserManagementUseCase.ChangePasswordCommand(
                        UserId.of(userId),
                        request.currentPassword(),
                        request.newPassword(),
                        changedBy
                );

        return userManagementUseCase.changePassword(command)
                .then(Mono.just(new MessageResponse("Contraseña cambiada exitosamente")))
                .doOnSuccess(response -> log.info("Password changed for user: {}", userId))
                .doOnError(error -> log.error("Error changing password for user {}: {}", userId, error.getMessage()));
    }

    @PutMapping("/{userId}/activate")
    @Operation(
            summary = "Activar usuario",
            description = "Activa un usuario desactivado",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Usuario activado exitosamente"),
                    @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
            }
    )
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('USER_WRITE')")
    public Mono<UserResponse> activateUser(
            @Parameter(description = "ID del usuario") @PathVariable Long userId,
            @AuthenticationPrincipal Object principal) {

        log.debug("Activating user: {}", userId);

        String activatedBy = extractUsernameFromPrincipal(principal);

        return userManagementUseCase.activateUser(UserId.of(userId), activatedBy)
                .map(this::toUserResponse)
                .doOnSuccess(response -> log.info("User activated: {}", response.username()))
                .doOnError(error -> log.error("Error activating user {}: {}", userId, error.getMessage()));
    }

    @PutMapping("/{userId}/deactivate")
    @Operation(
            summary = "Desactivar usuario",
            description = "Desactiva un usuario",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Usuario desactivado exitosamente"),
                    @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
            }
    )
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('USER_WRITE')")
    public Mono<UserResponse> deactivateUser(
            @Parameter(description = "ID del usuario") @PathVariable Long userId,
            @Valid @RequestBody DeactivateUserRequest request,
            @AuthenticationPrincipal Object principal) {

        log.debug("Deactivating user: {}", userId);

        String deactivatedBy = extractUsernameFromPrincipal(principal);

        return userManagementUseCase.deactivateUser(UserId.of(userId), deactivatedBy, request.reason())
                .map(this::toUserResponse)
                .doOnSuccess(response -> log.info("User deactivated: {}", response.username()))
                .doOnError(error -> log.error("Error deactivating user {}: {}", userId, error.getMessage()));
    }

    @PutMapping("/{userId}/roles")
    @Operation(
            summary = "Asignar roles a usuario",
            description = "Asigna roles específicos a un usuario",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Roles asignados exitosamente"),
                    @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
            }
    )
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<UserResponse> assignRoles(
            @Parameter(description = "ID del usuario") @PathVariable Long userId,
            @Valid @RequestBody AssignRolesRequest request,
            @AuthenticationPrincipal Object principal) {

        log.debug("Assigning roles {} to user: {}", request.roleNames(), userId);

        String assignedBy = extractUsernameFromPrincipal(principal);

        return userManagementUseCase.assignRoles(UserId.of(userId), request.roleNames(), assignedBy)
                .map(this::toUserResponse)
                .doOnSuccess(response -> log.info("Roles assigned to user: {}", response.username()))
                .doOnError(error -> log.error("Error assigning roles to user {}: {}", userId, error.getMessage()));
    }

    @DeleteMapping("/{userId}/roles")
    @Operation(
            summary = "Remover roles de usuario",
            description = "Remueve roles específicos de un usuario",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Roles removidos exitosamente"),
                    @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
            }
    )
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<UserResponse> removeRoles(
            @Parameter(description = "ID del usuario") @PathVariable Long userId,
            @Valid @RequestBody RemoveRolesRequest request,
            @AuthenticationPrincipal Object principal) {

        log.debug("Removing roles {} from user: {}", request.roleNames(), userId);

        String removedBy = extractUsernameFromPrincipal(principal);

        return userManagementUseCase.removeRoles(UserId.of(userId), request.roleNames(), removedBy)
                .map(this::toUserResponse)
                .doOnSuccess(response -> log.info("Roles removed from user: {}", response.username()))
                .doOnError(error -> log.error("Error removing roles from user {}: {}", userId, error.getMessage()));
    }

    @DeleteMapping("/{userId}")
    @Operation(
            summary = "Eliminar usuario",
            description = "Elimina (desactiva) un usuario del sistema",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Usuario eliminado exitosamente"),
                    @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
            }
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<Void> deleteUser(
            @Parameter(description = "ID del usuario") @PathVariable Long userId,
            @AuthenticationPrincipal Object principal) {

        log.debug("Deleting user: {}", userId);

        String deletedBy = extractUsernameFromPrincipal(principal);

        return userManagementUseCase.deleteUser(UserId.of(userId), deletedBy)
                .doOnSuccess(unused -> log.info("User deleted: {}", userId))
                .doOnError(error -> log.error("Error deleting user {}: {}", userId, error.getMessage()));
    }

    @GetMapping("/statistics")
    @Operation(
            summary = "Obtener estadísticas de usuarios",
            description = "Retorna estadísticas generales de usuarios",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Estadísticas de usuarios")
            }
    )
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('USER_READ')")
    public Mono<UserStatisticsResponse> getUserStatistics() {
        log.debug("Getting user statistics");

        return userManagementUseCase.getUserStatistics()
                .map(this::toUserStatisticsResponse)
                .doOnSuccess(stats -> log.debug("User statistics retrieved: {} total users", stats.totalUsers()))
                .doOnError(error -> log.error("Error getting user statistics: {}", error.getMessage()));
    }

    /**
     * Métodos auxiliares
     */
    private String extractUsernameFromPrincipal(Object principal) {
        if (principal instanceof com.contentshub.infrastructure.security.JwtAuthenticationConverter.JwtUserPrincipal jwtPrincipal) {
            return jwtPrincipal.getUsername();
        }

        if (principal instanceof org.springframework.security.core.userdetails.UserDetails userDetails) {
            return userDetails.getUsername();
        }

        // Fallback para casos de testing o desarrollo
        log.warn("Extracting username from principal of type: {}", principal.getClass());
        return "system";
    }

    /**
     * Mappers
     */
    private UserResponse toUserResponse(UserManagementUseCase.UserResponse response) {
        return new UserResponse(
                response.userId().getValue(),
                response.uuid(),
                response.username(),
                response.email(),
                response.firstName(),
                response.lastName(),
                response.profilePictureUrl(),
                response.isEnabled(),
                response.isAccountNonExpired(),
                response.isAccountNonLocked(),
                response.isCredentialsNonExpired(),
                response.isActive(),
                response.lastLoginAt(),
                response.createdAt(),
                response.updatedAt(),
                response.roles(),
                response.permissions()
        );
    }

    private UserStatisticsResponse toUserStatisticsResponse(UserManagementUseCase.UserStatistics stats) {
        return new UserStatisticsResponse(
                stats.totalUsers(),
                stats.activeUsers(),
                stats.inactiveUsers(),
                stats.lockedUsers(),
                stats.usersByRole(),
                stats.lastUserCreated(),
                stats.averageLoginFrequency()
        );
    }

    /**
     * DTOs de Request
     */
    public record CreateUserRequest(
            @NotBlank(message = "Username es requerido")
            @Size(min = 3, max = 50, message = "Username debe tener entre 3 y 50 caracteres")
            String username,

            @NotBlank(message = "Email es requerido")
            @jakarta.validation.constraints.Email(message = "Email debe ser válido")
            String email,

            @NotBlank(message = "Password es requerido")
            @Size(min = 6, max = 100, message = "Password debe tener entre 6 y 100 caracteres")
            String password,

            String firstName,
            String lastName
    ) {}

    public record UpdateProfileRequest(
            String firstName,
            String lastName,
            String profilePictureUrl
    ) {}

    public record ChangePasswordRequest(
            @NotBlank(message = "Contraseña actual es requerida")
            String currentPassword,

            @NotBlank(message = "Nueva contraseña es requerida")
            @Size(min = 6, max = 100, message = "Password debe tener entre 6 y 100 caracteres")
            String newPassword
    ) {}

    public record DeactivateUserRequest(
            String reason
    ) {}

    public record AssignRolesRequest(
            @NotNull(message = "Roles son requeridos")
            @Size(min = 1, message = "Debe especificar al menos un rol")
            Set<String> roleNames
    ) {}

    public record RemoveRolesRequest(
            @NotNull(message = "Roles son requeridos")
            @Size(min = 1, message = "Debe especificar al menos un rol")
            Set<String> roleNames
    ) {}

    /**
     * DTOs de Response
     */
    public record UserResponse(
            Long userId,
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
            java.time.LocalDateTime lastLoginAt,
            java.time.LocalDateTime createdAt,
            java.time.LocalDateTime updatedAt,
            Set<String> roles,
            Set<String> permissions
    ) {}

    public record UserStatisticsResponse(
            long totalUsers,
            long activeUsers,
            long inactiveUsers,
            long lockedUsers,
            java.util.Map<String, Long> usersByRole,
            java.time.LocalDateTime lastUserCreated,
            double averageLoginFrequency
    ) {}

    public record MessageResponse(String message) {}
}
