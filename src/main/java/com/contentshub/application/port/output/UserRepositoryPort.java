package com.contentshub.application.port.output;

import com.contentshub.domain.model.User;
import com.contentshub.domain.valueobject.Email;
import com.contentshub.domain.valueobject.UserId;
import com.contentshub.domain.valueobject.Username;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Puerto de salida para repositorio de usuarios
 * Define el contrato para persistencia de usuarios (PostgreSQL)
 */
public interface UserRepositoryPort {

    /**
     * Guardar un usuario
     */
    Mono<User> save(User user);

    /**
     * Buscar usuario por ID
     */
    Mono<User> findById(UserId userId);

    /**
     * Buscar usuario por username
     */
    Mono<User> findByUsername(Username username);

    /**
     * Buscar usuario por email
     */
    Mono<User> findByEmail(Email email);

    /**
     * Buscar usuario por username o email
     */
    Mono<User> findByUsernameOrEmail(String usernameOrEmail);

    /**
     * Verificar si existe un username
     */
    Mono<Boolean> existsByUsername(Username username);

    /**
     * Verificar si existe un email
     */
    Mono<Boolean> existsByEmail(Email email);

    /**
     * Eliminar usuario por ID
     */
    Mono<Void> deleteById(UserId userId);

    /**
     * Buscar todos los usuarios activos
     */
    Flux<User> findAllActive();

    /**
     * Buscar usuarios por rol
     */
    Flux<User> findByRole(String roleName);

    /**
     * Buscar usuarios creados después de una fecha
     */
    Flux<User> findByCreatedAtAfter(LocalDateTime date);

    /**
     * Buscar usuarios por patrón en nombre o email
     */
    Flux<User> findByNameOrEmailPattern(String pattern);

    /**
     * Contar usuarios activos
     */
    Mono<Long> countActiveUsers();

    /**
     * Contar usuarios por rol
     */
    Mono<Long> countByRole(String roleName);

    /**
     * Actualizar último login
     */
    Mono<User> updateLastLogin(UserId userId, LocalDateTime loginTime);

    /**
     * Buscar usuarios con paginación
     */
    Flux<User> findAllWithPagination(int page, int size);

    /**
     * Buscar usuarios inactivos (para limpieza)
     */
    Flux<User> findInactiveUsers(LocalDateTime cutoffDate);

    /**
     * Actualizar perfil de usuario
     */
    Mono<User> updateProfile(UserId userId, String firstName, String lastName, String profilePictureUrl);

    /**
     * Cambiar estado de activación
     */
    Mono<User> updateActivationStatus(UserId userId, boolean isActive);

    /**
     * Verificar si el usuario tiene permisos específicos
     */
    Mono<Boolean> hasPermission(UserId userId, String permission);

    /**
     * Obtener todos los permisos de un usuario
     */
    Flux<String> getUserPermissions(UserId userId);
}
