package com.contentshub.application.port.input;

import com.contentshub.domain.valueobject.UserId;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Puerto de entrada para casos de uso de autenticación
 */
public interface AuthenticationUseCase {

    /**
     * Autenticar usuario con credenciales
     */
    Mono<AuthenticationResponse> authenticate(AuthenticationCommand command);

    /**
     * Registrar nuevo usuario
     */
    Mono<AuthenticationResponse> register(RegisterCommand command);

    /**
     * Refrescar token de acceso
     */
    Mono<TokenResponse> refreshToken(RefreshTokenCommand command);

    /**
     * Cerrar sesión (invalidar tokens)
     */
    Mono<Void> logout(LogoutCommand command);

    /**
     * Cerrar todas las sesiones del usuario
     */
    Mono<Void> logoutAllSessions(UserId userId);

    /**
     * Validar token y obtener información
     */
    Mono<TokenValidationResponse> validateToken(String token);

    /**
     * Obtener información del usuario desde el token
     */
    Mono<UserTokenInfo> getUserFromToken(String token);

    /**
     * Solicitar reset de contraseña
     */
    Mono<Void> requestPasswordReset(String email);

    /**
     * Confirmar reset de contraseña
     */
    Mono<Void> resetPassword(ResetPasswordCommand command);

    /**
     * Verificar email del usuario
     */
    Mono<Void> verifyEmail(String verificationToken);

    /**
     * Reenviar email de verificación
     */
    Mono<Void> resendVerificationEmail(String email);

    /**
     * Obtener sesiones activas del usuario
     */
    Mono<ActiveSessionsResponse> getActiveSessions(UserId userId);

    /**
     * Commands (DTOs de entrada)
     */
    record AuthenticationCommand(
            String usernameOrEmail,
            String password,
            String ipAddress,
            String userAgent,
            boolean rememberMe
    ) {}

    record RegisterCommand(
            String username,
            String email,
            String password,
            String firstName,
            String lastName,
            String ipAddress,
            String userAgent
    ) {}

    record RefreshTokenCommand(
            String refreshToken,
            String ipAddress,
            String userAgent
    ) {}

    record LogoutCommand(
            String accessToken,
            String refreshToken,
            UserId userId
    ) {}

    record ResetPasswordCommand(
            String resetToken,
            String newPassword
    ) {}

    /**
     * Responses (DTOs de salida)
     */
    record AuthenticationResponse(
            UserId userId,
            String username,
            String email,
            String firstName,
            String lastName,
            String accessToken,
            String refreshToken,
            LocalDateTime expiresAt,
            Set<String> roles,
            Set<String> permissions,
            boolean isEmailVerified,
            boolean requiresPasswordChange
    ) {}

    record TokenResponse(
            String accessToken,
            String refreshToken,
            LocalDateTime expiresAt,
            String tokenType
    ) {
        public static TokenResponse create(String accessToken, String refreshToken, LocalDateTime expiresAt) {
            return new TokenResponse(accessToken, refreshToken, expiresAt, "Bearer");
        }
    }

    record TokenValidationResponse(
            boolean isValid,
            UserId userId,
            String username,
            Set<String> roles,
            Set<String> permissions,
            LocalDateTime expiresAt,
            String errorMessage
    ) {
        public static TokenValidationResponse valid(UserId userId, String username,
                                                    Set<String> roles, Set<String> permissions,
                                                    LocalDateTime expiresAt) {
            return new TokenValidationResponse(true, userId, username, roles, permissions, expiresAt, null);
        }

        public static TokenValidationResponse invalid(String errorMessage) {
            return new TokenValidationResponse(false, null, null, Set.of(), Set.of(), null, errorMessage);
        }
    }

    record UserTokenInfo(
            UserId userId,
            String username,
            String email,
            String firstName,
            String lastName,
            Set<String> roles,
            Set<String> permissions,
            LocalDateTime lastLoginAt
    ) {}

    record ActiveSessionsResponse(
            UserId userId,
            java.util.List<SessionInfo> sessions,
            int totalSessions
    ) {}

    record SessionInfo(
            String sessionId,
            String ipAddress,
            String userAgent,
            String location,
            LocalDateTime loginTime,
            LocalDateTime lastActivity,
            boolean isCurrent
    ) {}

    /**
     * Excepciones específicas de autenticación
     */
    class AuthenticationException extends RuntimeException {
        public AuthenticationException(String message) {
            super(message);
        }
        public AuthenticationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    class InvalidCredentialsException extends AuthenticationException {
        public InvalidCredentialsException() {
            super("Invalid username or password");
        }
    }

    class AccountLockedException extends AuthenticationException {
        public AccountLockedException() {
            super("Account is locked due to multiple failed login attempts");
        }
    }

    class AccountDisabledException extends AuthenticationException {
        public AccountDisabledException() {
            super("Account is disabled");
        }
    }

    class InvalidTokenException extends AuthenticationException {
        public InvalidTokenException(String message) {
            super(message);
        }
    }

    class TokenExpiredException extends AuthenticationException {
        public TokenExpiredException() {
            super("Token has expired");
        }
    }

    class UserAlreadyExistsException extends AuthenticationException {
        public UserAlreadyExistsException(String field, String value) {
            super(String.format("User with %s '%s' already exists", field, value));
        }
    }

    class EmailNotVerifiedException extends AuthenticationException {
        public EmailNotVerifiedException() {
            super("Email address is not verified");
        }
    }

    class PasswordResetTokenInvalidException extends AuthenticationException {
        public PasswordResetTokenInvalidException() {
            super("Password reset token is invalid or expired");
        }
    }
}
