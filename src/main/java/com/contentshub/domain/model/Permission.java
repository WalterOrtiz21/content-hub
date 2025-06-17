package com.contentshub.domain.model;

import lombok.Builder;
import lombok.Value;
import lombok.With;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * Entidad de dominio Permission
 * Representa un permiso específico en el sistema
 */
@Value
@Builder(toBuilder = true)
@With
@Table("permissions")
public class Permission {

    @Id
    @Column("id")
    Long id;

    @Column("name")
    String name;

    @Column("description")
    String description;

    @Column("resource")
    String resource;

    @Column("action")
    String action;

    @CreatedDate
    @Column("created_at")
    LocalDateTime createdAt;

    /**
     * Factory method para crear un nuevo permiso
     */
    public static Permission createNew(String name, String description, String resource, String action) {
        return Permission.builder()
                .name(name)
                .description(description)
                .resource(resource)
                .action(action)
                .build();
    }

    /**
     * Obtener el identificador completo del permiso
     */
    public String getFullPermission() {
        return String.format("%s:%s", resource, action);
    }

    /**
     * Verificar si el permiso es para un recurso específico
     */
    public boolean isForResource(String resourceName) {
        return resource.equalsIgnoreCase(resourceName);
    }

    /**
     * Verificar si el permiso es para una acción específica
     */
    public boolean isForAction(String actionName) {
        return action.equalsIgnoreCase(actionName);
    }

    /**
     * Verificar si es un permiso de lectura
     */
    public boolean isReadPermission() {
        return "READ".equalsIgnoreCase(action) || "VIEW".equalsIgnoreCase(action);
    }

    /**
     * Verificar si es un permiso de escritura
     */
    public boolean isWritePermission() {
        return "WRITE".equalsIgnoreCase(action) ||
                "CREATE".equalsIgnoreCase(action) ||
                "UPDATE".equalsIgnoreCase(action);
    }

    /**
     * Verificar si es un permiso de eliminación
     */
    public boolean isDeletePermission() {
        return "DELETE".equalsIgnoreCase(action) || "REMOVE".equalsIgnoreCase(action);
    }

    /**
     * Verificar si es un permiso de administración
     */
    public boolean isAdminPermission() {
        return "ADMIN".equalsIgnoreCase(action) ||
                "MANAGE".equalsIgnoreCase(action) ||
                name.contains("ADMIN");
    }

    /**
     * Verificar si es un permiso del sistema
     */
    public boolean isSystemPermission() {
        return "SYSTEM".equalsIgnoreCase(resource) || name.startsWith("SYSTEM_");
    }

    /**
     * Verificar si es un permiso de documento
     */
    public boolean isDocumentPermission() {
        return "DOCUMENT".equalsIgnoreCase(resource) || name.startsWith("DOCUMENT_");
    }

    /**
     * Verificar si es un permiso de usuario
     */
    public boolean isUserPermission() {
        return "USER".equalsIgnoreCase(resource) || name.startsWith("USER_");
    }
}
