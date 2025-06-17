package com.contentshub.domain.model;

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

/**
 * Entidad de dominio Role
 * Representa un rol del sistema con sus permisos asociados
 */
@Value
@Builder(toBuilder = true)
@With
@Table("roles")
public class Role {

    @Id
    @Column("id")
    Long id;

    @Column("name")
    String name;

    @Column("description")
    String description;

    @Column("is_active")
    Boolean isActive;

    @CreatedDate
    @Column("created_at")
    LocalDateTime createdAt;

    @LastModifiedDate
    @Column("updated_at")
    LocalDateTime updatedAt;

    // Permisos - Se cargan por separado debido a la relación many-to-many
    Set<Permission> permissions;

    /**
     * Factory method para crear un nuevo rol
     */
    public static Role createNew(String name, String description) {
        return Role.builder()
                .name(name)
                .description(description)
                .isActive(true)
                .build();
    }

    /**
     * Verificar si el rol está activo
     */
    public boolean isActive() {
        return Boolean.TRUE.equals(isActive);
    }

    /**
     * Activar rol
     */
    public Role activate() {
        return this.withIsActive(true);
    }

    /**
     * Desactivar rol
     */
    public Role deactivate() {
        return this.withIsActive(false);
    }

    /**
     * Actualizar descripción
     */
    public Role updateDescription(String newDescription) {
        return this.withDescription(newDescription);
    }

    /**
     * Verificar si el rol tiene un permiso específico
     */
    public boolean hasPermission(String permissionName) {
        return permissions != null && permissions.stream()
                .anyMatch(permission -> permission.getName().equals(permissionName));
    }

    /**
     * Obtener nombres de todos los permisos
     */
    public Set<String> getPermissionNames() {
        if (permissions == null) return Set.of();

        return permissions.stream()
                .map(Permission::getName)
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Verificar si es un rol de administrador
     */
    public boolean isAdminRole() {
        return "ROLE_ADMIN".equals(name) ||
                "ADMIN".equals(name) ||
                hasPermission("SYSTEM_ADMIN");
    }

    /**
     * Verificar si es un rol de usuario estándar
     */
    public boolean isUserRole() {
        return "ROLE_USER".equals(name) || "USER".equals(name);
    }

    /**
     * Verificar si es un rol de moderador
     */
    public boolean isModeratorRole() {
        return "ROLE_MODERATOR".equals(name) || "MODERATOR".equals(name);
    }
}
