package com.contentshub.domain.model;

import com.contentshub.domain.valueobject.Email;
import com.contentshub.domain.valueobject.UserId;
import com.contentshub.domain.valueobject.Username;
import lombok.Builder;
import lombok.Value;
import lombok.With;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.security.Permission;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * Entidad de dominio User
 * Representa un usuario del sistema con sus credenciales y metadata
 */
@Value
@Builder(toBuilder = true)
@With
@Table("users")
public class User {

    @Id
    @Column("id")
    Long id;

    @Column("uuid")
    UUID uuid;

    @Column("username")
    Username username;

    @Column("email")
    Email email;

    @Column("password_hash")
    String passwordHash;

    @Column("first_name")
    String firstName;

    @Column("last_name")
    String lastName;

    @Column("profile_picture_url")
    String profilePictureUrl;

    @Column("is_enabled")
    Boolean isEnabled;

    @Column("is_account_non_expired")
    Boolean isAccountNonExpired;

    @Column("is_account_non_locked")
    Boolean isAccountNonLocked;

    @Column("is_credentials_non_expired")
    Boolean isCredentialsNonExpired;

    @Column("last_login_at")
    LocalDateTime lastLoginAt;

    @CreatedDate
    @Column("created_at")
    LocalDateTime createdAt;

    @LastModifiedDate
    @Column("updated_at")
    LocalDateTime updatedAt;

    @Column("created_by")
    String createdBy;

    @Column("updated_by")
    String updatedBy;

    // Roles - Se cargan por separado debido a la relación many-to-many
    Set<Role> roles;

    /**
     * Factory method para crear un nuevo usuario
     */
    public static User createNew(Username username, Email email, String passwordHash) {
        return User.builder()
                .uuid(UUID.randomUUID())
                .username(username)
                .email(email)
                .passwordHash(passwordHash)
                .isEnabled(true)
                .isAccountNonExpired(true)
                .isAccountNonLocked(true)
                .isCredentialsNonExpired(true)
                .createdBy("system")
                .updatedBy("system")
                .build();
    }

    /**
     * Crear UserId desde el ID de la entidad
     */
    public UserId getUserId() {
        return UserId.of(this.id);
    }

    /**
     * Obtener nombre completo
     */
    public String getFullName() {
        if (firstName == null && lastName == null) {
            return username.getValue();
        }
        return String.format("%s %s",
                firstName != null ? firstName : "",
                lastName != null ? lastName : "").trim();
    }

    /**
     * Verificar si el usuario está activo
     */
    public boolean isActive() {
        return Boolean.TRUE.equals(isEnabled) &&
                Boolean.TRUE.equals(isAccountNonExpired) &&
                Boolean.TRUE.equals(isAccountNonLocked) &&
                Boolean.TRUE.equals(isCredentialsNonExpired);
    }

    /**
     * Actualizar último login
     */
    public User updateLastLogin() {
        return this.withLastLoginAt(LocalDateTime.now())
                .withUpdatedBy("system");
    }

    /**
     * Cambiar contraseña
     */
    public User changePassword(String newPasswordHash, String changedBy) {
        return this.withPasswordHash(newPasswordHash)
                .withUpdatedBy(changedBy);
    }

    /**
     * Actualizar perfil
     */
    public User updateProfile(String firstName, String lastName, String profilePictureUrl, String updatedBy) {
        return this.withFirstName(firstName)
                .withLastName(lastName)
                .withProfilePictureUrl(profilePictureUrl)
                .withUpdatedBy(updatedBy);
    }

    /**
     * Desactivar usuario
     */
    public User deactivate(String deactivatedBy) {
        return this.withIsEnabled(false)
                .withUpdatedBy(deactivatedBy);
    }

    /**
     * Reactivar usuario
     */
    public User activate(String activatedBy) {
        return this.withIsEnabled(true)
                .withUpdatedBy(activatedBy);
    }

    /**
     * Bloquear cuenta
     */
    public User lockAccount(String lockedBy) {
        return this.withIsAccountNonLocked(false)
                .withUpdatedBy(lockedBy);
    }

    /**
     * Desbloquear cuenta
     */
    public User unlockAccount(String unlockedBy) {
        return this.withIsAccountNonLocked(true)
                .withUpdatedBy(unlockedBy);
    }

    /**
     * Verificar si el usuario tiene un rol específico
     */
    public boolean hasRole(String roleName) {
        return roles != null && roles.stream()
                .anyMatch(role -> role.getName().equals(roleName));
    }

    /**
     * Verificar si el usuario tiene alguno de los roles especificados
     */
    public boolean hasAnyRole(String... roleNames) {
        if (roles == null) return false;

        for (String roleName : roleNames) {
            if (hasRole(roleName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Obtener todos los permisos del usuario (de todos sus roles)
     */
    public Set<String> getAllPermissions() {
        if (roles == null) return Set.of();

        return roles.stream()
                .filter(Role::isActive)
                .flatMap(role -> role.getPermissions().stream())
                .map(Permission::getName)
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Verificar si el usuario tiene un permiso específico
     */
    public boolean hasPermission(String permissionName) {
        return getAllPermissions().contains(permissionName);
    }
}
