package com.contentshub.domain.exception;

import com.contentshub.domain.valueobject.UserId;

/**
 * Excepciones específicas del dominio
 */
public class DomainExceptions {

    /**
     * Excepción base para todas las excepciones de dominio
     */
    public static abstract class DomainException extends RuntimeException {
        protected DomainException(String message) {
            super(message);
        }

        protected DomainException(String message, Throwable cause) {
            super(message, cause);
        }

        /**
         * Código de error para identificación
         */
        public abstract String getErrorCode();

        /**
         * Datos adicionales del error
         */
        public java.util.Map<String, Object> getErrorData() {
            return java.util.Map.of();
        }
    }

    /**
     * Excepción cuando no se encuentra una entidad
     */
    public static class EntityNotFoundException extends DomainException {
        private final String entityType;
        private final String entityId;

        public EntityNotFoundException(String entityType, String entityId) {
            super(String.format("%s with id '%s' not found", entityType, entityId));
            this.entityType = entityType;
            this.entityId = entityId;
        }

        @Override
        public String getErrorCode() {
            return "ENTITY_NOT_FOUND";
        }

        @Override
        public java.util.Map<String, Object> getErrorData() {
            return java.util.Map.of(
                    "entityType", entityType,
                    "entityId", entityId
            );
        }

        public String getEntityType() {
            return entityType;
        }

        public String getEntityId() {
            return entityId;
        }
    }

    /**
     * Excepción cuando una entidad ya existe
     */
    public static class EntityAlreadyExistsException extends DomainException {
        private final String entityType;
        private final String conflictField;
        private final String conflictValue;

        public EntityAlreadyExistsException(String entityType, String conflictField, String conflictValue) {
            super(String.format("%s with %s '%s' already exists", entityType, conflictField, conflictValue));
            this.entityType = entityType;
            this.conflictField = conflictField;
            this.conflictValue = conflictValue;
        }

        @Override
        public String getErrorCode() {
            return "ENTITY_ALREADY_EXISTS";
        }

        @Override
        public java.util.Map<String, Object> getErrorData() {
            return java.util.Map.of(
                    "entityType", entityType,
                    "conflictField", conflictField,
                    "conflictValue", conflictValue
            );
        }
    }

    /**
     * Excepción cuando se viola una regla de negocio
     */
    public static class BusinessRuleViolationException extends DomainException {
        private final String ruleCode;
        private final java.util.Map<String, Object> context;

        public BusinessRuleViolationException(String ruleCode, String message) {
            super(message);
            this.ruleCode = ruleCode;
            this.context = java.util.Map.of();
        }

        public BusinessRuleViolationException(String ruleCode, String message, java.util.Map<String, Object> context) {
            super(message);
            this.ruleCode = ruleCode;
            this.context = context != null ? context : java.util.Map.of();
        }

        @Override
        public String getErrorCode() {
            return "BUSINESS_RULE_VIOLATION";
        }

        @Override
        public java.util.Map<String, Object> getErrorData() {
            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("ruleCode", ruleCode);
            data.putAll(context);
            return data;
        }

        public String getRuleCode() {
            return ruleCode;
        }
    }

    /**
     * Excepción cuando no se tienen permisos suficientes
     */
    public static class InsufficientPermissionsException extends DomainException {
        private final UserId userId;
        private final String requiredPermission;
        private final String resource;

        public InsufficientPermissionsException(UserId userId, String requiredPermission, String resource) {
            super(String.format("User %s does not have permission '%s' for resource '%s'",
                    userId, requiredPermission, resource));
            this.userId = userId;
            this.requiredPermission = requiredPermission;
            this.resource = resource;
        }

        @Override
        public String getErrorCode() {
            return "INSUFFICIENT_PERMISSIONS";
        }

        @Override
        public java.util.Map<String, Object> getErrorData() {
            return java.util.Map.of(
                    "userId", userId.getValue(),
                    "requiredPermission", requiredPermission,
                    "resource", resource
            );
        }
    }

    /**
     * Excepción cuando se intenta una transición de estado inválida
     */
    public static class InvalidStateTransitionException extends DomainException {
        private final String currentState;
        private final String targetState;
        private final String entityType;

        public InvalidStateTransitionException(String entityType, String currentState, String targetState) {
            super(String.format("Cannot transition %s from state '%s' to '%s'",
                    entityType, currentState, targetState));
            this.entityType = entityType;
            this.currentState = currentState;
            this.targetState = targetState;
        }

        @Override
        public String getErrorCode() {
            return "INVALID_STATE_TRANSITION";
        }

        @Override
        public java.util.Map<String, Object> getErrorData() {
            return java.util.Map.of(
                    "entityType", entityType,
                    "currentState", currentState,
                    "targetState", targetState
            );
        }
    }

    /**
     * Excepción cuando se excede un límite
     */
    public static class LimitExceededException extends DomainException {
        private final String limitType;
        private final long currentValue;
        private final long maxValue;

        public LimitExceededException(String limitType, long currentValue, long maxValue) {
            super(String.format("%s limit exceeded: %d > %d", limitType, currentValue, maxValue));
            this.limitType = limitType;
            this.currentValue = currentValue;
            this.maxValue = maxValue;
        }

        @Override
        public String getErrorCode() {
            return "LIMIT_EXCEEDED";
        }

        @Override
        public java.util.Map<String, Object> getErrorData() {
            return java.util.Map.of(
                    "limitType", limitType,
                    "currentValue", currentValue,
                    "maxValue", maxValue
            );
        }
    }

    /**
     * Excepción para operaciones de contenido inválidas
     */
    public static class InvalidContentOperationException extends DomainException {
        private final String operation;
        private final String contentType;
        private final String reason;

        public InvalidContentOperationException(String operation, String contentType, String reason) {
            super(String.format("Cannot perform operation '%s' on content type '%s': %s",
                    operation, contentType, reason));
            this.operation = operation;
            this.contentType = contentType;
            this.reason = reason;
        }

        @Override
        public String getErrorCode() {
            return "INVALID_CONTENT_OPERATION";
        }

        @Override
        public java.util.Map<String, Object> getErrorData() {
            return java.util.Map.of(
                    "operation", operation,
                    "contentType", contentType,
                    "reason", reason
            );
        }
    }

    /**
     * Excepción cuando se intenta una colaboración inválida
     */
    public static class InvalidCollaborationException extends DomainException {
        private final String documentId;
        private final UserId userId;
        private final String reason;

        public InvalidCollaborationException(String documentId, UserId userId, String reason) {
            super(String.format("Invalid collaboration for document '%s' and user '%s': %s",
                    documentId, userId, reason));
            this.documentId = documentId;
            this.userId = userId;
            this.reason = reason;
        }

        @Override
        public String getErrorCode() {
            return "INVALID_COLLABORATION";
        }

        @Override
        public java.util.Map<String, Object> getErrorData() {
            return java.util.Map.of(
                    "documentId", documentId,
                    "userId", userId.getValue(),
                    "reason", reason
            );
        }
    }

    /**
     * Excepción cuando una cuenta de usuario está inactiva
     */
    public static class InactiveUserAccountException extends DomainException {
        private final UserId userId;
        private final String reason;

        public InactiveUserAccountException(UserId userId, String reason) {
            super(String.format("User account '%s' is inactive: %s", userId, reason));
            this.userId = userId;
            this.reason = reason;
        }

        @Override
        public String getErrorCode() {
            return "INACTIVE_USER_ACCOUNT";
        }

        @Override
        public java.util.Map<String, Object> getErrorData() {
            return java.util.Map.of(
                    "userId", userId.getValue(),
                    "reason", reason
            );
        }
    }

    /**
     * Excepción cuando se intenta una operación en un documento archivado
     */
    public static class ArchivedDocumentException extends DomainException {
        private final String documentId;
        private final String operation;

        public ArchivedDocumentException(String documentId, String operation) {
            super(String.format("Cannot perform operation '%s' on archived document '%s'",
                    operation, documentId));
            this.documentId = documentId;
            this.operation = operation;
        }

        @Override
        public String getErrorCode() {
            return "ARCHIVED_DOCUMENT_OPERATION";
        }

        @Override
        public java.util.Map<String, Object> getErrorData() {
            return java.util.Map.of(
                    "documentId", documentId,
                    "operation", operation
            );
        }
    }

    /**
     * Excepción cuando se detecta un conflicto de concurrencia
     */
    public static class ConcurrencyConflictException extends DomainException {
        private final String entityType;
        private final String entityId;
        private final Integer expectedVersion;
        private final Integer actualVersion;

        public ConcurrencyConflictException(String entityType, String entityId,
                                            Integer expectedVersion, Integer actualVersion) {
            super(String.format("Concurrency conflict for %s '%s': expected version %d, found %d",
                    entityType, entityId, expectedVersion, actualVersion));
            this.entityType = entityType;
            this.entityId = entityId;
            this.expectedVersion = expectedVersion;
            this.actualVersion = actualVersion;
        }

        @Override
        public String getErrorCode() {
            return "CONCURRENCY_CONFLICT";
        }

        @Override
        public java.util.Map<String, Object> getErrorData() {
            return java.util.Map.of(
                    "entityType", entityType,
                    "entityId", entityId,
                    "expectedVersion", expectedVersion,
                    "actualVersion", actualVersion
            );
        }
    }
}
