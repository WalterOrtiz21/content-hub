package com.contentshub.application.usecase;

import com.contentshub.application.port.input.AuthenticationUseCase;
import com.contentshub.application.port.input.UserManagementUseCase;
import com.contentshub.application.port.output.CacheRepositoryPort;
import com.contentshub.application.port.output.EventPublisherPort;
import com.contentshub.application.port.output.UserRepositoryPort;
import com.contentshub.domain.event.UserEvents;
import com.contentshub.domain.model.User;
import com.contentshub.domain.valueobject.Email;
import com.contentshub.domain.valueobject.UserId;
import com.contentshub.domain.valueobject.Username;
import com.contentshub.infrastructure.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * Implementación de casos de uso para autenticación
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationService implements AuthenticationUseCase {

    private final UserRepositoryPort userRepository;
    private final UserManagementUseCase userManagementUseCase;
    private final CacheRepositoryPort cacheRepository;
    private final EventPublisherPort eventPublisher;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    // Cache keys
    private static final String REFRESH_TOKEN_PREFIX = "refresh_token:";
    private static final String FAILED_ATTEMPTS_PREFIX = "failed_attempts:";
    private static final String USER_SESSION_PREFIX = "user_session:";
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);
    private static final Duration FAILED_ATTEMPTS_TTL = Duration.ofMinutes(15);
    private static final int MAX_FAILED_ATTEMPTS = 5;

    @Override
    public Mono<AuthenticationResponse> authenticate(AuthenticationCommand command) {
        log.debug("Authenticating user: {}", command.usernameOrEmail());

        return checkFailedAttempts(command.usernameOrEmail())
                .then(findUser(command.usernameOrEmail()))
                .flatMap(user -> validateUser(user, command.password()))
                .flatMap(user -> generateTokens(user, command))
                .flatMap(response -> updateLastLogin(response, command))
                .flatMap(this::publishLoginEvent)
                .doOnSuccess(response -> {
                    log.info("User authenticated successfully: {}", response.username());
                    clearFailedAttempts(command.usernameOrEmail());
                })
                .doOnError(error -> {
                    log.warn("Authentication failed for {}: {}", command.usernameOrEmail(), error.getMessage());
                    recordFailedAttempt(command.usernameOrEmail());
                });
    }

    @Override
    public Mono<TokenResponse> refreshToken(RefreshTokenCommand command) {
        log.debug("Refreshing token");

        return validateRefreshToken(command.refreshToken())
                .flatMap(this::generateNewTokens)
                .doOnSuccess(response -> log.debug("Token refreshed successfully"))
                .doOnError(error -> log.error("Error refreshing token: {}", error.getMessage()));
    }

    @Override
    public Mono<Void> logout(LogoutCommand command) {
        log.debug("Logging out user: {}", command.userId());

        return invalidateTokens(command.accessToken(), command.refreshToken())
                .then(removeUserSession(command.userId()))
                .doOnSuccess(unused -> log.info("User logged out: {}", command.userId()))
                .doOnError(error -> log.error("Error during logout: {}", error.getMessage()));
    }

    @Override
    public Mono<Void> logoutAllSessions(UserId userId) {
        log.debug("Logging out all sessions for user: {}", userId);

        return cacheRepository.clearByPattern(USER_SESSION_PREFIX + userId.getValue() + ":*")
                .then(cacheRepository.clearByPattern(REFRESH_TOKEN_PREFIX + userId.getValue() + ":*"))
                .doOnSuccess(unused -> log.info("All sessions logged out for user: {}", userId))
                .doOnError(error -> log.error("Error logging out all sessions: {}", error.getMessage()));
    }

    @Override
    public Mono<TokenValidationResponse> validateToken(String token) {
        if (!jwtProvider.validateToken(token)) {
            return Mono.just(TokenValidationResponse.invalid("Token is invalid or expired"));
        }

        try {
            String username = jwtProvider.getUsernameFromToken(token);
            Long userId = jwtProvider.getUserIdFromToken(token);
            var authorities = jwtProvider.getAuthoritiesFromToken(token);
            var expiration = jwtProvider.getExpirationFromToken(token).toInstant()
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();

            Set<String> roles = authorities.stream()
                    .map(auth -> auth.getAuthority())
                    .filter(auth -> auth.startsWith("ROLE_"))
                    .collect(java.util.stream.Collectors.toSet());

            Set<String> permissions = authorities.stream()
                    .map(auth -> auth.getAuthority())
                    .filter(auth -> !auth.startsWith("ROLE_"))
                    .collect(java.util.stream.Collectors.toSet());

            return Mono.just(TokenValidationResponse.valid(
                    UserId.of(userId), username, roles, permissions, expiration));

        } catch (Exception e) {
            return Mono.just(TokenValidationResponse.invalid("Error validating token: " + e.getMessage()));
        }
    }

    @Override
    public Mono<UserTokenInfo> getUserFromToken(String token) {
        return validateToken(token)
                .filter(TokenValidationResponse::isValid)
                .switchIfEmpty(Mono.error(new InvalidTokenException("Invalid token")))
                .flatMap(validation -> userRepository.findById(validation.userId()))
                .map(user -> new UserTokenInfo(
                        user.getUserId(),
                        user.getUsername().getValue(),
                        user.getEmail().getValue(),
                        user.getFirstName(),
                        user.getLastName(),
                        user.getRoles() != null ?
                                user.getRoles().stream()
                                        .map(role -> role.getName())
                                        .collect(java.util.stream.Collectors.toSet()) : Set.of(),
                        user.getAllPermissions(),
                        user.getLastLoginAt()
                ));
    }

    @Override
    public Mono<AuthenticationResponse> register(RegisterCommand command) {
        log.debug("Registering new user: {}", command.username());

        UserManagementUseCase.CreateUserCommand createCommand =
                new UserManagementUseCase.CreateUserCommand(
                        command.username(),
                        command.email(),
                        command.password(),
                        command.firstName(),
                        command.lastName(),
                        "system"
                );

        return userManagementUseCase.createUser(createCommand)
                .flatMap(userResponse -> {
                    // Convertir UserResponse a AuthenticationResponse
                    return generateTokensForUser(userResponse, command.ipAddress(), command.userAgent());
                })
                .doOnSuccess(response -> log.info("User registered successfully: {}", response.username()))
                .doOnError(error -> log.error("Error registering user {}: {}", command.username(), error.getMessage()));
    }

    @Override
    public Mono<Void> requestPasswordReset(String email) {
        log.debug("Password reset requested for email: {}", email);

        return userRepository.findByEmail(Email.of(email))
                .flatMap(user -> {
                    String resetToken = generatePasswordResetToken(user);
                    return storePasswordResetToken(user.getUserId(), resetToken)
                            .then(sendPasswordResetEmail(user, resetToken));
                })
                .then()
                .doOnSuccess(unused -> log.info("Password reset email sent to: {}", email))
                .doOnError(error -> log.error("Error requesting password reset for {}: {}", email, error.getMessage()));
    }

    @Override
    public Mono<Void> resetPassword(ResetPasswordCommand command) {
        log.debug("Resetting password with token");

        return validatePasswordResetToken(command.resetToken())
                .flatMap(userId -> userRepository.findById(userId))
                .flatMap(user -> {
                    String hashedPassword = passwordEncoder.encode(command.newPassword());
                    User updatedUser = user.changePassword(hashedPassword, "password_reset");
                    return userRepository.save(updatedUser);
                })
                .then(invalidatePasswordResetToken(command.resetToken()))
                .doOnSuccess(unused -> log.info("Password reset successfully"))
                .doOnError(error -> log.error("Error resetting password: {}", error.getMessage()));
    }

    @Override
    public Mono<Void> verifyEmail(String verificationToken) {
        log.debug("Verifying email with token");

        return validateEmailVerificationToken(verificationToken)
                .flatMap(userId -> userRepository.findById(userId))
                .flatMap(user -> {
                    // Marcar email como verificado
                    return userRepository.save(user);
                })
                .then()
                .doOnSuccess(unused -> log.info("Email verified successfully"))
                .doOnError(error -> log.error("Error verifying email: {}", error.getMessage()));
    }

    @Override
    public Mono<Void> resendVerificationEmail(String email) {
        log.debug("Resending verification email to: {}", email);

        return userRepository.findByEmail(Email.of(email))
                .flatMap(user -> {
                    String verificationToken = generateEmailVerificationToken(user);
                    return sendVerificationEmail(user, verificationToken);
                })
                .then()
                .doOnSuccess(unused -> log.info("Verification email resent to: {}", email))
                .doOnError(error -> log.error("Error resending verification email: {}", error.getMessage()));
    }

    @Override
    public Mono<ActiveSessionsResponse> getActiveSessions(UserId userId) {
        log.debug("Getting active sessions for user: {}", userId);

        return cacheRepository.findKeysByPattern(USER_SESSION_PREFIX + userId.getValue() + ":*")
                .flatMap(key -> cacheRepository.get(key, SessionInfo.class))
                .collectList()
                .map(sessions -> new ActiveSessionsResponse(userId, sessions, sessions.size()))
                .doOnSuccess(response -> log.debug("Found {} active sessions for user: {}",
                        response.totalSessions(), userId))
                .doOnError(error -> log.error("Error getting active sessions: {}", error.getMessage()));
    }

    /**
     * Verificar intentos fallidos de login
     */
    private Mono<Void> checkFailedAttempts(String usernameOrEmail) {
        String key = FAILED_ATTEMPTS_PREFIX + usernameOrEmail;
        return cacheRepository.get(key, Integer.class)
                .defaultIfEmpty(0)
                .filter(attempts -> attempts < MAX_FAILED_ATTEMPTS)
                .switchIfEmpty(Mono.error(new AccountLockedException()))
                .then();
    }

    /**
     * Buscar usuario por username o email
     */
    private Mono<User> findUser(String usernameOrEmail) {
        return userRepository.findByUsernameOrEmail(usernameOrEmail)
                .switchIfEmpty(Mono.error(new InvalidCredentialsException()));
    }

    /**
     * Validar usuario y contraseña
     */
    private Mono<User> validateUser(User user, String password) {
        if (!user.isActive()) {
            if (!user.getIsEnabled()) {
                return Mono.error(new AccountDisabledException());
            }
            if (!user.getIsAccountNonLocked()) {
                return Mono.error(new AccountLockedException());
            }
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            return Mono.error(new InvalidCredentialsException());
        }

        return Mono.just(user);
    }

    /**
     * Generar tokens JWT
     */
    private Mono<AuthenticationResponse> generateTokens(User user, AuthenticationCommand command) {
        return Mono.fromCallable(() -> {
            String accessToken = jwtProvider.generateToken(
                    createUserDetails(user),
                    user.getId(),
                    user.getEmail().getValue()
            );
            String refreshToken = jwtProvider.generateRefreshToken(user.getUsername().getValue());

            LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(
                    Duration.ofMillis(jwtProvider.getExpirationFromToken(accessToken).getTime()).getSeconds()
            );

            return new AuthenticationResponse(
                    user.getUserId(),
                    user.getUsername().getValue(),
                    user.getEmail().getValue(),
                    user.getFirstName(),
                    user.getLastName(),
                    accessToken,
                    refreshToken,
                    expiresAt,
                    user.getRoles() != null ?
                            user.getRoles().stream()
                                    .map(role -> role.getName())
                                    .collect(java.util.stream.Collectors.toSet()) : Set.of(),
                    user.getAllPermissions(),
                    true, // isEmailVerified - implementar lógica real
                    false // requiresPasswordChange
            );
        }).flatMap(response -> storeRefreshToken(user.getUserId(), response.refreshToken())
                .then(storeUserSession(user.getUserId(), command))
                .thenReturn(response));
    }

    /**
     * Generar tokens para usuario registrado
     */
    private Mono<AuthenticationResponse> generateTokensForUser(UserManagementUseCase.UserResponse userResponse,
                                                               String ipAddress, String userAgent) {
        return userRepository.findById(userResponse.userId())
                .flatMap(user -> generateTokens(user, new AuthenticationCommand(
                        user.getUsername().getValue(),
                        "", // password not needed here
                        ipAddress,
                        userAgent,
                        false
                )));
    }

    /**
     * Actualizar último login
     */
    private Mono<AuthenticationResponse> updateLastLogin(AuthenticationResponse response, AuthenticationCommand command) {
        return userRepository.updateLastLogin(response.userId(), LocalDateTime.now())
                .thenReturn(response);
    }

    /**
     * Crear UserDetails para JWT
     */
    private org.springframework.security.core.userdetails.UserDetails createUserDetails(User user) {
        var authorities = user.getAllPermissions().stream()
                .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new)
                .collect(java.util.stream.Collectors.toList());

        // Agregar roles
        if (user.getRoles() != null) {
            user.getRoles().stream()
                    .map(role -> new org.springframework.security.core.authority.SimpleGrantedAuthority(role.getName()))
                    .forEach(authorities::add);
        }

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername().getValue())
                .password(user.getPasswordHash())
                .authorities(authorities)
                .accountExpired(!user.getIsAccountNonExpired())
                .accountLocked(!user.getIsAccountNonLocked())
                .credentialsExpired(!user.getIsCredentialsNonExpired())
                .disabled(!user.getIsEnabled())
                .build();
    }

    /**
     * Registrar intento fallido
     */
    private void recordFailedAttempt(String usernameOrEmail) {
        String key = FAILED_ATTEMPTS_PREFIX + usernameOrEmail;
        cacheRepository.increment(key)
                .then(cacheRepository.expire(key, FAILED_ATTEMPTS_TTL))
                .subscribe();
    }

    /**
     * Limpiar intentos fallidos
     */
    private void clearFailedAttempts(String usernameOrEmail) {
        String key = FAILED_ATTEMPTS_PREFIX + usernameOrEmail;
        cacheRepository.delete(key).subscribe();
    }

    /**
     * Almacenar refresh token
     */
    private Mono<Void> storeRefreshToken(UserId userId, String refreshToken) {
        String key = REFRESH_TOKEN_PREFIX + userId.getValue() + ":" + UUID.randomUUID();
        return cacheRepository.set(key, refreshToken, REFRESH_TOKEN_TTL);
    }

    /**
     * Almacenar sesión de usuario
     */
    private Mono<Void> storeUserSession(UserId userId, AuthenticationCommand command) {
        String sessionId = UUID.randomUUID().toString();
        String key = USER_SESSION_PREFIX + userId.getValue() + ":" + sessionId;

        SessionInfo sessionInfo = new SessionInfo(
                sessionId,
                command.ipAddress(),
                command.userAgent(),
                "Unknown", // location - implementar geolocalización
                LocalDateTime.now(),
                LocalDateTime.now(),
                true
        );

        return cacheRepository.set(key, sessionInfo, Duration.ofHours(24));
    }

    /**
     * Validar refresh token
     */
    private Mono<User> validateRefreshToken(String refreshToken) {
        if (!jwtProvider.validateToken(refreshToken)) {
            return Mono.error(new InvalidTokenException("Invalid refresh token"));
        }

        String username = jwtProvider.getUsernameFromToken(refreshToken);
        return userRepository.findByUsername(Username.of(username));
    }

    /**
     * Generar nuevos tokens
     */
    private Mono<TokenResponse> generateNewTokens(User user) {
        return Mono.fromCallable(() -> {
            String accessToken = jwtProvider.generateToken(
                    createUserDetails(user),
                    user.getId(),
                    user.getEmail().getValue()
            );
            String refreshToken = jwtProvider.generateRefreshToken(user.getUsername().getValue());

            LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(
                    Duration.ofMillis(jwtProvider.getExpirationFromToken(accessToken).getTime()).getSeconds()
            );

            return TokenResponse.create(accessToken, refreshToken, expiresAt);
        });
    }

    /**
     * Invalidar tokens
     */
    private Mono<Void> invalidateTokens(String accessToken, String refreshToken) {
        // En una implementación real, mantendrías una blacklist de tokens
        return Mono.empty();
    }

    /**
     * Remover sesión de usuario
     */
    private Mono<Void> removeUserSession(UserId userId) {
        return cacheRepository.clearByPattern(USER_SESSION_PREFIX + userId.getValue() + ":*");
    }

    /**
     * Publicar evento de login
     */
    private Mono<AuthenticationResponse> publishLoginEvent(AuthenticationResponse response) {
        UserEvents.UserLoggedIn event = UserEvents.UserLoggedIn.builder()
                .userId(response.userId())
                .ipAddress("") // Obtener de context
                .userAgent("") // Obtener de context
                .loginTime(LocalDateTime.now())
                .build();

        return eventPublisher.publish(event).thenReturn(response);
    }

    /**
     * Generar token de reset de contraseña
     */
    private String generatePasswordResetToken(User user) {
        return UUID.randomUUID().toString();
    }

    /**
     * Almacenar token de reset de contraseña
     */
    private Mono<Void> storePasswordResetToken(UserId userId, String token) {
        return cacheRepository.set("password_reset:" + token, userId, Duration.ofHours(1));
    }

    /**
     * Enviar email de reset de contraseña
     */
    private Mono<Void> sendPasswordResetEmail(User user, String token) {
        // Implementar envío de email
        return Mono.empty();
    }

    /**
     * Validar token de reset de contraseña
     */
    private Mono<UserId> validatePasswordResetToken(String token) {
        return cacheRepository.get("password_reset:" + token, UserId.class)
                .switchIfEmpty(Mono.error(new InvalidTokenException("Invalid or expired reset token")));
    }

    /**
     * Invalidar token de reset de contraseña
     */
    private Mono<Void> invalidatePasswordResetToken(String token) {
        return cacheRepository.delete("password_reset:" + token);
    }

    /**
     * Generar token de verificación de email
     */
    private String generateEmailVerificationToken(User user) {
        return UUID.randomUUID().toString();
    }

    /**
     * Validar token de verificación de email
     */
    private Mono<UserId> validateEmailVerificationToken(String token) {
        return cacheRepository.get("email_verification:" + token, UserId.class)
                .switchIfEmpty(Mono.error(new InvalidTokenException("Invalid or expired verification token")));
    }

    /**
     * Enviar email de verificación
     */
    private Mono<Void> sendVerificationEmail(User user, String token) {
        // Implementar envío de email
        return Mono.empty();
    }
}
