package com.contentshub.adapter.output.persistence.r2dbc;

import com.contentshub.domain.model.Permission;
import com.contentshub.domain.model.Role;
import com.contentshub.domain.model.User;
import com.contentshub.domain.valueobject.Email;
import com.contentshub.domain.valueobject.Username;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Mapper para convertir entre filas de base de datos y entidades de dominio
 */
@Component
public class UserMapper {

    /**
     * Mapear fila de BD a User
     */
    public User mapToUser(Row row, RowMetadata metadata) {
        return User.builder()
                .id(row.get("id", Long.class))
                .uuid(row.get("uuid", UUID.class))
                .username(Username.of(row.get("username", String.class)))
                .email(Email.of(row.get("email", String.class)))
                .passwordHash(row.get("password_hash", String.class))
                .firstName(row.get("first_name", String.class))
                .lastName(row.get("last_name", String.class))
                .profilePictureUrl(row.get("profile_picture_url", String.class))
                .isEnabled(row.get("is_enabled", Boolean.class))
                .isAccountNonExpired(row.get("is_account_non_expired", Boolean.class))
                .isAccountNonLocked(row.get("is_account_non_locked", Boolean.class))
                .isCredentialsNonExpired(row.get("is_credentials_non_expired", Boolean.class))
                .lastLoginAt(row.get("last_login_at", LocalDateTime.class))
                .createdAt(row.get("created_at", LocalDateTime.class))
                .updatedAt(row.get("updated_at", LocalDateTime.class))
                .createdBy(row.get("created_by", String.class))
                .updatedBy(row.get("updated_by", String.class))
                .build();
    }

    /**
     * Mapear fila de BD a Role
     */
    public Role mapToRole(Row row, RowMetadata metadata) {
        return Role.builder()
                .id(row.get("id", Long.class))
                .name(row.get("name", String.class))
                .description(row.get("description", String.class))
                .isActive(row.get("is_active", Boolean.class))
                .createdAt(row.get("created_at", LocalDateTime.class))
                .updatedAt(row.get("updated_at", LocalDateTime.class))
                .build();
    }

    /**
     * Mapear fila de BD a Permission
     */
    public Permission mapToPermission(Row row, RowMetadata metadata) {
        return Permission.builder()
                .id(row.get("id", Long.class))
                .name(row.get("name", String.class))
                .description(row.get("description", String.class))
                .resource(row.get("resource", String.class))
                .action(row.get("action", String.class))
                .createdAt(row.get("created_at", LocalDateTime.class))
                .build();
    }
}
