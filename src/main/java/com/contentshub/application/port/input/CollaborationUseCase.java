package com.contentshub.application.port.input;

import com.contentshub.domain.valueobject.UserId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Puerto de entrada para casos de uso de colaboración en tiempo real
 */
public interface CollaborationUseCase {

    /**
     * Iniciar sesión de colaboración
     */
    Mono<CollaborationSessionResponse> startSession(StartSessionCommand command);

    /**
     * Finalizar sesión de colaboración
     */
    Mono<Void> endSession(String sessionId, UserId userId);

    /**
     * Obtener sesiones activas de un documento
     */
    Flux<ActiveCollaboratorResponse> getActiveSessions(String documentId, UserId requestingUserId);

    /**
     * Enviar cambio de contenido en tiempo real
     */
    Mono<Void> sendContentChange(ContentChangeCommand command);

    /**
     * Enviar posición del cursor
     */
    Mono<Void> sendCursorPosition(CursorPositionCommand command);

    /**
     * Enviar selección de texto
     */
    Mono<Void> sendTextSelection(TextSelectionCommand command);

    /**
     * Obtener stream de cambios en tiempo real
     */
    Flux<CollaborationEvent> getCollaborationStream(String documentId, UserId userId);

    /**
     * Enviar comentario en tiempo real
     */
    Mono<CommentResponse> addComment(AddCommentCommand command);

    /**
     * Responder a comentario
     */
    Mono<CommentResponse> replyToComment(ReplyCommentCommand command);

    /**
     * Resolver comentario
     */
    Mono<CommentResponse> resolveComment(String commentId, UserId userId);

    /**
     * Obtener comentarios del documento
     */
    Flux<CommentResponse> getDocumentComments(String documentId, UserId requestingUserId);

    /**
     * Enviar typing indicator
     */
    Mono<Void> sendTypingIndicator(TypingIndicatorCommand command);

    /**
     * Obtener presencia de colaboradores
     */
    Flux<CollaboratorPresence> getCollaboratorPresence(String documentId, UserId requestingUserId);

    /**
     * Bloquear sección del documento para edición exclusiva
     */
    Mono<SectionLockResponse> lockSection(LockSectionCommand command);

    /**
     * Desbloquear sección del documento
     */
    Mono<Void> unlockSection(String lockId, UserId userId);

    /**
     * Obtener locks activos del documento
     */
    Flux<SectionLockResponse> getActiveLocks(String documentId, UserId requestingUserId);

    /**
     * Comando para iniciar sesión
     */
    record StartSessionCommand(
            String documentId,
            UserId userId,
            String sessionId,
            String userAgent,
            String ipAddress
    ) {}

    /**
     * Comando para cambio de contenido
     */
    record ContentChangeCommand(
            String documentId,
            String sessionId,
            UserId userId,
            String operation,
            int position,
            String content,
            String changeId
    ) {}

    /**
     * Comando para posición del cursor
     */
    record CursorPositionCommand(
            String documentId,
            String sessionId,
            UserId userId,
            int position,
            String selection
    ) {}

    /**
     * Comando para selección de texto
     */
    record TextSelectionCommand(
            String documentId,
            String sessionId,
            UserId userId,
            int startPosition,
            int endPosition,
            String selectedText
    ) {}

    /**
     * Comando para agregar comentario
     */
    record AddCommentCommand(
            String documentId,
            UserId authorId,
            String content,
            CommentPosition position,
            String sessionId
    ) {}

    /**
     * Comando para responder comentario
     */
    record ReplyCommentCommand(
            String parentCommentId,
            UserId authorId,
            String content
    ) {}

    /**
     * Comando para typing indicator
     */
    record TypingIndicatorCommand(
            String documentId,
            String sessionId,
            UserId userId,
            boolean isTyping,
            int position
    ) {}

    /**
     * Comando para bloquear sección
     */
    record LockSectionCommand(
            String documentId,
            UserId userId,
            String sessionId,
            int startPosition,
            int endPosition,
            String lockReason
    ) {}

    /**
     * Respuesta de sesión de colaboración
     */
    record CollaborationSessionResponse(
            String sessionId,
            String documentId,
            UserId userId,
            String userName,
            LocalDateTime startedAt,
            String status
    ) {}

    /**
     * Respuesta de colaborador activo
     */
    record ActiveCollaboratorResponse(
            String sessionId,
            UserId userId,
            String userName,
            String userAvatar,
            LocalDateTime joinedAt,
            LocalDateTime lastActivity,
            CollaboratorStatus status,
            CursorInfo cursorInfo
    ) {}

    /**
     * Evento de colaboración
     */
    record CollaborationEvent(
            String eventId,
            String eventType,
            String documentId,
            String sessionId,
            UserId userId,
            String userName,
            Map<String, Object> eventData,
            LocalDateTime timestamp
    ) {
        public static CollaborationEvent contentChange(String documentId, String sessionId,
                                                       UserId userId, String userName,
                                                       String operation, int position, String content) {
            return new CollaborationEvent(
                    java.util.UUID.randomUUID().toString(),
                    "CONTENT_CHANGE",
                    documentId,
                    sessionId,
                    userId,
                    userName,
                    Map.of(
                            "operation", operation,
                            "position", position,
                            "content", content
                    ),
                    LocalDateTime.now()
            );
        }

        public static CollaborationEvent cursorMove(String documentId, String sessionId,
                                                    UserId userId, String userName,
                                                    int position, String selection) {
            return new CollaborationEvent(
                    java.util.UUID.randomUUID().toString(),
                    "CURSOR_MOVE",
                    documentId,
                    sessionId,
                    userId,
                    userName,
                    Map.of(
                            "position", position,
                            "selection", selection != null ? selection : ""
                    ),
                    LocalDateTime.now()
            );
        }

        public static CollaborationEvent userJoined(String documentId, String sessionId,
                                                    UserId userId, String userName) {
            return new CollaborationEvent(
                    java.util.UUID.randomUUID().toString(),
                    "USER_JOINED",
                    documentId,
                    sessionId,
                    userId,
                    userName,
                    Map.of(),
                    LocalDateTime.now()
            );
        }

        public static CollaborationEvent userLeft(String documentId, String sessionId,
                                                  UserId userId, String userName) {
            return new CollaborationEvent(
                    java.util.UUID.randomUUID().toString(),
                    "USER_LEFT",
                    documentId,
                    sessionId,
                    userId,
                    userName,
                    Map.of(),
                    LocalDateTime.now()
            );
        }
    }

    /**
     * Respuesta de comentario
     */
    record CommentResponse(
            String commentId,
            String documentId,
            UserId authorId,
            String authorName,
            String authorAvatar,
            String content,
            CommentPosition position,
            String parentCommentId,
            boolean isResolved,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            java.util.List<CommentResponse> replies
    ) {}

    /**
     * Posición de comentario en el documento
     */
    record CommentPosition(
            int startPosition,
            int endPosition,
            String contextText
    ) {}

    /**
     * Información del cursor
     */
    record CursorInfo(
            int position,
            String selection,
            LocalDateTime lastUpdate
    ) {}

    /**
     * Presencia de colaborador
     */
    record CollaboratorPresence(
            UserId userId,
            String userName,
            String userAvatar,
            CollaboratorStatus status,
            CursorInfo cursorInfo,
            boolean isTyping,
            LocalDateTime lastSeen
    ) {}

    /**
     * Estado del colaborador
     */
    enum CollaboratorStatus {
        ACTIVE,
        IDLE,
        TYPING,
        AWAY,
        OFFLINE
    }

    /**
     * Respuesta de bloqueo de sección
     */
    record SectionLockResponse(
            String lockId,
            String documentId,
            UserId userId,
            String userName,
            int startPosition,
            int endPosition,
            String lockReason,
            LocalDateTime lockedAt,
            LocalDateTime expiresAt
    ) {}

    /**
     * Excepciones de colaboración
     */
    class CollaborationException extends RuntimeException {
        public CollaborationException(String message) {
            super(message);
        }
    }

    class SessionNotFoundException extends CollaborationException {
        public SessionNotFoundException(String sessionId) {
            super("Collaboration session not found: " + sessionId);
        }
    }

    class DocumentLockedException extends CollaborationException {
        public DocumentLockedException(String documentId, String section) {
            super("Document section is locked: " + documentId + " section: " + section);
        }
    }

    class InvalidCollaborationOperationException extends CollaborationException {
        public InvalidCollaborationOperationException(String operation, String reason) {
            super("Invalid collaboration operation '" + operation + "': " + reason);
        }
    }
}
