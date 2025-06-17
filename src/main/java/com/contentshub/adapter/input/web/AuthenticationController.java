package com.contentshub.adapter.input.web;

import com.contentshub.application.port.input.AuthenticationUseCase;
import com.contentshub.domain.valueobject.UserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * Controlador REST para operaciones de autenticación
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "Endpoints para autenticación y autorización")
public class AuthenticationController {

    private final AuthenticationUseCase authenticationUseCase;

    @PostMapping("/login")
    @Operation(
            summary = "Iniciar sesión",
            description = "Autentica un usuario con email/username y contraseña",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Autenticación exitosa"),
                    @ApiResponse(responseCode = "401", description = "Credenciales inválidas"),
                    @ApiResponse(responseCode = "423", description = "Cuenta bloqueada")
            }
    )
    public Mono<AuthenticationResponse> login(
            @Valid @RequestBody LoginRequest request,
            ServerHttpRequest httpRequest) {

        log.debug("Login attempt for: {}", request.usernameOrEmail());

        AuthenticationUseCase.AuthenticationCommand command =
                new AuthenticationUseCase.AuthenticationCommand(
                        request.usernameOrEmail(),
                        request.password(),
                        getClientIp(httpRequest),
                        getUserAgent(httpRequest),
                        request.rememberMe()
                );

        return authenticationUseCase.authenticate(command)
                .map(this::toAuthenticationResponse)
                .doOnSuccess(response -> log.info("User authenticated: {}", response.username()))
                .doOnError(error -> log.warn("Authentication failed for {}: {}",
                        request.usernameOrEmail(), error.getMessage()));
    }

    @PostMapping("/register")
    @Operation(
            summary = "Registrar nuevo usuario",
            description = "Crea una nueva cuenta de usuario",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Usuario registrado exitosamente"),
                    @ApiResponse(responseCode = "409", description = "Usuario ya existe"),
                    @ApiResponse(responseCode = "400", description = "Datos inválidos")
            }
    )
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<AuthenticationResponse> register(
            @Valid @RequestBody RegisterRequest request,
            ServerHttpRequest httpRequest) {

        log.debug("Registration attempt for: {}", request.username());

        AuthenticationUseCase.RegisterCommand command =
                new AuthenticationUseCase.RegisterCommand(
                        request.username(),
                        request.email(),
                        request.password(),
                        request.firstName(),
                        request.lastName(),
                        getClientIp(httpRequest),
                        getUserAgent(httpRequest)
                );

        return authenticationUseCase.register(command)
                .map(this::toAuthenticationResponse)
                .doOnSuccess(response -> log.info("User registered: {}", response.username()))
                .doOnError(error -> log.error("Registration failed for {}: {}",
                        request.username(), error.getMessage()));
    }

    @PostMapping("/refresh")
    @Operation(
            summary = "Refrescar token",
            description = "Genera un nuevo token de acceso usando el refresh token",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Token refrescado exitosamente"),
                    @ApiResponse(responseCode = "401", description = "Refresh token inválido")
            }
    )
    public Mono<TokenResponse> refreshToken(
            @Valid @RequestBody RefreshTokenRequest request,
            ServerHttpRequest httpRequest) {

        log.debug("Token refresh attempt");

        AuthenticationUseCase.RefreshTokenCommand command =
                new AuthenticationUseCase.RefreshTokenCommand(
                        request.refreshToken(),
                        getClientIp(httpRequest),
                        getUserAgent(httpRequest)
                );

        return authenticationUseCase.refreshToken(command)
                .map(this::toTokenResponse)
                .doOnSuccess(response -> log.debug("Token refreshed successfully"))
                .doOnError(error -> log.warn("Token refresh failed: {}", error.getMessage()));
    }

    @PostMapping("/logout")
    @Operation(
            summary = "Cerrar sesión",
            description = "Invalida el token actual y cierra la sesión",
            security = @SecurityRequirement(name = "Bearer Authentication"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Sesión cerrada exitosamente"),
                    @ApiResponse(responseCode = "401", description = "Token inválido")
            }
    )
    public Mono<Void> logout(
            @Valid @RequestBody LogoutRequest request,
            @AuthenticationPrincipal Object principal) {

        log.debug("Logout attempt");

        // Extraer userId del principal (implementar según tu JwtAuthenticationConverter)
        UserId userId = extractUserIdFromPrincipal(principal);

        AuthenticationUseCase.LogoutCommand command =
                new AuthenticationUseCase.LogoutCommand(
                        request.accessToken(),
                        request.refreshToken(),
                        userId
                );

        return authenticationUseCase.logout(command)
                .doOnSuccess(unused -> log.info("User logged out: {}", userId))
                .doOnError(error -> log.error("Logout failed: {}", error.getMessage()));
    }

    @PostMapping("/logout-all")
    @Operation(
            summary = "Cerrar todas las sesiones",
            description = "Invalida todos los tokens del usuario",
            security = @SecurityRequirement(name = "Bearer Authentication"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Todas las sesiones cerradas"),
                    @ApiResponse(responseCode = "401", description = "Token inválido")
            }
    )
    public Mono<Void> logoutAllSessions(@AuthenticationPrincipal Object principal) {
        log.debug("Logout all sessions attempt");

        UserId userId = extractUserIdFromPrincipal(principal);

        return authenticationUseCase.logoutAllSessions(userId)
                .doOnSuccess(unused -> log.info("All sessions logged out for user: {}", userId))
                .doOnError(error -> log.error("Logout all sessions failed: {}", error.getMessage()));
    }

    @PostMapping("/password-reset/request")
    @Operation(
            summary = "Solicitar reset de contraseña",
            description = "Envía un email con link para resetear contraseña",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Email enviado si el usuario existe"),
                    @ApiResponse(responseCode = "429", description = "Demasiadas solicitudes")
            }
    )
    public Mono<MessageResponse> requestPasswordReset(
            @Valid @RequestBody PasswordResetRequestRequest request) {

        log.debug("Password reset requested for: {}", request.email());

        return authenticationUseCase.requestPasswordReset(request.email())
                .then(Mono.just(new MessageResponse(
                        "Si el email existe, recibirás instrucciones para resetear tu contraseña")))
                .doOnSuccess(response -> log.info("Password reset requested for: {}", request.email()))
                .doOnError(error -> log.error("Password reset request failed: {}", error.getMessage()));
    }

    @PostMapping("/password-reset/confirm")
    @Operation(
            summary = "Confirmar reset de contraseña",
            description = "Resetea la contraseña usando el token recibido por email",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Contraseña reseteada exitosamente"),
                    @ApiResponse(responseCode = "400", description = "Token inválido o expirado")
            }
    )
    public Mono<MessageResponse> confirmPasswordReset(
            @Valid @RequestBody PasswordResetConfirmRequest request) {

        log.debug("Password reset confirmation attempt");

        AuthenticationUseCase.ResetPasswordCommand command =
                new AuthenticationUseCase.ResetPasswordCommand(
                        request.resetToken(),
                        request.newPassword()
                );

        return authenticationUseCase.resetPassword(command)
                .then(Mono.just(new MessageResponse("Contraseña reseteada exitosamente")))
                .doOnSuccess(response -> log.info("Password reset confirmed"))
                .doOnError(error -> log.error("Password reset confirmation failed: {}", error.getMessage()));
    }

    @PostMapping("/email/verify")
    @Operation(
            summary = "Verificar email",
            description = "Verifica el email del usuario usando el token",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Email verificado exitosamente"),
                    @ApiResponse(responseCode = "400", description = "Token inválido o expirado")
            }
    )
    public Mono<MessageResponse> verifyEmail(@RequestParam String token) {
        log.debug("Email verification attempt");

        return authenticationUseCase.verifyEmail(token)
                .then(Mono.just(new MessageResponse("Email verificado exitosamente")))
                .doOnSuccess(response -> log.info("Email verified"))
                .doOnError(error -> log.error("Email verification failed: {}", error.getMessage()));
    }

    @PostMapping("/email/resend")
    @Operation(
            summary = "Reenviar verificación de email",
            description = "Reenvía el email de verificación",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Email reenviado"),
                    @ApiResponse(responseCode = "429", description = "Demasiadas solicitudes")
            }
    )
    public Mono<MessageResponse> resendVerificationEmail(
            @Valid @RequestBody ResendVerificationRequest request) {

        log.debug("Resend verification email for: {}", request.email());

        return authenticationUseCase.resendVerificationEmail(request.email())
                .then(Mono.just(new MessageResponse("Email de verificación reenviado")))
                .doOnSuccess(response -> log.info("Verification email resent"))
                .doOnError(error -> log.error("Resend verification failed: {}", error.getMessage()));
    }

    @GetMapping("/me")
    @Operation(
            summary = "Obtener información del usuario actual",
            description = "Retorna la información del usuario autenticado",
            security = @SecurityRequirement(name = "Bearer Authentication"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Información del usuario"),
                    @ApiResponse(responseCode = "401", description = "Token inválido")
            }
    )
    public Mono<UserInfoResponse> getCurrentUser(@AuthenticationPrincipal Object principal) {
        log.debug("Get current user info");

        // Extraer token del principal
        String token = extractTokenFromPrincipal(principal);

        return authenticationUseCase.getUserFromToken(token)
                .map(this::toUserInfoResponse)
                .doOnSuccess(user -> log.debug("Current user info retrieved: {}", user.username()))
                .doOnError(error -> log.error("Get current user failed: {}", error.getMessage()));
    }

    @GetMapping("/sessions")
    @Operation(
            summary = "Obtener sesiones activas",
            description = "Lista todas las sesiones activas del usuario",
            security = @SecurityRequirement(name = "Bearer Authentication"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Lista de sesiones activas"),
                    @ApiResponse(responseCode = "401", description = "Token inválido")
            }
    )
    public Mono<ActiveSessionsResponse> getActiveSessions(@AuthenticationPrincipal Object principal) {
        log.debug("Get active sessions");

        UserId userId = extractUserIdFromPrincipal(principal);

        return authenticationUseCase.getActiveSessions(userId)
                .map(this::toActiveSessionsResponse)
                .doOnSuccess(sessions -> log.debug("Active sessions retrieved: {}", sessions.totalSessions()))
                .doOnError(error -> log.error("Get active sessions failed: {}", error.getMessage()));
    }

    /**
     * Métodos auxiliares
     */
    private String getClientIp(ServerHttpRequest request) {
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return request.getRemoteAddress() != null ?
                request.getRemoteAddress().getAddress().getHostAddress() : "unknown";
    }

    private String getUserAgent(ServerHttpRequest request) {
        return request.getHeaders().getFirst("User-Agent");
    }

    private UserId extractUserIdFromPrincipal(Object principal) {
        if (principal instanceof com.contentshub.infrastructure.security.JwtAuthenticationConverter.JwtUserPrincipal jwtPrincipal) {
            return UserId.of(jwtPrincipal.getUserId());
        }

        // Fallback para casos de testing o desarrollo
        log.warn("Extracting user ID from principal of type: {}", principal.getClass());
        return UserId.of(1L);
    }

    private String extractTokenFromPrincipal(Object principal) {
        if (principal instanceof com.contentshub.infrastructure.security.JwtAuthenticationConverter.JwtUserPrincipal jwtPrincipal) {
            return jwtPrincipal.getToken();
        }

        log.warn("Cannot extract token from principal of type: {}", principal.getClass());
        return "";
    }

    /**
     * Mappers de response
     */
    private AuthenticationResponse toAuthenticationResponse(AuthenticationUseCase.AuthenticationResponse response) {
        return new AuthenticationResponse(
                response.userId().getValue(),
                response.username(),
                response.email(),
                response.firstName(),
                response.lastName(),
                response.accessToken(),
                response.refreshToken(),
                response.expiresAt(),
                response.roles(),
                response.permissions(),
                response.isEmailVerified(),
                response.requiresPasswordChange()
        );
    }

    private TokenResponse toTokenResponse(AuthenticationUseCase.TokenResponse response) {
        return new TokenResponse(
                response.accessToken(),
                response.refreshToken(),
                response.expiresAt(),
                response.tokenType()
        );
    }

    private UserInfoResponse toUserInfoResponse(AuthenticationUseCase.UserTokenInfo info) {
        return new UserInfoResponse(
                info.userId().getValue(),
                info.username(),
                info.email(),
                info.firstName(),
                info.lastName(),
                info.roles(),
                info.permissions(),
                info.lastLoginAt()
        );
    }

    private ActiveSessionsResponse toActiveSessionsResponse(AuthenticationUseCase.ActiveSessionsResponse response) {
        return new ActiveSessionsResponse(
                response.userId().getValue(),
                response.sessions().stream()
                        .map(session -> new SessionInfoResponse(
                                session.sessionId(),
                                session.ipAddress(),
                                session.userAgent(),
                                session.location(),
                                session.loginTime(),
                                session.lastActivity(),
                                session.isCurrent()
                        ))
                        .toList(),
                response.totalSessions()
        );
    }

    /**
     * DTOs de Request
     */
    public record LoginRequest(
            @NotBlank(message = "Username o email es requerido")
            String usernameOrEmail,

            @NotBlank(message = "Password es requerido")
            @Size(min = 6, max = 100, message = "Password debe tener entre 6 y 100 caracteres")
            String password,

            boolean rememberMe
    ) {}

    public record RegisterRequest(
            @NotBlank(message = "Username es requerido")
            @Size(min = 3, max = 50, message = "Username debe tener entre 3 y 50 caracteres")
            String username,

            @NotBlank(message = "Email es requerido")
            @Email(message = "Email debe ser válido")
            String email,

            @NotBlank(message = "Password es requerido")
            @Size(min = 6, max = 100, message = "Password debe tener entre 6 y 100 caracteres")
            String password,

            String firstName,
            String lastName
    ) {}

    public record RefreshTokenRequest(
            @NotBlank(message = "Refresh token es requerido")
            String refreshToken
    ) {}

    public record LogoutRequest(
            String accessToken,
            String refreshToken
    ) {}

    public record PasswordResetRequestRequest(
            @NotBlank(message = "Email es requerido")
            @Email(message = "Email debe ser válido")
            String email
    ) {}

    public record PasswordResetConfirmRequest(
            @NotBlank(message = "Reset token es requerido")
            String resetToken,

            @NotBlank(message = "Nueva password es requerida")
            @Size(min = 6, max = 100, message = "Password debe tener entre 6 y 100 caracteres")
            String newPassword
    ) {}

    public record ResendVerificationRequest(
            @NotBlank(message = "Email es requerido")
            @Email(message = "Email debe ser válido")
            String email
    ) {}

    /**
     * DTOs de Response
     */
    public record AuthenticationResponse(
            Long userId,
            String username,
            String email,
            String firstName,
            String lastName,
            String accessToken,
            String refreshToken,
            java.time.LocalDateTime expiresAt,
            java.util.Set<String> roles,
            java.util.Set<String> permissions,
            boolean isEmailVerified,
            boolean requiresPasswordChange
    ) {}

    public record TokenResponse(
            String accessToken,
            String refreshToken,
            java.time.LocalDateTime expiresAt,
            String tokenType
    ) {}

    public record UserInfoResponse(
            Long userId,
            String username,
            String email,
            String firstName,
            String lastName,
            java.util.Set<String> roles,
            java.util.Set<String> permissions,
            java.time.LocalDateTime lastLoginAt
    ) {}

    public record ActiveSessionsResponse(
            Long userId,
            java.util.List<SessionInfoResponse> sessions,
            int totalSessions
    ) {}

    public record SessionInfoResponse(
            String sessionId,
            String ipAddress,
            String userAgent,
            String location,
            java.time.LocalDateTime loginTime,
            java.time.LocalDateTime lastActivity,
            boolean isCurrent
    ) {}

    public record MessageResponse(String message) {}
}
