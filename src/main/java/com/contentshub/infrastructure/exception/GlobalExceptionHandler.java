package com.contentshub.infrastructure.exception;

import com.contentshub.application.port.input.AuthenticationUseCase;
import com.contentshub.application.port.input.DocumentManagementUseCase;
import com.contentshub.application.port.input.UserManagementUseCase;
import com.contentshub.domain.exception.DomainExceptions;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Manejador global de excepciones para la aplicación
 * Convierte excepciones de dominio y técnicas en respuestas HTTP apropiadas
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Manejo de excepciones de dominio - Entity Not Found
     */
    @ExceptionHandler(DomainExceptions.EntityNotFoundException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleEntityNotFound(DomainExceptions.EntityNotFoundException ex) {
        log.warn("Entity not found: {}", ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.NOT_FOUND.value())
                .error("Entity Not Found")
                .message(ex.getMessage())
                .code(ex.getErrorCode())
                .details(ex.getErrorData())
                .build();

        return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse));
    }

    /**
     * Manejo de excepciones de dominio - Entity Already Exists
     */
    @ExceptionHandler(DomainExceptions.EntityAlreadyExistsException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleEntityAlreadyExists(DomainExceptions.EntityAlreadyExistsException ex) {
        log.warn("Entity already exists: {}", ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.CONFLICT.value())
                .error("Entity Already Exists")
                .message(ex.getMessage())
                .code(ex.getErrorCode())
                .details(ex.getErrorData())
                .build();

        return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse));
    }

    /**
     * Manejo de excepciones de dominio - Business Rule Violation
     */
    @ExceptionHandler(DomainExceptions.BusinessRuleViolationException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleBusinessRuleViolation(DomainExceptions.BusinessRuleViolationException ex) {
        log.warn("Business rule violation: {}", ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Business Rule Violation")
                .message(ex.getMessage())
                .code(ex.getErrorCode())
                .details(ex.getErrorData())
                .build();

        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse));
    }

    /**
     * Manejo de excepciones de dominio - Insufficient Permissions
     */
    @ExceptionHandler(DomainExceptions.InsufficientPermissionsException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleInsufficientPermissions(DomainExceptions.InsufficientPermissionsException ex) {
        log.warn("Insufficient permissions: {}", ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.FORBIDDEN.value())
                .error("Insufficient Permissions")
                .message(ex.getMessage())
                .code(ex.getErrorCode())
                .details(ex.getErrorData())
                .build();

        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse));
    }

    /**
     * Manejo de excepciones de dominio - Invalid State Transition
     */
    @ExceptionHandler(DomainExceptions.InvalidStateTransitionException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleInvalidStateTransition(DomainExceptions.InvalidStateTransitionException ex) {
        log.warn("Invalid state transition: {}", ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Invalid State Transition")
                .message(ex.getMessage())
                .code(ex.getErrorCode())
                .details(ex.getErrorData())
                .build();

        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse));
    }

    /**
     * Manejo de excepciones de dominio - Limit Exceeded
     */
    @ExceptionHandler(DomainExceptions.LimitExceededException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleLimitExceeded(DomainExceptions.LimitExceededException ex) {
        log.warn("Limit exceeded: {}", ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.TOO_MANY_REQUESTS.value())
                .error("Limit Exceeded")
                .message(ex.getMessage())
                .code(ex.getErrorCode())
                .details(ex.getErrorData())
                .build();

        return Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(errorResponse));
    }

    /**
     * Manejo de excepciones de autenticación - Invalid Credentials
     */
    @ExceptionHandler(AuthenticationUseCase.InvalidCredentialsException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleInvalidCredentials(AuthenticationUseCase.InvalidCredentialsException ex) {
        log.warn("Invalid credentials: {}", ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.UNAUTHORIZED.value())
                .error("Invalid Credentials")
                .message("Username or password is incorrect")
                .code("INVALID_CREDENTIALS")
                .build();

        return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse));
    }

    /**
     * Manejo de excepciones de autenticación - Account Locked
     */
    @ExceptionHandler(AuthenticationUseCase.AccountLockedException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleAccountLocked(AuthenticationUseCase.AccountLockedException ex) {
        log.warn("Account locked: {}", ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.LOCKED.value())
                .error("Account Locked")
                .message("Account is temporarily locked due to multiple failed login attempts")
                .code("ACCOUNT_LOCKED")
                .build();

        return Mono.just(ResponseEntity.status(HttpStatus.LOCKED).body(errorResponse));
    }

    /**
     * Manejo de excepciones de autenticación - Account Disabled
     */
    @ExceptionHandler(AuthenticationUseCase.AccountDisabledException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleAccountDisabled(AuthenticationUseCase.AccountDisabledException ex) {
        log.warn("Account disabled: {}", ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.FORBIDDEN.value())
                .error("Account Disabled")
                .message("Account is disabled")
                .code("ACCOUNT_DISABLED")
                .build();

        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse));
    }

    /**
     * Manejo de excepciones de autenticación - Invalid Token
     */
    @ExceptionHandler(AuthenticationUseCase.InvalidTokenException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleInvalidToken(AuthenticationUseCase.InvalidTokenException ex) {
        log.warn("Invalid token: {}", ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.UNAUTHORIZED.value())
                .error("Invalid Token")
                .message(ex.getMessage())
                .code("INVALID_TOKEN")
                .build();

        return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse));
    }

    /**
     * Manejo de excepciones de autenticación - Token Expired
     */
    @ExceptionHandler(AuthenticationUseCase.TokenExpiredException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleTokenExpired(AuthenticationUseCase.TokenExpiredException ex) {
        log.warn("Token expired: {}", ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.UNAUTHORIZED.value())
                .error("Token Expired")
                .message("Access token has expired")
                .code("TOKEN_EXPIRED")
                .build();

        return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse));
    }

    /**
     * Manejo de excepciones de documentos - Document Not Found
     */
    @ExceptionHandler(DocumentManagementUseCase.DocumentNotFoundException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleDocumentNotFound(DocumentManagementUseCase.DocumentNotFoundException ex) {
        log.warn("Document not found: {}", ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.NOT_FOUND.value())
                .error("Document Not Found")
                .message(ex.getMessage())
                .code("DOCUMENT_NOT_FOUND")
                .build();

        return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse));
    }

    /**
     * Manejo de excepciones de documentos - Document Access Denied
     */
    @ExceptionHandler(DocumentManagementUseCase.DocumentAccessDeniedException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleDocumentAccessDenied(DocumentManagementUseCase.DocumentAccessDeniedException ex) {
        log.warn("Document access denied: {}", ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.FORBIDDEN.value())
                .error("Document Access Denied")
                .message("You don't have permission to access this document")
                .code("DOCUMENT_ACCESS_DENIED")
                .build();

        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse));
    }

    /**
     * Manejo de excepciones de documentos - Document Already Published
     */
    @ExceptionHandler(DocumentManagementUseCase.DocumentAlreadyPublishedException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleDocumentAlreadyPublished(DocumentManagementUseCase.DocumentAlreadyPublishedException ex) {
        log.warn("Document already published: {}", ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.CONFLICT.value())
                .error("Document Already Published")
                .message(ex.getMessage())
                .code("DOCUMENT_ALREADY_PUBLISHED")
                .build();

        return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse));
    }

    /**
     * Manejo de excepciones de validación - Method Argument Not Valid
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleValidationErrors(MethodArgumentNotValidException ex) {
        log.warn("Validation error: {}", ex.getMessage());

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Validation Failed")
                .message("Invalid input parameters")
                .code("VALIDATION_ERROR")
                .details(Map.of("fieldErrors", errors))
                .build();

        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse));
    }

    /**
     * Manejo de excepciones de validación - WebExchange Bind Exception
     */
    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleWebExchangeBindException(WebExchangeBindException ex) {
        log.warn("Web exchange bind error: {}", ex.getMessage());

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> {
            errors.put(error.getField(), error.getDefaultMessage());
        });

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Validation Failed")
                .message("Invalid input parameters")
                .code("VALIDATION_ERROR")
                .details(Map.of("fieldErrors", errors))
                .build();

        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse));
    }

    /**
     * Manejo de excepciones de seguridad - Access Denied
     */
    @ExceptionHandler(AccessDeniedException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.FORBIDDEN.value())
                .error("Access Denied")
                .message("You don't have permission to access this resource")
                .code("ACCESS_DENIED")
                .build();

        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse));
    }

    /**
     * Manejo de excepciones de seguridad - Bad Credentials
     */
    @ExceptionHandler(BadCredentialsException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleBadCredentials(BadCredentialsException ex) {
        log.warn("Bad credentials: {}", ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.UNAUTHORIZED.value())
                .error("Authentication Failed")
                .message("Invalid credentials provided")
                .code("BAD_CREDENTIALS")
                .build();

        return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse));
    }

    /**
     * Manejo de excepciones de entrada web - Server Web Input Exception
     */
    @ExceptionHandler(ServerWebInputException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleServerWebInputException(ServerWebInputException ex) {
        log.warn("Server web input error: {}", ex.getMessage());

        String message = "Invalid request format";
        if (ex.getCause() instanceof InvalidFormatException invalidFormatEx) {
            message = String.format("Invalid value '%s' for field '%s'",
                    invalidFormatEx.getValue(),
                    invalidFormatEx.getPath().stream()
                            .map(ref -> ref.getFieldName())
                            .collect(Collectors.joining(".")));
        }

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Invalid Input")
                .message(message)
                .code("INVALID_INPUT")
                .build();

        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse));
    }

    /**
     * Manejo de excepciones de integridad de datos
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.error("Data integrity violation: {}", ex.getMessage());

        String message = "Data integrity constraint violation";
        String code = "DATA_INTEGRITY_VIOLATION";

        // Detectar tipos específicos de violación
        if (ex.getMessage() != null) {
            if (ex.getMessage().contains("unique") || ex.getMessage().contains("duplicate")) {
                message = "Resource already exists";
                code = "DUPLICATE_RESOURCE";
            } else if (ex.getMessage().contains("foreign key") || ex.getMessage().contains("constraint")) {
                message = "Referenced resource not found";
                code = "FOREIGN_KEY_VIOLATION";
            }
        }

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.CONFLICT.value())
                .error("Data Integrity Violation")
                .message(message)
                .code(code)
                .build();

        return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse));
    }

    /**
     * Manejo de excepciones con estado HTTP específico
     */
    @ExceptionHandler(ResponseStatusException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleResponseStatusException(ResponseStatusException ex) {
        log.warn("Response status exception: {} - {}", ex.getStatusCode(), ex.getReason());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(ex.getStatusCode().value())
                .error(ex.getStatusCode().getReasonPhrase())
                .message(ex.getReason() != null ? ex.getReason() : "Request processing failed")
                .code("HTTP_" + ex.getStatusCode().value())
                .build();

        return Mono.just(ResponseEntity.status(ex.getStatusCode()).body(errorResponse));
    }

    /**
     * Manejo de excepciones de valor ilegal
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Invalid Argument")
                .message(ex.getMessage())
                .code("ILLEGAL_ARGUMENT")
                .build();

        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse));
    }

    /**
     * Manejo de excepciones de estado ilegal
     */
    @ExceptionHandler(IllegalStateException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleIllegalStateException(IllegalStateException ex) {
        log.warn("Illegal state: {}", ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.CONFLICT.value())
                .error("Invalid State")
                .message(ex.getMessage())
                .code("ILLEGAL_STATE")
                .build();

        return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse));
    }

    /**
     * Manejo de excepciones genéricas
     */
    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ErrorResponse>> handleGenericException(Exception ex) {
        log.error("Unexpected error occurred", ex);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Internal Server Error")
                .message("An unexpected error occurred. Please try again later.")
                .code("INTERNAL_ERROR")
                .build();

        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse));
    }

    /**
     * DTO para respuestas de error estandarizadas
     */
    @lombok.Builder
    @lombok.Data
    public static class ErrorResponse {
        private LocalDateTime timestamp;
        private int status;
        private String error;
        private String message;
        private String code;
        private Map<String, Object> details;
        private String path;
        private String traceId;

        /**
         * Factory method para crear una respuesta de error simple
         */
        public static ErrorResponse simple(HttpStatus status, String message, String code) {
            return ErrorResponse.builder()
                    .timestamp(LocalDateTime.now())
                    .status(status.value())
                    .error(status.getReasonPhrase())
                    .message(message)
                    .code(code)
                    .build();
        }

        /**
         * Factory method para crear una respuesta de error con detalles
         */
        public static ErrorResponse withDetails(HttpStatus status, String message, String code, Map<String, Object> details) {
            return ErrorResponse.builder()
                    .timestamp(LocalDateTime.now())
                    .status(status.value())
                    .error(status.getReasonPhrase())
                    .message(message)
                    .code(code)
                    .details(details)
                    .build();
        }
    }
}
