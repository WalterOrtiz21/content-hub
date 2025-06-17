package com.contentshub.infrastructure.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestor independiente de sesiones WebSocket
 * Rompe la dependencia circular separando la lógica de manejo de sesiones
 */
@Component
@Slf4j
public class WebSocketManager {

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> userSessions = new ConcurrentHashMap<>(); // userId -> sessionId
    private final Map<String, java.util.Set<String>> documentSessions = new ConcurrentHashMap<>(); // documentId -> sessionIds

    /**
     * Agregar sesión
     */
    public void addSession(String sessionId, WebSocketSession session) {
        sessions.put(sessionId, session);
        log.debug("WebSocket session added: {}", sessionId);
    }

    /**
     * Remover sesión
     */
    public void removeSession(String sessionId) {
        WebSocketSession removed = sessions.remove(sessionId);
        if (removed != null) {
            // Limpiar referencias de usuario
            userSessions.entrySet().removeIf(entry -> entry.getValue().equals(sessionId));

            // Limpiar referencias de documento
            documentSessions.values().forEach(sessionIds -> sessionIds.remove(sessionId));
            documentSessions.entrySet().removeIf(entry -> entry.getValue().isEmpty());

            log.debug("WebSocket session removed: {}", sessionId);
        }
    }

    /**
     * Asociar sesión con usuario
     */
    public void associateUserSession(String userId, String sessionId) {
        userSessions.put(userId, sessionId);
        log.debug("User {} associated with session {}", userId, sessionId);
    }

    /**
     * Asociar sesión con documento
     */
    public void associateDocumentSession(String documentId, String sessionId) {
        documentSessions.computeIfAbsent(documentId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
        log.debug("Session {} associated with document {}", sessionId, documentId);
    }

    /**
     * Obtener sesión por ID
     */
    public WebSocketSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    /**
     * Obtener sesión de usuario
     */
    public WebSocketSession getUserSession(String userId) {
        String sessionId = userSessions.get(userId);
        return sessionId != null ? sessions.get(sessionId) : null;
    }

    /**
     * Obtener sesiones de documento
     */
    public java.util.Set<WebSocketSession> getDocumentSessions(String documentId) {
        java.util.Set<String> sessionIds = documentSessions.get(documentId);
        if (sessionIds == null) {
            return java.util.Set.of();
        }

        return sessionIds.stream()
                .map(sessions::get)
                .filter(session -> session != null && session.isOpen())
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Enviar mensaje a usuario específico
     */
    public void sendToUser(String userId, String message) {
        WebSocketSession session = getUserSession(userId);
        if (session != null && session.isOpen()) {
            session.send(Mono.just(session.textMessage(message)))
                    .doOnError(error -> log.error("Error sending message to user {}: {}", userId, error.getMessage()))
                    .subscribe();
        }
    }

    /**
     * Broadcast mensaje a todas las sesiones
     */
    public void broadcastMessage(String message) {
        sessions.values().stream()
                .filter(WebSocketSession::isOpen)
                .forEach(session -> {
                    session.send(Mono.just(session.textMessage(message)))
                            .doOnError(error -> log.error("Error broadcasting message: {}", error.getMessage()))
                            .subscribe();
                });
    }

    /**
     * Enviar mensaje a colaboradores de documento
     */
    public void sendToDocumentCollaborators(String documentId, String message) {
        java.util.Set<WebSocketSession> documentSessions = getDocumentSessions(documentId);
        documentSessions.forEach(session -> {
            if (session.isOpen()) {
                session.send(Mono.just(session.textMessage(message)))
                        .doOnError(error -> log.error("Error sending to document collaborators: {}", error.getMessage()))
                        .subscribe();
            }
        });
    }

    /**
     * Obtener estadísticas de sesiones
     */
    public WebSocketStats getStats() {
        long activeSessions = sessions.values().stream()
                .mapToLong(session -> session.isOpen() ? 1L : 0L)
                .sum();

        return new WebSocketStats(
                sessions.size(),
                activeSessions,
                userSessions.size(),
                documentSessions.size()
        );
    }

    /**
     * Limpiar sesiones cerradas
     */
    public void cleanupClosedSessions() {
        sessions.entrySet().removeIf(entry -> !entry.getValue().isOpen());

        // Limpiar referencias huérfanas
        userSessions.entrySet().removeIf(entry -> !sessions.containsKey(entry.getValue()));
        documentSessions.values().forEach(sessionIds ->
                sessionIds.removeIf(sessionId -> !sessions.containsKey(sessionId)));
        documentSessions.entrySet().removeIf(entry -> entry.getValue().isEmpty());

        log.debug("Cleaned up closed WebSocket sessions");
    }

    /**
     * Estadísticas de WebSocket
     */
    public record WebSocketStats(
            int totalSessions,
            long activeSessions,
            int userSessions,
            int documentSessions
    ) {}
}
