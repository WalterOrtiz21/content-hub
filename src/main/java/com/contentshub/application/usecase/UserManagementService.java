package com.contentshub.application.usecase;

import com.contentshub.application.port.input.UserManagementUseCase;
import com.contentshub.application.port.output.CacheRepositoryPort;
import com.contentshub.application.port.output.EventPublisherPort;
import com.contentshub.application.port.output.UserRepositoryPort;
import com.contentshub.domain.event.UserEvents;
import com.contentshub.domain.exception.DomainExceptions;
import com.contentshub.domain.model.User;
import com.contentshub.domain.valueobject.Email;
import com.contentshub.domain.valueobject.UserId;
import com.contentshub.domain.valueobject.Username;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Implementación de casos de uso para gestión de usuarios
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserManagementService implements UserManagementUseCase {

    private final UserRepositoryPort userRepository;
    private final CacheRepositoryPort cacheRepository;
    private final EventPublisherPort eventPublisher;
    private final PasswordEncoder passwordEncoder;

    // Cache keys
    private static final String USER_CACHE_PREFIX = "user:";
    private static final String USER_STATS_CACHE_KEY = "user:stats";
    private static final Duration USER_CACHE_TTL = Duration.ofHours(1);

    @Override
    public Mono<UserResponse> createUser(CreateUserCommand command) {
        log.debug("Creating user with username: {}", command.username());

        return validateUserCreation(command)
                .then(createUserEntity(command))
                .flatMap(userRepository::save)
                .flatMap(this::cacheUser)
                .flatMap(this::publishUserCreatedEvent)
                .map(UserResponse::fromUser)
                .doOnSuccess(user -> log.info("User created successfully: {}", user.username()))
                .doOnError(error -> log.error("Error creating user {}: {}", command.username(), error.getMessage()));
    }

    @Override
    public Mono<UserResponse> getUserById(UserId userId) {
        log.debug("Getting user by ID: {}", userId);

        String cacheKey = USER_CACHE_PREFIX + userId.getValue();

        return cacheRepository.get(cacheKey, User.class)
                .switchIfEmpty(userRepository.findById(userId)
                        .flatMap(user -> cacheUser(user).thenReturn(user)))
                .switchIfEmpty(Mono.error(new DomainExceptions.EntityNotFoundException("User", userId.toString())))
                .map(UserResponse::fromUser)
                .doOnSuccess(user -> log.debug("User retrieved: {}", user.username()))
                .doOnError(error -> log.error("Error getting user {}: {}", userId, error.getMessage()));
    }

    @Override
    public Mono<UserResponse> getUserByUsername(Username username) {
        log.debug("Getting user by username: {}", username);

        return userRepository.findByUsername(username)
                .switchIfEmpty(Mono.error(new DomainExceptions.EntityNotFoundException("User", username.getValue())))
                .flatMap(this::cacheUser)
                .map(UserResponse::fromUser)
                .doOnSuccess(user -> log.debug("User retrieved by username: {}", user.username()))
                .doOnError(error -> log.error("Error getting user by username {}: {}", username, error.getMessage()));
    }

    @Override
    public Mono<UserResponse> getUserByEmail(Email email) {
        log.debug("Getting user by email: {}", email);

        return userRepository.findByEmail(email)
                .switchIfEmpty(Mono.error(new DomainExceptions.EntityNotFoundException("User", email.getValue())))
                .flatMap(this::cacheUser)
                .map(UserResponse::fromUser)
                .doOnSuccess(user -> log.debug("User retrieved by email: {}", user.email()))
                .doOnError(error -> log.error("Error getting user by email {}: {}", email, error.getMessage()));
    }

    @Override
    public Mono<UserResponse> updateUserProfile(UpdateUserProfileCommand command) {
        log.debug("Updating user profile: {}", command.userId());

        return userRepository.findById(command.userId())
                .switchIfEmpty(Mono.error(new DomainExceptions.EntityNotFoundException("User", command.userId().toString())))
                .map(user -> user.updateProfile(
                        command.firstName(),
                        command.lastName(),
                        command.profilePictureUrl(),
                        command.updatedBy()))
                .flatMap(userRepository::save)
                .flatMap(this::invalidateUserCache)
                .flatMap(this::publishUserProfileUpdatedEvent)
                .map(UserResponse::fromUser)
                .doOnSuccess(user -> log.info("User profile updated: {}", user.username()))
                .doOnError(error -> log.error("Error updating user profile {}: {}", command.userId(), error.getMessage()));
    }

    @Override
    public Mono<Void> changePassword(ChangePasswordCommand command) {
        log.debug("Changing password for user: {}", command.userId());

        return userRepository.findById(command.userId())
                .switchIfEmpty(Mono.error(new DomainExceptions.EntityNotFoundException("User", command.userId().toString())))
                .filter(user -> passwordEncoder.matches(command.currentPassword(), user.getPasswordHash()))
                .switchIfEmpty(Mono.error(new DomainExceptions.BusinessRuleViolationException(
                        "INVALID_CURRENT_PASSWORD", "Current password is incorrect")))
                .map(user -> user.changePassword(
                        passwordEncoder.encode(command.newPassword()),
                        command.changedBy()))
                .flatMap(userRepository::save)
                .flatMap(this::invalidateUserCache)
                .flatMap(this::publishPasswordChangedEvent)
                .then()
                .doOnSuccess(unused -> log.info("Password changed for user: {}", command.userId()))
                .doOnError(error -> log.error("Error changing password for user {}: {}", command.userId(), error.getMessage()));
    }

    @Override
    public Mono<UserResponse> activateUser(UserId userId, String activatedBy) {
        log.debug("Activating user: {}", userId);

        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new DomainExceptions.EntityNotFoundException("User", userId.toString())))
                .map(user -> user.activate(activatedBy))
                .flatMap(userRepository::save)
                .flatMap(this::invalidateUserCache)
                .flatMap(this::publishUserActivatedEvent)
                .map(UserResponse::fromUser)
                .doOnSuccess(user -> log.info("User activated: {}", user.username()))
                .doOnError(error -> log.error("Error activating user {}: {}", userId, error.getMessage()));
    }

    @Override
    public Mono<UserResponse> deactivateUser(UserId userId, String deactivatedBy, String reason) {
        log.debug("Deactivating user: {}", userId);

        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new DomainExceptions.EntityNotFoundException("User", userId.toString())))
                .map(user -> user.deactivate(deactivatedBy))
                .flatMap(userRepository::save)
                .flatMap(this::invalidateUserCache)
                .flatMap(user -> publishUserDeactivatedEvent(user, deactivatedBy, reason))
                .map(UserResponse::fromUser)
                .doOnSuccess(user -> log.info("User deactivated: {}", user.username()))
                .doOnError(error -> log.error("Error deactivating user {}: {}", userId, error.getMessage()));
    }

    @Override
    public Mono<UserResponse> assignRoles(UserId userId, Set<String> roleNames, String assignedBy) {
        log.debug("Assigning roles {} to user: {}", roleNames, userId);

        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new DomainExceptions.EntityNotFoundException("User", userId.toString())))
                // En una implementación real, aquí cargarías y asignarías los roles
                .flatMap(userRepository::save)
                .flatMap(this::invalidateUserCache)
                .flatMap(user -> publishRolesAssignedEvent(user, roleNames, assignedBy))
                .map(UserResponse::fromUser)
                .doOnSuccess(user -> log.info("Roles assigned to user: {}", user.username()))
                .doOnError(error -> log.error("Error assigning roles to user {}: {}", userId, error.getMessage()));
    }

    @Override
    public Mono<UserResponse> removeRoles(UserId userId, Set<String> roleNames, String removedBy) {
        log.debug("Removing roles {} from user: {}", roleNames, userId);

        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new DomainExceptions.EntityNotFoundException("User", userId.toString())))
                // En una implementación real, aquí removerías los roles
                .flatMap(userRepository::save)
                .flatMap(this::invalidateUserCache)
                .map(UserResponse::fromUser)
                .doOnSuccess(user -> log.info("Roles removed from user: {}", user.username()))
                .doOnError(error -> log.error("Error removing roles from user {}: {}", userId, error.getMessage()));
    }

    @Override
    public Flux<UserResponse> getAllUsers(int page, int size) {
        log.debug("Getting all users with pagination: page={}, size={}", page, size);

        return userRepository.findAllWithPagination(page, size)
                .map(UserResponse::fromUser)
                .doOnNext(user -> log.debug("User in list: {}", user.username()))
                .doOnError(error -> log.error("Error getting users list: {}", error.getMessage()));
    }

    @Override
    public Flux<UserResponse> searchUsers(UserSearchCriteria criteria) {
        log.debug("Searching users with criteria: {}", criteria);

        return userRepository.findByNameOrEmailPattern(
                        criteria.namePattern() != null ? criteria.namePattern() :
                                criteria.emailPattern() != null ? criteria.emailPattern() :
                                        criteria.usernamePattern() != null ? criteria.usernamePattern() : "")
                .filter(user -> matchesCriteria(user, criteria))
                .skip(criteria.page() * criteria.size())
                .take(criteria.size())
                .map(UserResponse::fromUser)
                .doOnNext(user -> log.debug("User matches search: {}", user.username()))
                .doOnError(error -> log.error("Error searching users: {}", error.getMessage()));
    }

    @Override
    public Mono<Void> deleteUser(UserId userId, String deletedBy) {
        log.debug("Deleting user: {}", userId);

        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new DomainExceptions.EntityNotFoundException("User", userId.toString())))
                .flatMap(user -> {
                    // Soft delete - just deactivate
                    User deactivatedUser = user.deactivate(deletedBy);
                    return userRepository.save(deactivatedUser);
                })
                .flatMap(this::invalidateUserCache)
                .then()
                .doOnSuccess(unused -> log.info("User deleted (deactivated): {}", userId))
                .doOnError(error -> log.error("Error deleting user {}: {}", userId, error.getMessage()));
    }

    @Override
    public Mono<UserStatistics> getUserStatistics() {
        log.debug("Getting user statistics");

        return cacheRepository.get(USER_STATS_CACHE_KEY, UserStatistics.class)
                .switchIfEmpty(calculateUserStatistics()
                        .flatMap(stats -> cacheRepository.set(USER_STATS_CACHE_KEY, stats, Duration.ofMinutes(15))
                                .thenReturn(stats)))
                .doOnSuccess(stats -> log.debug("User statistics retrieved: {} total users", stats.totalUsers()))
                .doOnError(error -> log.error("Error getting user statistics: {}", error.getMessage()));
    }

    /**
     * Validar creación de usuario
     */
    private Mono<Void> validateUserCreation(CreateUserCommand command) {
        Username username = Username.of(command.username());
        Email email = Email.of(command.email());

        return userRepository.existsByUsername(username)
                .filter(exists -> !exists)
                .switchIfEmpty(Mono.error(new DomainExceptions.EntityAlreadyExistsException(
                        "User", "username", command.username())))
                .then(userRepository.existsByEmail(email))
                .filter(exists -> !exists)
                .switchIfEmpty(Mono.error(new DomainExceptions.EntityAlreadyExistsException(
                        "User", "email", command.email())))
                .then();
    }

    /**
     * Crear entidad de usuario
     */
    private Mono<User> createUserEntity(CreateUserCommand command) {
        return Mono.fromCallable(() -> {
            String hashedPassword = passwordEncoder.encode(command.password());

            return User.createNew(
                            Username.of(command.username()),
                            Email.of(command.email()),
                            hashedPassword)
                    .withFirstName(command.firstName())
                    .withLastName(command.lastName())
                    .withCreatedBy(command.createdBy())
                    .withUpdatedBy(command.createdBy());
        });
    }

    /**
     * Cachear usuario
     */
    private Mono<User> cacheUser(User user) {
        if (user.getId() == null) {
            return Mono.just(user);
        }

        String cacheKey = USER_CACHE_PREFIX + user.getId();
        return cacheRepository.set(cacheKey, user, USER_CACHE_TTL)
                .thenReturn(user);
    }

    /**
     * Invalidar cache de usuario
     */
    private Mono<User> invalidateUserCache(User user) {
        String cacheKey = USER_CACHE_PREFIX + user.getId();
        return cacheRepository.delete(cacheKey)
                .then(cacheRepository.delete(USER_STATS_CACHE_KEY))
                .thenReturn(user);
    }

    /**
     * Verificar si usuario coincide con criterios de búsqueda
     */
    private boolean matchesCriteria(User user, UserSearchCriteria criteria) {
        if (criteria.isActive() != null && !criteria.isActive().equals(user.isActive())) {
            return false;
        }

        if (criteria.roles() != null && !criteria.roles().isEmpty()) {
            return criteria.roles().stream().anyMatch(user::hasRole);
        }

        return true;
    }

    /**
     * Calcular estadísticas de usuarios
     */
    private Mono<UserStatistics> calculateUserStatistics() {
        return Mono.zip(
                userRepository.countActiveUsers(),
                userRepository.countByRole("ROLE_ADMIN"),
                userRepository.countByRole("ROLE_USER"),
                userRepository.countByRole("ROLE_MODERATOR")
        ).map(tuple -> {
            long activeUsers = tuple.getT1();
            long admins = tuple.getT2();
            long users = tuple.getT3();
            long moderators = tuple.getT4();

            return new UserStatistics(
                    activeUsers,
                    activeUsers,
                    0L, // inactiveUsers - calcular si es necesario
                    0L, // lockedUsers - calcular si es necesario
                    java.util.Map.of(
                            "ROLE_ADMIN", admins,
                            "ROLE_USER", users,
                            "ROLE_MODERATOR", moderators
                    ),
                    LocalDateTime.now(),
                    0.0 // averageLoginFrequency - calcular si es necesario
            );
        });
    }

    /**
     * Eventos de dominio
     */
    private Mono<User> publishUserCreatedEvent(User user) {
        UserEvents.UserCreated event = UserEvents.UserCreated.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .email(user.getEmail())
                .createdBy(user.getCreatedBy())
                .build();

        return eventPublisher.publish(event).thenReturn(user);
    }

    private Mono<User> publishUserProfileUpdatedEvent(User user) {
        UserEvents.UserProfileUpdated event = UserEvents.UserProfileUpdated.builder()
                .userId(user.getUserId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .profilePictureUrl(user.getProfilePictureUrl())
                .updatedBy(user.getUpdatedBy())
                .build();

        return eventPublisher.publish(event).thenReturn(user);
    }

    private Mono<User> publishPasswordChangedEvent(User user) {
        UserEvents.UserPasswordChanged event = UserEvents.UserPasswordChanged.builder()
                .userId(user.getUserId())
                .changedBy(user.getUpdatedBy())
                .reason("password_change")
                .build();

        return eventPublisher.publish(event).thenReturn(user);
    }

    private Mono<User> publishUserActivatedEvent(User user) {
        UserEvents.UserActivated event = UserEvents.UserActivated.builder()
                .userId(user.getUserId())
                .activatedBy(user.getUpdatedBy())
                .reason("manual_activation")
                .build();

        return eventPublisher.publish(event).thenReturn(user);
    }

    private Mono<User> publishUserDeactivatedEvent(User user, String deactivatedBy, String reason) {
        UserEvents.UserDeactivated event = UserEvents.UserDeactivated.builder()
                .userId(user.getUserId())
                .deactivatedBy(deactivatedBy)
                .reason(reason)
                .build();

        return eventPublisher.publish(event).thenReturn(user);
    }

    private Mono<User> publishRolesAssignedEvent(User user, Set<String> roleNames, String assignedBy) {
        UserEvents.UserRolesAssigned event = UserEvents.UserRolesAssigned.builder()
                .userId(user.getUserId())
                .roleNames(roleNames)
                .assignedBy(assignedBy)
                .build();

        return eventPublisher.publish(event).thenReturn(user);
    }
}
