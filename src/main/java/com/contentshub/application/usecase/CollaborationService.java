package com.contentshub.application.usecase;

import com.contentshub.application.port.input.CollaborationUseCase;
import com.contentshub.application.port.output.CacheRepositoryPort;
import com.contentshub.application.port.output.DocumentRepositoryPort;
import com.contentshub.application.port.output.EventPublisherPort;
import com.contentshub.application.port.output.UserRepositoryPort;
import com.contentshub.domain.valueobject.UserId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementación de casos de uso para colaboración en tiempo real
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CollaborationService implements CollaborationUseCase {

    private final DocumentRepositoryPort documentRepository;
    private final CacheRepositoryPort cacheRepository;

    // Cache keys
    private static final String SESSION_PREFIX = "collaboration_session:";
    private static final String DOCUMENT_SESSIONS_PREFIX = "document_sessions:";
    private static final String USER_PRESENCE_PREFIX = "user_presence:";
    private static final String SECTION_LOCK_PREFIX = "section_lock:";
    private static final String COMMENT_PREFIX = "comment:";

    // Streams para eventos en tiempo real
    private final Map<String, Sinks.Many<CollaborationEvent>> documentStreams = new ConcurrentHashMap<>();

    @Override
    public Mono<CollaborationSessionResponse> startSession(StartSessionCommand command) {
        log.debug("Starting collaboration session for user {} on document {}",
                command.userId(), command.documentId());

        return validateDocumentAccess(command.documentId(), command.userId())
                .then(createSession(command))
                .flatMap(this::storeSession)
                .flatMap(this::addToDocumentSessions)
                .flatMap(this::updateUserPresence)
                .flatMap(this::publishSessionStartedEvent)
                .doOnSuccess(response -> log.info("Collaboration session started: {}", response.sessionId()))
                .doOnError(error -> log.error("Error starting collaboration session: {}", error.getMessage()));
    }

    @Override
    public Mono<Void> endSession(String sessionId, UserId userId) {
        log.debug("Ending collaboration session: {}", sessionId);

        return getSession(sessionId)
                .flatMap(session -> removeFromDocumentSessions(session)
                        .then(removeUserPresence(session.documentId(), userId))
                        .then(publishSessionEndedEvent(session)))
                .then(removeSession(sessionId))
                .doOnSuccess(unused -> log.info("Collaboration session ended: {}", sessionId))
                .doOnError(error -> log.error("Error ending collaboration session: {}", error.getMessage()));
    }

    @Override
    public Flux<ActiveCollaboratorResponse> getActiveSessions(String documentId, UserId requestingUserId) {
        log.debug("Getting active collaborators for document: {}", documentId);

        return validateDocumentAccess(documentId, requestingUserId)
                .thenMany(getDocumentSessions(documentId))
                .flatMap(this::getSession)
                .flatMap(this::toActiveCollaboratorResponse)
                .doOnNext(collaborator -> log.debug("Active collaborator: {}", collaborator.userName()))
                .doOnError(error -> log.error("Error getting active sessions: {}", error.getMessage()));
    }

    @Override
    public Mono<Void> sendContentChange(ContentChangeCommand command) {
        log.debug("Processing content change for document: {}", command.documentId());

        return validateSessionAccess(command.sessionId(), command.userId())
                .then(storeContentChange(command))
                .then(publishContentChangeEvent(command))
                .doOnSuccess(unused -> log.debug("Content change processed"))
                .doOnError(error -> log.error("Error processing content change: {}", error.getMessage()));
    }

    @Override
    public Mono<Void> sendCursorPosition(CursorPositionCommand command) {
        log.debug("Processing cursor position for document: {}", command.documentId());

        return validateSessionAccess(command.sessionId(), command.userId())
                .then(updateCursorPosition(command))
                .then(publishCursorPositionEvent(command))
                .doOnSuccess(unused -> log.debug("Cursor position updated"))
                .doOnError(error -> log.error("Error updating cursor position: {}", error.getMessage()));
    }

    @Override
    public Mono<Void> sendTextSelection(TextSelectionCommand command) {
        log.debug("Processing text selection for document: {}", command.documentId());

        return validateSessionAccess(command.sessionId(), command.userId())
                .then(publishTextSelectionEvent(command))
                .doOnSuccess(unused -> log.debug("Text selection processed"))
                .doOnError(error -> log.error("Error processing text selection: {}", error.getMessage()));
    }

    @Override
    public Flux<CollaborationEvent> getCollaborationStream(String documentId, UserId userId) {
        log.debug("Creating collaboration stream for user {} on document {}", userId, documentId);

        return validateDocumentAccess(documentId, userId)
                .thenMany(getOrCreateDocumentStream(documentId))
                .doOnSubscribe(subscription -> log.debug("User {} subscribed to document stream {}", userId, documentId))
                .doOnCancel(() -> log.debug("User {} unsubscribed from document stream {}", userId, documentId))
                .doOnError(error -> log.error("Error in collaboration stream: {}", error.getMessage()));
    }

    @Override
    public Mono<CommentResponse> addComment(AddCommentCommand command) {
        log.debug("Adding comment to document: {}", command.documentId());

        return validateDocumentAccess(command.documentId(), command.authorId())
                .then(createComment(command))
                .flatMap(this::storeComment)
                .flatMap(this::publishCommentAddedEvent)
                .doOnSuccess(comment -> log.info("Comment added: {}", comment.commentId()))
                .doOnError(error -> log.error("Error adding comment: {}", error.getMessage()));
    }

    @Override
    public Mono<CommentResponse> replyToComment(ReplyCommentCommand command) {
        log.debug("Replying to comment: {}", command.parentCommentId());

        return getComment(command.parentCommentId())
                .flatMap(parentComment -> createReplyComment(command, parentComment.documentId()))
                .flatMap(this::storeComment)
                .flatMap(this::publishCommentAddedEvent)
                .doOnSuccess(reply -> log.info("Comment reply added: {}", reply.commentId()))
                .doOnError(error -> log.error("Error replying to comment: {}", error.getMessage()));
    }

    @Override
    public Mono<CommentResponse> resolveComment(String commentId, UserId userId) {
        log.debug("Resolving comment: {}", commentId);

        return getComment(commentId)
                .flatMap(comment -> validateDocumentAccess(comment.documentId(), userId)
                        .thenReturn(comment))
                .map(comment -> new CommentResponse(
                        comment.commentId(),
                        comment.documentId(),
                        comment.authorId(),
                        comment.authorName(),
                        comment.authorAvatar(),
                        comment.content(),
                        comment.position(),
                        comment.parentCommentId(),
                        true, // isResolved = true
                        comment.createdAt(),
                        LocalDateTime.now(), // updatedAt
                        comment.replies()
                ))
                .flatMap(this::storeComment)
                .doOnSuccess(comment -> log.info("Comment resolved: {}", comment.commentId()))
                .doOnError(error -> log.error("Error resolving comment: {}", error.getMessage()));
    }

    @Override
    public Flux<CommentResponse> getDocumentComments(String documentId, UserId requestingUserId) {
        log.debug("Getting comments for document: {}", documentId);

        return validateDocumentAccess(documentId, requestingUserId)
                .thenMany(getCommentsForDocument(documentId))
                .doOnNext(comment -> log.debug("Document comment: {}", comment.commentId()))
                .doOnError(error -> log.error("Error getting document comments: {}", error.getMessage()));
    }

    @Override
    public Mono<Void> sendTypingIndicator(TypingIndicatorCommand command) {
        log.debug("Processing typing indicator for document: {}", command.documentId());

        return validateSessionAccess(command.sessionId(), command.userId())
                .then(updateTypingStatus(command))
                .then(publishTypingIndicatorEvent(command))
                .doOnSuccess(unused -> log.debug("Typing indicator processed"))
                .doOnError(error -> log.error("Error processing typing indicator: {}", error.getMessage()));
    }

    @Override
    public Flux<CollaboratorPresence> getCollaboratorPresence(String documentId, UserId requestingUserId) {
        log.debug("Getting collaborator presence for document: {}", documentId);

        return validateDocumentAccess(documentId, requestingUserId)
                .thenMany(getDocumentPresence(documentId))
                .doOnNext(presence -> log.debug("Collaborator presence: {}", presence.userName()))
                .doOnError(error -> log.error("Error getting collaborator presence: {}", error.getMessage()));
    }

    @Override
    public Mono<SectionLockResponse> lockSection(LockSectionCommand command) {
        log.debug("Locking section in document: {}", command.documentId());

        return validateDocumentAccess(command.documentId(), command.userId())
                .then(checkSectionAvailable(command))
                .then(createSectionLock(command))
                .flatMap(this::storeSectionLock)
                .doOnSuccess(lock -> log.info("Section locked: {}", lock.lockId()))
                .doOnError(error -> log.error("Error locking section: {}", error.getMessage()));
    }

    @Override
    public Mono<Void> unlockSection(String lockId, UserId userId) {
        log.debug("Unlocking section: {}", lockId);

        return getSectionLock(lockId)
                .filter(lock -> lock.userId().equals(userId))
                .switchIfEmpty(Mono.error(new SecurityException("Not authorized to unlock this section")))
                .then(removeSectionLock(lockId))
                .doOnSuccess(unused -> log.info("Section unlocked: {}", lockId))
                .doOnError(error -> log.error("Error unlocking section: {}", error.getMessage()));
    }

    @Override
    public Flux<SectionLockResponse> getActiveLocks(String documentId, UserId requestingUserId) {
        log.debug("Getting active locks for document: {}", documentId);

        return validateDocumentAccess(documentId, requestingUserId)
                .thenMany(getDocumentLocks(documentId))
                .doOnNext(lock -> log.debug("Active lock: {}", lock.lockId()))
                .doOnError(error -> log.error("Error getting active locks: {}", error.getMessage()));
    }

    /**
     * Métodos auxiliares privados
     */

    private Mono<Void> validateDocumentAccess(String documentId, UserId userId) {
        return documentRepository.findById(documentId)
                .switchIfEmpty(Mono.error(new DocumentNotFoundException(documentId)))
                .filter(document -> document.canRead(userId))
                .switchIfEmpty(Mono.error(new SecurityException("No access to document")))
                .then();
    }

    private Mono<Void> validateSessionAccess(String sessionId, UserId userId) {
        return getSession(sessionId)
                .filter(session -> session.userId().equals(userId))
                .switchIfEmpty(Mono.error(new SecurityException("Invalid session")))
                .then();
    }

    private Mono<CollaborationSessionResponse> createSession(StartSessionCommand command) {
        return Mono.fromCallable(() -> new CollaborationSessionResponse(
                command.sessionId(),
                command.documentId(),
                command.userId(),
                "", // userName - obtener del repositorio si es necesario
                LocalDateTime.now(),
                "ACTIVE"
        ));
    }

    private Mono<CollaborationSessionResponse> storeSession(CollaborationSessionResponse session) {
        String key = SESSION_PREFIX + session.sessionId();
        return cacheRepository.set(key, session, Duration.ofHours(24))
                .thenReturn(session);
    }

    private Mono<CollaborationSessionResponse> getSession(String sessionId) {
        String key = SESSION_PREFIX + sessionId;
        return cacheRepository.get(key, CollaborationSessionResponse.class)
                .switchIfEmpty(Mono.error(new SessionNotFoundException(sessionId)));
    }

    private Mono<Void> removeSession(String sessionId) {
        String key = SESSION_PREFIX + sessionId;
        return cacheRepository.delete(key);
    }

    private Mono<CollaborationSessionResponse> addToDocumentSessions(CollaborationSessionResponse session) {
        String key = DOCUMENT_SESSIONS_PREFIX + session.documentId();
        return cacheRepository.setAdd(key, session.sessionId())
                .thenReturn(session);
    }

    private Mono<Void> removeFromDocumentSessions(CollaborationSessionResponse session) {
        String key = DOCUMENT_SESSIONS_PREFIX + session.documentId();
        return cacheRepository.setRemove(key, session.sessionId());
    }

    private Flux<String> getDocumentSessions(String documentId) {
        String key = DOCUMENT_SESSIONS_PREFIX + documentId;
        return cacheRepository.setMembers(key, String.class)
                .flatMapMany(Flux::fromIterable);
    }

    private Mono<CollaborationSessionResponse> updateUserPresence(CollaborationSessionResponse session) {
        String key = USER_PRESENCE_PREFIX + session.documentId() + ":" + session.userId();

        CollaboratorPresence presence = new CollaboratorPresence(
                session.userId(),
                session.userName(),
                "", // userAvatar
                CollaboratorStatus.ACTIVE,
                new CursorInfo(0, "", LocalDateTime.now()),
                false,
                LocalDateTime.now()
        );

        return cacheRepository.set(key, presence, Duration.ofMinutes(30))
                .thenReturn(session);
    }

    private Mono<Void> removeUserPresence(String documentId, UserId userId) {
        String key = USER_PRESENCE_PREFIX + documentId + ":" + userId;
        return cacheRepository.delete(key);
    }

    private Flux<CollaboratorPresence> getDocumentPresence(String documentId) {
        String pattern = USER_PRESENCE_PREFIX + documentId + ":*";
        return cacheRepository.findKeysByPattern(pattern)
                .flatMap(key -> cacheRepository.get(key, CollaboratorPresence.class)
                        .cast(CollaboratorPresence.class));
    }

    private Mono<ActiveCollaboratorResponse> toActiveCollaboratorResponse(CollaborationSessionResponse session) {
        return Mono.just(new ActiveCollaboratorResponse(
                session.sessionId(),
                session.userId(),
                session.userName(),
                "", // userAvatar
                session.startedAt(),
                LocalDateTime.now(),
                CollaboratorStatus.ACTIVE,
                new CursorInfo(0, "", LocalDateTime.now())
        ));
    }

    private Flux<CollaborationEvent> getOrCreateDocumentStream(String documentId) {
        return Flux.defer(() -> {
            Sinks.Many<CollaborationEvent> sink = documentStreams.computeIfAbsent(documentId,
                    key -> Sinks.many().multicast().onBackpressureBuffer());
            return sink.asFlux();
        });
    }

    private void publishToDocumentStream(String documentId, CollaborationEvent event) {
        Sinks.Many<CollaborationEvent> sink = documentStreams.get(documentId);
        if (sink != null) {
            sink.tryEmitNext(event);
        }
    }

    private Mono<CollaborationSessionResponse> publishSessionStartedEvent(CollaborationSessionResponse session) {
        CollaborationEvent event = CollaborationEvent.userJoined(
                session.documentId(), session.sessionId(), session.userId(), session.userName());
        publishToDocumentStream(session.documentId(), event);
        return Mono.just(session);
    }

    private Mono<Void> publishSessionEndedEvent(CollaborationSessionResponse session) {
        CollaborationEvent event = CollaborationEvent.userLeft(
                session.documentId(), session.sessionId(), session.userId(), session.userName());
        publishToDocumentStream(session.documentId(), event);
        return Mono.empty();
    }

    private Mono<Void> storeContentChange(ContentChangeCommand command) {
        // Implementar almacenamiento de cambios para versionado
        return Mono.empty();
    }

    private Mono<Void> publishContentChangeEvent(ContentChangeCommand command) {
        CollaborationEvent event = CollaborationEvent.contentChange(
                command.documentId(), command.sessionId(), command.userId(),
                "", command.operation(), command.position(), command.content());
        publishToDocumentStream(command.documentId(), event);
        return Mono.empty();
    }

    private Mono<Void> updateCursorPosition(CursorPositionCommand command) {
        // Actualizar posición del cursor en cache
        return Mono.empty();
    }

    private Mono<Void> publishCursorPositionEvent(CursorPositionCommand command) {
        CollaborationEvent event = CollaborationEvent.cursorMove(
                command.documentId(), command.sessionId(), command.userId(),
                "", command.position(), command.selection());
        publishToDocumentStream(command.documentId(), event);
        return Mono.empty();
    }

    private Mono<Void> publishTextSelectionEvent(TextSelectionCommand command) {
        // Implementar evento de selección de texto
        return Mono.empty();
    }

    private Mono<CommentResponse> createComment(AddCommentCommand command) {
        return Mono.fromCallable(() -> new CommentResponse(
                UUID.randomUUID().toString(),
                command.documentId(),
                command.authorId(),
                "", // authorName - obtener del repositorio
                "", // authorAvatar
                command.content(),
                command.position(),
                null, // parentCommentId
                false,
                LocalDateTime.now(),
                LocalDateTime.now(),
                java.util.List.of()
        ));
    }

    private Mono<CommentResponse> createReplyComment(ReplyCommentCommand command, String documentId) {
        return Mono.fromCallable(() -> new CommentResponse(
                UUID.randomUUID().toString(),
                documentId,
                command.authorId(),
                "", // authorName
                "", // authorAvatar
                command.content(),
                null, // position - las respuestas no tienen posición específica
                command.parentCommentId(),
                false,
                LocalDateTime.now(),
                LocalDateTime.now(),
                java.util.List.of()
        ));
    }

    private Mono<CommentResponse> storeComment(CommentResponse comment) {
        String key = COMMENT_PREFIX + comment.commentId();
        return cacheRepository.set(key, comment, Duration.ofDays(365))
                .thenReturn(comment);
    }

    private Mono<CommentResponse> getComment(String commentId) {
        String key = COMMENT_PREFIX + commentId;
        return cacheRepository.get(key, CommentResponse.class)
                .switchIfEmpty(Mono.error(new RuntimeException("Comment not found")));
    }

    private Flux<CommentResponse> getCommentsForDocument(String documentId) {
        String pattern = COMMENT_PREFIX + "*";
        return cacheRepository.findKeysByPattern(pattern)
                .flatMap(key -> cacheRepository.get(key, CommentResponse.class)
                        .cast(CommentResponse.class))
                .filter(comment -> comment.documentId().equals(documentId));
    }

    private Mono<CommentResponse> publishCommentAddedEvent(CommentResponse comment) {
        // Implementar publicación de evento de comentario
        return Mono.just(comment);
    }

    private Mono<Void> updateTypingStatus(TypingIndicatorCommand command) {
        // Implementar actualización de estado de escritura
        return Mono.empty();
    }

    private Mono<Void> publishTypingIndicatorEvent(TypingIndicatorCommand command) {
        // Implementar evento de indicador de escritura
        return Mono.empty();
    }

    private Mono<Void> checkSectionAvailable(LockSectionCommand command) {
        // Verificar si la sección está disponible para bloqueo
        return Mono.empty();
    }

    private Mono<SectionLockResponse> createSectionLock(LockSectionCommand command) {
        return Mono.fromCallable(() -> new SectionLockResponse(
                UUID.randomUUID().toString(),
                command.documentId(),
                command.userId(),
                "", // userName
                command.startPosition(),
                command.endPosition(),
                command.lockReason(),
                LocalDateTime.now(),
                LocalDateTime.now().plusMinutes(30) // Expira en 30 minutos
        ));
    }

    private Mono<SectionLockResponse> storeSectionLock(SectionLockResponse lock) {
        String key = SECTION_LOCK_PREFIX + lock.lockId();
        return cacheRepository.set(key, lock, Duration.ofMinutes(30))
                .thenReturn(lock);
    }

    private Mono<SectionLockResponse> getSectionLock(String lockId) {
        String key = SECTION_LOCK_PREFIX + lockId;
        return cacheRepository.get(key, SectionLockResponse.class)
                .switchIfEmpty(Mono.error(new RuntimeException("Lock not found")));
    }

    private Mono<Void> removeSectionLock(String lockId) {
        String key = SECTION_LOCK_PREFIX + lockId;
        return cacheRepository.delete(key);
    }

    private Flux<SectionLockResponse> getDocumentLocks(String documentId) {
        String pattern = SECTION_LOCK_PREFIX + "*";
        return cacheRepository.findKeysByPattern(pattern)
                .flatMap(key -> cacheRepository.get(key, SectionLockResponse.class)
                        .cast(SectionLockResponse.class))
                .filter(lock -> lock.documentId().equals(documentId));
    }

    /**
     * Excepciones específicas
     */
    public static class DocumentNotFoundException extends RuntimeException {
        public DocumentNotFoundException(String documentId) {
            super("Document not found: " + documentId);
        }
    }
}
