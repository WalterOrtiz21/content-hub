package com.contentshub.application.port.input;

/**
 * Excepciones faltantes para DocumentManagementUseCase
 */
public class DocumentManagementExceptions {

    public static class DocumentNotFoundException extends RuntimeException {
        public DocumentNotFoundException(String documentId) {
            super("Document not found: " + documentId);
        }
    }

    public static class DocumentAccessDeniedException extends RuntimeException {
        public DocumentAccessDeniedException(String documentId, com.contentshub.domain.valueobject.UserId userId) {
            super(String.format("User %s does not have access to document %s", userId, documentId));
        }
    }

    public static class DocumentAlreadyPublishedException extends RuntimeException {
        public DocumentAlreadyPublishedException(String documentId) {
            super("Document is already published: " + documentId);
        }
    }

    public static class InvalidDocumentStateException extends RuntimeException {
        public InvalidDocumentStateException(String message) {
            super(message);
        }
    }
}

/**
 * Excepciones faltantes para AuthenticationUseCase
 */
class AuthenticationExceptions {

    public static class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException() {
            super("Invalid username or password");
        }

        public InvalidCredentialsException(String message) {
            super(message);
        }
    }

    public static class AccountLockedException extends RuntimeException {
        public AccountLockedException() {
            super("Account is locked due to multiple failed login attempts");
        }

        public AccountLockedException(String message) {
            super(message);
        }
    }

    public static class AccountDisabledException extends RuntimeException {
        public AccountDisabledException() {
            super("Account is disabled");
        }

        public AccountDisabledException(String message) {
            super(message);
        }
    }

    public static class InvalidTokenException extends RuntimeException {
        public InvalidTokenException(String message) {
            super(message);
        }
    }

    public static class TokenExpiredException extends RuntimeException {
        public TokenExpiredException() {
            super("Token has expired");
        }

        public TokenExpiredException(String message) {
            super(message);
        }
    }

    public static class UserAlreadyExistsException extends RuntimeException {
        public UserAlreadyExistsException(String field, String value) {
            super(String.format("User with %s '%s' already exists", field, value));
        }
    }

    public static class EmailNotVerifiedException extends RuntimeException {
        public EmailNotVerifiedException() {
            super("Email address is not verified");
        }
    }

    public static class PasswordResetTokenInvalidException extends RuntimeException {
        public PasswordResetTokenInvalidException() {
            super("Password reset token is invalid or expired");
        }
    }
}

/**
 * Excepciones faltantes para UserManagementUseCase
 */
class UserManagementExceptions {

    public static class UserNotFoundException extends RuntimeException {
        public UserNotFoundException(String identifier) {
            super("User not found: " + identifier);
        }
    }

    public static class UserAlreadyExistsException extends RuntimeException {
        public UserAlreadyExistsException(String field, String value) {
            super(String.format("User with %s '%s' already exists", field, value));
        }
    }

    public static class InvalidPasswordException extends RuntimeException {
        public InvalidPasswordException(String message) {
            super(message);
        }
    }

    public static class UserAccountLockedException extends RuntimeException {
        public UserAccountLockedException(String username) {
            super("User account is locked: " + username);
        }
    }

    public static class UserAccountDisabledException extends RuntimeException {
        public UserAccountDisabledException(String username) {
            super("User account is disabled: " + username);
        }
    }

    public static class InsufficientPrivilegesException extends RuntimeException {
        public InsufficientPrivilegesException(String operation) {
            super("Insufficient privileges to perform operation: " + operation);
        }
    }

    public static class RoleNotFoundException extends RuntimeException {
        public RoleNotFoundException(String roleName) {
            super("Role not found: " + roleName);
        }
    }

    public static class InvalidRoleAssignmentException extends RuntimeException {
        public InvalidRoleAssignmentException(String message) {
            super(message);
        }
    }
}
