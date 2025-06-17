package com.contentshub.domain.event;

import com.contentshub.domain.valueobject.Email;
import com.contentshub.domain.valueobject.UserId;
import com.contentshub.domain.valueobject.Username;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

/**
 * Eventos relacionados con usuarios
 */
public class UserEvents {

    /**
     * Evento cuando se crea un nuevo usuario
     */
    @Value
    @Builder
    public static class UserCreated extends DomainEvent {
        UserId userId;
        Username username;
        Email email;
        String createdBy;

        @Override
        public Map<String, Object> getEventData() {
            return Map.of(
                    "userId", userId.getValue(),
                    "username", username.getValue(),
                    "email", email.getValue(),
                    "createdBy", createdBy
            );
        }

        @Override
        public String getAggregateId() {
            return userId.toString();
        }

        @Override
        public String getAggregateType() {
            return "User";
        }
    }

    /**
     * Evento cuando se actualiza el perfil de un usuario
     */
    @Value
    @Builder
    public static class UserProfileUpdated extends DomainEvent {
        UserId userId;
        String firstName;
        String lastName;
        String profilePictureUrl;
        String updatedBy;

        @Override
        public Map<String, Object> getEventData() {
            return Map.of(
                    "userId", userId.getValue(),
                    "firstName", firstName != null ? firstName : "",
                    "lastName", lastName != null ? lastName : "",
                    "profilePictureUrl", profilePictureUrl != null ? profilePictureUrl : "",
                    "updatedBy", updatedBy
            );
        }

        @Override
        public String getAggregateId() {
            return userId.toString();
        }

        @Override
        public String getAggregateType() {
            return "User";
        }
    }

    /**
     * Evento cuando un usuario hace login
     */
    @Value
    @Builder
    public static class UserLoggedIn extends DomainEvent {
        UserId userId;
        String ipAddress;
        String userAgent;
        LocalDateTime loginTime;

        @Override
        public Map<String, Object> getEventData() {
            return Map.of(
                    "userId", userId.getValue(),
                    "ipAddress", ipAddress != null ? ipAddress : "",
                    "userAgent", userAgent != null ? userAgent : "",
                    "loginTime", loginTime.toString()
            );
        }

        @Override
        public String getAggregateId() {
            return userId.toString();
        }

        @Override
        public String getAggregateType() {
            return "User";
        }
    }

    /**
     * Evento cuando se cambia la contraseña de un usuario
     */
    @Value
    @Builder
    public static class UserPasswordChanged extends DomainEvent {
        UserId userId;
        String changedBy;
        String reason;

        @Override
        public Map<String, Object> getEventData() {
            return Map.of(
                    "userId", userId.getValue(),
                    "changedBy", changedBy,
                    "reason", reason != null ? reason : "password_change"
            );
        }

        @Override
        public String getAggregateId() {
            return userId.toString();
        }

        @Override
        public String getAggregateType() {
            return "User";
        }
    }

    /**
     * Evento cuando se desactiva un usuario
     */
    @Value
    @Builder
    public static class UserDeactivated extends DomainEvent {
        UserId userId;
        String deactivatedBy;
        String reason;

        @Override
        public Map<String, Object> getEventData() {
            return Map.of(
                    "userId", userId.getValue(),
                    "deactivatedBy", deactivatedBy,
                    "reason", reason != null ? reason : "manual_deactivation"
            );
        }

        @Override
        public String getAggregateId() {
            return userId.toString();
        }

        @Override
        public String getAggregateType() {
            return "User";
        }
    }

    /**
     * Evento cuando se activa un usuario
     */
    @Value
    @Builder
    public static class UserActivated extends DomainEvent {
        UserId userId;
        String activatedBy;
        String reason;

        @Override
        public Map<String, Object> getEventData() {
            return Map.of(
                    "userId", userId.getValue(),
                    "activatedBy", activatedBy,
                    "reason", reason != null ? reason : "manual_activation"
            );
        }

        @Override
        public String getAggregateId() {
            return userId.toString();
        }

        @Override
        public String getAggregateType() {
            return "User";
        }
    }

    /**
     * Evento cuando se asignan roles a un usuario
     */
    @Value
    @Builder
    public static class UserRolesAssigned extends DomainEvent {
        UserId userId;
        Set<String> roleNames;
        String assignedBy;

        @Override
        public Map<String, Object> getEventData() {
            return Map.of(
                    "userId", userId.getValue(),
                    "roleNames", roleNames,
                    "assignedBy", assignedBy
            );
        }

        @Override
        public String getAggregateId() {
            return userId.toString();
        }

        @Override
        public String getAggregateType() {
            return "User";
        }
    }

    /**
     * Evento cuando se bloquea la cuenta de un usuario
     */
    @Value
    @Builder
    public static class UserAccountLocked extends DomainEvent {
        UserId userId;
        String lockedBy;
        String reason;
        LocalDateTime lockTime;

        @Override
        public Map<String, Object> getEventData() {
            return Map.of(
                    "userId", userId.getValue(),
                    "lockedBy", lockedBy,
                    "reason", reason,
                    "lockTime", lockTime.toString()
            );
        }

        @Override
        public String getAggregateId() {
            return userId.toString();
        }

        @Override
        public String getAggregateType() {
            return "User";
        }
    }
}
