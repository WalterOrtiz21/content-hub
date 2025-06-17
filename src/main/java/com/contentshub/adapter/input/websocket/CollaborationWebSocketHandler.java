package com.contentshub.adapter.input.websocket;

import com.contentshub.application.port.input.CollaborationUseCase;
import com.contentshub.domain.valueobject.UserId;
import com.contentshub.infrastructure.security.JwtProvider;
import com.contentshub.infrastructure.websocket.WebSocketManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket Handler para colaboración en tiempo real en documentos
 * Actualizado para usar WebSocketManager independiente
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CollaborationWebSocketHandler implements WebSocketHandler {

    private final CollaborationUseCase collaborationUseCase;
    private final JwtProvider jwtProvider;
    private final ObjectMapper objectMapper;
    private final WebSocketManager webSocketManager; // Usar el manager independiente

    // Mapa de streams por documento
    private final Map<String, Sinks.Many<String>> documentSinks = new ConcurrentHashMap<>();
    private final Map<String, SessionInfo> activeSessions = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        log.debug("Nueva conexión WebSocket: {}", session.getId());

        return extractAuthenticationInfo(session)
                .flatMap(authInfo -> handleAuthenticatedSession(session, authInfo))
                .doOnError(error -> log.error("Error en sesión WebSocket {}: {}", session.getId(), error.getMessage()))
                .onErrorResume(error -> session.close());
    }

    /**
     * Extraer información de autenticación de la sesión WebSocket
     */
    private Mono<AuthenticationInfo> extractAuthenticationInfo(WebSocketSession session) {
        return Mono.fromCallable(() -> {
            URI uri = session.getHandshakeInfo().getUri();
            String query = uri.getQuery();

            if (query == null) {
                throw new SecurityException("No authentication token provided");
            }

            Map<String, String> params = parseQueryString(query);
            String token = params.get("token");
            String documentId = params.get("documentId");

            if (token == null || documentId == null) {
                throw new SecurityException("Missing required parameters: token and documentId");
            }

            if (!jwtProvider.validateToken(token)) {
                throw new SecurityException("Invalid authentication token");
            }

            String username = jwtProvider.getUsernameFromToken(token);
            Long userId = jwtProvider.getUserIdFromToken(token);

            return new AuthenticationInfo(UserId.of(userId), username, documentId, token);
        });
    }

    /**
     * Manejar sesión autenticada
     */
    private Mono<Void> handleAuthenticatedSession(WebSocketSession session, AuthenticationInfo authInfo) {
        log.info("Usuario {} conectado al documento {} via WebSocket",
                authInfo.username(), authInfo.documentId());

        // Registrar sesión en el manager
        webSocketManager.addSession(session.getId(), session);
        webSocketManager.associateUserSession(authInfo.userId().toString(), session.getId());
        webSocketManager.associateDocumentSession(authInfo.documentId(), session.getId());

        // Registrar sesión local
        SessionInfo sessionInfo = new SessionInfo(
                session.getId(),
                authInfo.userId(),
                authInfo.username(),
                authInfo.documentId(),
                LocalDateTime.now()
        );
        activeSessions.put(session.getId(), sessionInfo);

        // Iniciar sesión de colaboración
        return startCollaborationSession(authInfo, session.getId())
                .then(setupDocumentStream(authInfo.documentId()))
                .then(handleSessionCommunication(session, authInfo))
                .doFinally(signalType -> cleanupSession(session.getId(), authInfo));
    }

    /**
     * Iniciar sesión de colaboración
     */
    private Mono<Void> startCollaborationSession(AuthenticationInfo authInfo, String sessionId) {
        CollaborationUseCase.StartSessionCommand command =
                new CollaborationUseCase.StartSessionCommand(
                        authInfo.documentId(),
                        authInfo.userId(),
                        sessionId,
                        "WebSocket",
                        "127.0.0.1" // En una implementación real, extraer IP real
                );

        return collaborationUseCase.startSession(command)
                .doOnSuccess(response -> log.debug("Sesión de colaboración iniciada: {}", response.sessionId()))
                .then();
    }

    /**
     * Configurar stream para el documento
     */
    private Mono<Void> setupDocumentStream(String documentId) {
        return Mono.fromRunnable(() -> {
            documentSinks.computeIfAbsent(documentId, key ->
                    Sinks.many().multicast().onBackpressureBuffer());
        });
    }

    /**
     * Manejar comunicación de la sesión
     */
    private Mono<Void> handleSessionCommunication(WebSocketSession session, AuthenticationInfo authInfo) {
        // Stream de mensajes de entrada
        Mono<Void> input = session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .flatMap(message -> handleIncomingMessage(session.getId(), authInfo, message))
                .doOnError(error -> log.error("Error procesando mensaje de entrada: {}", error.getMessage()))
                .onErrorResume(error -> Mono.empty())
                .then();

        // Stream de mensajes de salida
        Flux<WebSocketMessage> output = createOutputFlux(session, authInfo);

        return session.send(output).and(input);
    }

    /**
     * Crear flujo de mensajes de salida
     */
    private Flux<WebSocketMessage> createOutputFlux(WebSocketSession session, AuthenticationInfo authInfo) {
        // Heartbeat cada 30 segundos
        Flux<WebSocketMessage> heartbeat = Flux.interval(Duration.ofSeconds(30))
                .map(tick -> createHeartbeatMessage(session))
                .doOnNext(msg -> log.trace("Enviando heartbeat a sesión: {}", session.getId()));

        // Mensajes de colaboración del documento
        Flux<WebSocketMessage> collaborationMessages = getCollaborationStream(authInfo.documentId())
                .map(session::textMessage)
                .doOnNext(msg -> log.trace("Enviando mensaje de colaboración a sesión: {}", session.getId()));

        // Eventos de colaboración en tiempo real
        Flux<WebSocketMessage> realtimeEvents = collaborationUseCase
                .getCollaborationStream(authInfo.documentId(), authInfo.userId())
                .map(event -> createEventMessage(session, event))
                .doOnNext(msg -> log.trace("Enviando evento en tiempo real a sesión: {}", session.getId()));

        return Flux.merge(heartbeat, collaborationMessages, realtimeEvents);
    }

    /**
     * Manejar mensaje entrante
     */
    private Mono<Void> handleIncomingMessage(String sessionId, AuthenticationInfo authInfo, String message) {
        return Mono.fromRunnable(() -> {
                    try {
                        JsonNode messageNode = objectMapper.readTree(message);
                        String messageType = messageNode.get("type").asText();

                        log.debug("Mensaje recibido de sesión {}: type={}", sessionId, messageType);

                        switch (messageType) {
                            case "content_change" -> handleContentChange(sessionId, authInfo, messageNode);
                            case "cursor_position" -> handleCursorPosition(sessionId, authInfo, messageNode);
                            case "text_selection" -> handleTextSelection(sessionId, authInfo, messageNode);
                            case "typing_indicator" -> handleTypingIndicator(sessionId, authInfo, messageNode);
                            case "comment" -> handleComment(sessionId, authInfo, messageNode);
                            default -> log.warn("Tipo de mensaje desconocido: {}", messageType);
                        }

                    } catch (Exception e) {
                        log.error("Error procesando mensaje de sesión {}: {}", sessionId, e.getMessage());
                    }
                })
                .subscribeOn(Schedulers.boundedElastic()).then();
    }

    /**
     * Manejar cambio de contenido
     */
    private void handleContentChange(String sessionId, AuthenticationInfo authInfo, JsonNode messageNode) {
        try {
            CollaborationUseCase.ContentChangeCommand command =
                    new CollaborationUseCase.ContentChangeCommand(
                            authInfo.documentId(),
                            sessionId,
                            authInfo.userId(),
                            messageNode.get("operation").asText(),
                            messageNode.get("position").asInt(),
                            messageNode.get("content").asText(),
                            messageNode.get("changeId").asText()
                    );

            collaborationUseCase.sendContentChange(command)
                    .doOnSuccess(unused -> broadcastToDocument(authInfo.documentId(),
                            createContentChangeMessage(authInfo, messageNode)))
                    .subscribe();

        } catch (Exception e) {
            log.error("Error procesando cambio de contenido: {}", e.getMessage());
        }
    }

    /**
     * Manejar posición del cursor
     */
    private void handleCursorPosition(String sessionId, AuthenticationInfo authInfo, JsonNode messageNode) {
        try {
            CollaborationUseCase.CursorPositionCommand command =
                    new CollaborationUseCase.CursorPositionCommand(
                            authInfo.documentId(),
                            sessionId,
                            authInfo.userId(),
                            messageNode.get("position").asInt(),
                            messageNode.has("selection") ? messageNode.get("selection").asText() : null
                    );

            collaborationUseCase.sendCursorPosition(command)
                    .doOnSuccess(unused -> broadcastToDocument(authInfo.documentId(),
                            createCursorPositionMessage(authInfo, messageNode)))
                    .subscribe();

        } catch (Exception e) {
            log.error("Error procesando posición del cursor: {}", e.getMessage());
        }
    }

    /**
     * Manejar selección de texto
     */
    private void handleTextSelection(String sessionId, AuthenticationInfo authInfo, JsonNode messageNode) {
        try {
            CollaborationUseCase.TextSelectionCommand command =
                    new CollaborationUseCase.TextSelectionCommand(
                            authInfo.documentId(),
                            sessionId,
                            authInfo.userId(),
                            messageNode.get("startPosition").asInt(),
                            messageNode.get("endPosition").asInt(),
                            messageNode.get("selectedText").asText()
                    );

            collaborationUseCase.sendTextSelection(command)
                    .doOnSuccess(unused -> broadcastToDocument(authInfo.documentId(),
                            createTextSelectionMessage(authInfo, messageNode)))
                    .subscribe();

        } catch (Exception e) {
            log.error("Error procesando selección de texto: {}", e.getMessage());
        }
    }

    /**
     * Manejar indicador de escritura
     */
    private void handleTypingIndicator(String sessionId, AuthenticationInfo authInfo, JsonNode messageNode) {
        try {
            CollaborationUseCase.TypingIndicatorCommand command =
                    new CollaborationUseCase.TypingIndicatorCommand(
                            authInfo.documentId(),
                            sessionId,
                            authInfo.userId(),
                            messageNode.get("isTyping").asBoolean(),
                            messageNode.get("position").asInt()
                    );

            collaborationUseCase.sendTypingIndicator(command)
                    .doOnSuccess(unused -> broadcastToDocument(authInfo.documentId(),
                            createTypingIndicatorMessage(authInfo, messageNode)))
                    .subscribe();

        } catch (Exception e) {
            log.error("Error procesando indicador de escritura: {}", e.getMessage());
        }
    }

    /**
     * Manejar comentario
     */
    private void handleComment(String sessionId, AuthenticationInfo authInfo, JsonNode messageNode) {
        try {
            JsonNode positionNode = messageNode.get("position");
            CollaborationUseCase.CommentPosition position =
                    new CollaborationUseCase.CommentPosition(
                            positionNode.get("startPosition").asInt(),
                            positionNode.get("endPosition").asInt(),
                            positionNode.get("contextText").asText()
                    );

            CollaborationUseCase.AddCommentCommand command =
                    new CollaborationUseCase.AddCommentCommand(
                            authInfo.documentId(),
                            authInfo.userId(),
                            messageNode.get("content").asText(),
                            position,
                            sessionId
                    );

            collaborationUseCase.addComment(command)
                    .doOnSuccess(comment -> broadcastToDocument(authInfo.documentId(),
                            createCommentMessage(authInfo, comment)))
                    .subscribe();

        } catch (Exception e) {
            log.error("Error procesando comentario: {}", e.getMessage());
        }
    }

    /**
     * Broadcast mensaje a todas las sesiones del documento
     */
    private void broadcastToDocument(String documentId, String message) {
        Sinks.Many<String> sink = documentSinks.get(documentId);
        if (sink != null) {
            sink.tryEmitNext(message);
        }

        // También usar el WebSocketManager para broadcast directo
        webSocketManager.sendToDocumentCollaborators(documentId, message);
    }

    /**
     * Obtener stream de colaboración para el documento
     */
    private Flux<String> getCollaborationStream(String documentId) {
        return documentSinks.computeIfAbsent(documentId, key ->
                        Sinks.many().multicast().onBackpressureBuffer())
                .asFlux()
                .doOnSubscribe(subscription -> log.debug("Nueva suscripción al stream del documento: {}", documentId))
                .doOnCancel(() -> log.debug("Cancelada suscripción al stream del documento: {}", documentId));
    }

    /**
     * Limpiar sesión al desconectar
     */
    private void cleanupSession(String sessionId, AuthenticationInfo authInfo) {
        log.info("Limpiando sesión WebSocket: {}", sessionId);

        // Remover del manager
        webSocketManager.removeSession(sessionId);

        // Remover de sesiones activas locales
        SessionInfo sessionInfo = activeSessions.remove(sessionId);

        if (sessionInfo != null) {
            // Finalizar sesión de colaboración
            collaborationUseCase.endSession(sessionId, authInfo.userId())
                    .doOnSuccess(unused -> log.debug("Sesión de colaboración finalizada: {}", sessionId))
                    .doOnError(error -> log.error("Error finalizando sesión de colaboración: {}", error.getMessage()))
                    .subscribe();

            // Notificar a otros colaboradores que el usuario se desconectó
            broadcastToDocument(authInfo.documentId(), createUserLeftMessage(authInfo));

            // Limpiar sink del documento si no hay más sesiones
            cleanupDocumentSinkIfEmpty(authInfo.documentId());
        }
    }

    /**
     * Limpiar sink del documento si no tiene más sesiones
     */
    private void cleanupDocumentSinkIfEmpty(String documentId) {
        boolean hasActiveSessions = activeSessions.values().stream()
                .anyMatch(session -> session.documentId().equals(documentId));

        if (!hasActiveSessions) {
            Sinks.Many<String> sink = documentSinks.remove(documentId);
            if (sink != null) {
                sink.tryEmitComplete();
                log.debug("Sink del documento {} limpiado", documentId);
            }
        }
    }

    /**
     * Métodos de creación de mensajes
     */
    private WebSocketMessage createHeartbeatMessage(WebSocketSession session) {
        try {
            Map<String, Object> heartbeat = Map.of(
                    "type", "heartbeat",
                    "timestamp", LocalDateTime.now().toString()
            );
            return session.textMessage(objectMapper.writeValueAsString(heartbeat));
        } catch (Exception e) {
            log.error("Error creando mensaje heartbeat: {}", e.getMessage());
            return session.textMessage("{\"type\":\"heartbeat\"}");
        }
    }

    private WebSocketMessage createEventMessage(WebSocketSession session, CollaborationUseCase.CollaborationEvent event) {
        try {
            Map<String, Object> eventMessage = Map.of(
                    "type", "collaboration_event",
                    "event", Map.of(
                            "eventId", event.eventId(),
                            "eventType", event.eventType(),
                            "documentId", event.documentId(),
                            "userId", event.userId().getValue(),
                            "userName", event.userName(),
                            "data", event.eventData(),
                            "timestamp", event.timestamp().toString()
                    )
            );
            return session.textMessage(objectMapper.writeValueAsString(eventMessage));
        } catch (Exception e) {
            log.error("Error creando mensaje de evento: {}", e.getMessage());
            return session.textMessage("{\"type\":\"error\",\"message\":\"Event serialization failed\"}");
        }
    }

    private String createContentChangeMessage(AuthenticationInfo authInfo, JsonNode messageNode) {
        try {
            Map<String, Object> message = Map.of(
                    "type", "content_change",
                    "documentId", authInfo.documentId(),
                    "userId", authInfo.userId().getValue(),
                    "userName", authInfo.username(),
                    "operation", messageNode.get("operation").asText(),
                    "position", messageNode.get("position").asInt(),
                    "content", messageNode.get("content").asText(),
                    "changeId", messageNode.get("changeId").asText(),
                    "timestamp", LocalDateTime.now().toString()
            );
            return objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            log.error("Error creando mensaje de cambio de contenido: {}", e.getMessage());
            return "{\"type\":\"error\"}";
        }
    }

    private String createCursorPositionMessage(AuthenticationInfo authInfo, JsonNode messageNode) {
        try {
            Map<String, Object> message = Map.of(
                    "type", "cursor_position",
                    "documentId", authInfo.documentId(),
                    "userId", authInfo.userId().getValue(),
                    "userName", authInfo.username(),
                    "position", messageNode.get("position").asInt(),
                    "selection", messageNode.has("selection") ? messageNode.get("selection").asText() : "",
                    "timestamp", LocalDateTime.now().toString()
            );
            return objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            log.error("Error creando mensaje de posición del cursor: {}", e.getMessage());
            return "{\"type\":\"error\"}";
        }
    }

    private String createTextSelectionMessage(AuthenticationInfo authInfo, JsonNode messageNode) {
        try {
            Map<String, Object> message = Map.of(
                    "type", "text_selection",
                    "documentId", authInfo.documentId(),
                    "userId", authInfo.userId().getValue(),
                    "userName", authInfo.username(),
                    "startPosition", messageNode.get("startPosition").asInt(),
                    "endPosition", messageNode.get("endPosition").asInt(),
                    "selectedText", messageNode.get("selectedText").asText(),
                    "timestamp", LocalDateTime.now().toString()
            );
            return objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            log.error("Error creando mensaje de selección de texto: {}", e.getMessage());
            return "{\"type\":\"error\"}";
        }
    }

    private String createTypingIndicatorMessage(AuthenticationInfo authInfo, JsonNode messageNode) {
        try {
            Map<String, Object> message = Map.of(
                    "type", "typing_indicator",
                    "documentId", authInfo.documentId(),
                    "userId", authInfo.userId().getValue(),
                    "userName", authInfo.username(),
                    "isTyping", messageNode.get("isTyping").asBoolean(),
                    "position", messageNode.get("position").asInt(),
                    "timestamp", LocalDateTime.now().toString()
            );
            return objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            log.error("Error creando mensaje de indicador de escritura: {}", e.getMessage());
            return "{\"type\":\"error\"}";
        }
    }

    private String createCommentMessage(AuthenticationInfo authInfo, CollaborationUseCase.CommentResponse comment) {
        try {
            Map<String, Object> message = Map.of(
                    "type", "comment_added",
                    "documentId", authInfo.documentId(),
                    "comment", Map.of(
                            "commentId", comment.commentId(),
                            "authorId", comment.authorId().getValue(),
                            "authorName", comment.authorName(),
                            "content", comment.content(),
                            "position", Map.of(
                                    "startPosition", comment.position().startPosition(),
                                    "endPosition", comment.position().endPosition(),
                                    "contextText", comment.position().contextText()
                            ),
                            "createdAt", comment.createdAt().toString()
                    ),
                    "timestamp", LocalDateTime.now().toString()
            );
            return objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            log.error("Error creando mensaje de comentario: {}", e.getMessage());
            return "{\"type\":\"error\"}";
        }
    }

    private String createUserLeftMessage(AuthenticationInfo authInfo) {
        try {
            Map<String, Object> message = Map.of(
                    "type", "user_left",
                    "documentId", authInfo.documentId(),
                    "userId", authInfo.userId().getValue(),
                    "userName", authInfo.username(),
                    "timestamp", LocalDateTime.now().toString()
            );
            return objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            log.error("Error creando mensaje de usuario desconectado: {}", e.getMessage());
            return "{\"type\":\"error\"}";
        }
    }

    /**
     * Parsear query string
     */
    private Map<String, String> parseQueryString(String query) {
        Map<String, String> params = new ConcurrentHashMap<>();
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=");
            if (keyValue.length == 2) {
                params.put(keyValue[0], keyValue[1]);
            }
        }
        return params;
    }

    /**
     * Records auxiliares
     */
    private record AuthenticationInfo(
            UserId userId,
            String username,
            String documentId,
            String token
    ) {}

    private record SessionInfo(
            String sessionId,
            UserId userId,
            String username,
            String documentId,
            LocalDateTime connectedAt
    ) {}
}
