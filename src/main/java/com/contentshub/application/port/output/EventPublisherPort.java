package com.contentshub.application.port.output;

import com.contentshub.domain.event.DomainEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Puerto de salida para publicación de eventos
 * Define el contrato para manejo de eventos de dominio
 */
public interface EventPublisherPort {

    /**
     * Publicar un evento de dominio
     */
    Mono<Void> publish(DomainEvent event);

    /**
     * Publicar múltiples eventos de dominio
     */
    Mono<Void> publishAll(Flux<DomainEvent> events);

    /**
     * Publicar evento con delay
     */
    Mono<Void> publishDelayed(DomainEvent event, java.time.Duration delay);

    /**
     * Publicar evento con retry automático
     */
    Mono<Void> publishWithRetry(DomainEvent event, int maxRetries);

    /**
     * Operaciones de WebSocket para eventos en tiempo real
     */
    interface WebSocketEventOperations {

        /**
         * Enviar evento a un usuario específico
         */
        Mono<Void> sendToUser(String userId, DomainEvent event);

        /**
         * Enviar evento a múltiples usuarios
         */
        Mono<Void> sendToUsers(java.util.Set<String> userIds, DomainEvent event);

        /**
         * Broadcast evento a todos los usuarios conectados
         */
        Mono<Void> broadcast(DomainEvent event);

        /**
         * Enviar evento a usuarios en un documento específico
         */
        Mono<Void> sendToDocumentCollaborators(String documentId, DomainEvent event);

        /**
         * Enviar notificación personalizada
         */
        Mono<Void> sendNotification(String userId, WebSocketNotification notification);
    }

    /**
     * Operaciones de email para eventos
     */
    interface EmailEventOperations {

        /**
         * Enviar email basado en evento
         */
        Mono<Void> sendEventEmail(DomainEvent event, String recipientEmail);

        /**
         * Enviar email de notificación
         */
        Mono<Void> sendNotificationEmail(String recipientEmail, EmailNotification notification);

        /**
         * Enviar email masivo
         */
        Mono<Void> sendBulkEmail(java.util.List<String> recipients, EmailNotification notification);
    }

    /**
     * Obtener operaciones específicas
     */
    WebSocketEventOperations webSocket();
    EmailEventOperations email();

    /**
     * Configurar handlers de eventos
     */
    <T extends DomainEvent> Mono<Void> registerHandler(Class<T> eventType, EventHandler<T> handler);

    /**
     * Obtener estadísticas de eventos
     */
    Mono<EventStatistics> getStatistics();

    /**
     * Obtener historial de eventos
     */
    Flux<EventRecord> getEventHistory(String aggregateId, String aggregateType);

    /**
     * Obtener eventos por tipo en un rango de tiempo
     */
    Flux<EventRecord> getEventsByType(String eventType, LocalDateTime from, LocalDateTime to);

    /**
     * Handler de eventos
     */
    @FunctionalInterface
    interface EventHandler<T extends DomainEvent> {
        Mono<Void> handle(T event);
    }

    /**
     * Notificación WebSocket
     */
    record WebSocketNotification(
            String type,
            String title,
            String message,
            Map<String, Object> data,
            String priority,
            LocalDateTime timestamp
    ) {
        public static WebSocketNotification info(String title, String message) {
            return new WebSocketNotification("info", title, message, Map.of(), "normal", LocalDateTime.now());
        }

        public static WebSocketNotification warning(String title, String message) {
            return new WebSocketNotification("warning", title, message, Map.of(), "high", LocalDateTime.now());
        }

        public static WebSocketNotification error(String title, String message) {
            return new WebSocketNotification("error", title, message, Map.of(), "urgent", LocalDateTime.now());
        }

        public static WebSocketNotification success(String title, String message) {
            return new WebSocketNotification("success", title, message, Map.of(), "normal", LocalDateTime.now());
        }
    }

    /**
     * Notificación por email
     */
    record EmailNotification(
            String subject,
            String body,
            String template,
            Map<String, Object> variables,
            String priority
    ) {
        public static EmailNotification simple(String subject, String body) {
            return new EmailNotification(subject, body, null, Map.of(), "normal");
        }

        public static EmailNotification fromTemplate(String subject, String template, Map<String, Object> variables) {
            return new EmailNotification(subject, null, template, variables, "normal");
        }
    }

    /**
     * Estadísticas de eventos
     */
    record EventStatistics(
            long totalEventsPublished,
            long totalEventsProcessed,
            long failedEvents,
            Map<String, Long> eventsByType,
            double averageProcessingTime,
            LocalDateTime lastEventTime
    ) {}

    /**
     * Registro de evento para auditoría
     */
    record EventRecord(
            String eventId,
            String eventType,
            String aggregateId,
            String aggregateType,
            Map<String, Object> eventData,
            LocalDateTime occurredAt,
            String status,
            String errorMessage
    ) {}
}
