package com.contentshub.adapter.output.messaging;

import com.contentshub.application.port.output.CacheRepositoryPort;
import com.contentshub.application.port.output.EventPublisherPort;
import com.contentshub.domain.event.DomainEvent;
import com.contentshub.infrastructure.config.WebSocketConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Implementación del Event Publisher usando Spring Events y WebSocket
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SpringEventPublisherAdapter implements EventPublisherPort {

    private final ApplicationEventPublisher applicationEventPublisher;
    private final CacheRepositoryPort cacheRepository;
    private final WebSocketConfiguration.WebSocketSessionManager webSocketSessionManager;
    private final ObjectMapper objectMapper;

    // Event handlers registry
    private final Map<Class<? extends DomainEvent>, List<EventHandler<? extends DomainEvent>>> eventHandlers =
            new ConcurrentHashMap<>();

    // Statistics
    private final AtomicLong totalEventsPublished = new AtomicLong(0);
    private final AtomicLong totalEventsProcessed = new AtomicLong(0);
    private final AtomicLong failedEvents = new AtomicLong(0);
    private final Map<String, Long> eventsByType = new ConcurrentHashMap<>();

    // Cache keys
    private static final String EVENT_HISTORY_PREFIX = "event_history:";
    private static final String EVENT_STATS_KEY = "event_stats";

    @Override
    public Mono<Void> publish(DomainEvent event) {
        log.debug("Publishing domain event: {}", event.getEventType());

        return Mono.fromRunnable(() -> {
                    // Publish to Spring event system
                    applicationEventPublisher.publishEvent(event);

                    // Update statistics
                    totalEventsPublished.incrementAndGet();
                    eventsByType.merge(event.getEventType(), 1L, Long::sum);
                })
                .then(storeEventRecord(event))
                .then(publishToWebSocket(event))
                .then(processEventHandlers(event))
                .doOnSuccess(unused -> {
                    log.debug("Domain event published successfully: {}", event.getEventType());
                    totalEventsProcessed.incrementAndGet();
                })
                .doOnError(error -> {
                    log.error("Error publishing domain event {}: {}", event.getEventType(), error.getMessage());
                    failedEvents.incrementAndGet();
                });
    }

    @Override
    public Mono<Void> publishAll(Flux<DomainEvent> events) {
        return events
                .flatMap(this::publish)
                .then()
                .doOnSuccess(unused -> log.debug("All events published successfully"))
                .doOnError(error -> log.error("Error publishing multiple events: {}", error.getMessage()));
    }

    @Override
    //@Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public Mono<Void> publishDelayed(DomainEvent event, Duration delay) {
        log.debug("Publishing delayed event: {} after {}", event.getEventType(), delay);

        return Mono.delay(delay)
                .then(publish(event))
                .doOnSuccess(unused -> log.debug("Delayed event published: {}", event.getEventType()))
                .doOnError(error -> log.error("Error publishing delayed event: {}", error.getMessage()));
    }

    @Override
    public Mono<Void> publishWithRetry(DomainEvent event, int maxRetries) {
        return Mono.defer(() -> publish(event))
                .retry(maxRetries)
                .doOnSuccess(unused -> log.debug("Event published with retry: {}", event.getEventType()))
                .doOnError(error -> {
                    log.error("Failed to publish event after {} retries: {}", maxRetries, error.getMessage());
                    failedEvents.incrementAndGet();
                });
    }

    @Override
    public WebSocketEventOperations webSocket() {
        return new WebSocketEventOperationsImpl();
    }

    @Override
    public EmailEventOperations email() {
        return new EmailEventOperationsImpl();
    }

    @Override
    public <T extends DomainEvent> Mono<Void> registerHandler(Class<T> eventType, EventHandler<T> handler) {
        return Mono.fromRunnable(() -> {
            eventHandlers.computeIfAbsent(eventType, k -> new java.util.ArrayList<>()).add(handler);
            log.debug("Event handler registered for: {}", eventType.getSimpleName());
        });
    }

    @Override
    public Mono<EventStatistics> getStatistics() {
        return Mono.fromCallable(() -> new EventStatistics(
                totalEventsPublished.get(),
                totalEventsProcessed.get(),
                failedEvents.get(),
                Map.copyOf(eventsByType),
                0.0, // averageProcessingTime - implementar si es necesario
                LocalDateTime.now()
        ));
    }

    @Override
    public Flux<EventRecord> getEventHistory(String aggregateId, String aggregateType) {
        String pattern = EVENT_HISTORY_PREFIX + aggregateType + ":" + aggregateId + ":*";

        return cacheRepository.findKeysByPattern(pattern)
                .flatMap(key -> cacheRepository.get(key, EventRecord.class))
                .sort((e1, e2) -> e2.occurredAt().compareTo(e1.occurredAt()))
                .doOnNext(record -> log.debug("Event history record: {}", record.eventType()))
                .doOnError(error -> log.error("Error getting event history: {}", error.getMessage()));
    }

    @Override
    public Flux<EventRecord> getEventsByType(String eventType, LocalDateTime from, LocalDateTime to) {
        String pattern = EVENT_HISTORY_PREFIX + "*:" + eventType + ":*";

        return cacheRepository.findKeysByPattern(pattern)
                .flatMap(key -> cacheRepository.get(key, EventRecord.class))
                .filter(record -> record.occurredAt().isAfter(from) && record.occurredAt().isBefore(to))
                .sort((e1, e2) -> e2.occurredAt().compareTo(e1.occurredAt()))
                .doOnNext(record -> log.debug("Event by type record: {}", record.eventType()))
                .doOnError(error -> log.error("Error getting events by type: {}", error.getMessage()));
    }

    /**
     * Almacenar registro del evento para auditoría
     */
    private Mono<Void> storeEventRecord(DomainEvent event) {
        String key = EVENT_HISTORY_PREFIX + event.getAggregateType() + ":" +
                event.getAggregateId() + ":" + event.getEventId();

        EventRecord record = new EventRecord(
                event.getEventId(),
                event.getEventType(),
                event.getAggregateId(),
                event.getAggregateType(),
                event.getEventData(),
                event.getOccurredAt(),
                "PUBLISHED",
                null
        );

        return cacheRepository.set(key, record, Duration.ofDays(30))
                .doOnError(error -> log.warn("Failed to store event record: {}", error.getMessage()))
                .onErrorResume(error -> Mono.empty()); // No fallar la publicación por esto
    }

    /**
     * Publicar evento a través de WebSocket
     */
    private Mono<Void> publishToWebSocket(DomainEvent event) {
        return Mono.fromRunnable(() -> {
                    try {
                        String eventJson = objectMapper.writeValueAsString(Map.of(
                                "type", "domain_event",
                                "eventType", event.getEventType(),
                                "aggregateId", event.getAggregateId(),
                                "aggregateType", event.getAggregateType(),
                                "data", event.getEventData(),
                                "timestamp", event.getOccurredAt()
                        ));

                        webSocketSessionManager.broadcastMessage(eventJson);
                        log.debug("Event broadcasted via WebSocket: {}", event.getEventType());

                    } catch (Exception e) {
                        log.warn("Failed to broadcast event via WebSocket: {}", e.getMessage());
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(error -> Mono.empty()).then(); // No fallar la publicación por esto
    }

    /**
     * Procesar event handlers registrados
     */
    private Mono<Void> processEventHandlers(DomainEvent event) {
        List<EventHandler<? extends DomainEvent>> handlers = eventHandlers.get(event.getClass());

        if (handlers == null || handlers.isEmpty()) {
            return Mono.empty();
        }

        return Flux.<EventHandler<DomainEvent>>fromIterable((List<EventHandler<DomainEvent>>) (List<?>) handlers)
                .flatMap(handler -> handler.handle(event)
                        .doOnError(error -> log.error("Event handler failed for {}: {}",
                                event.getEventType(), error.toString()))
                        .onErrorResume(error -> Mono.empty()))
                .then()
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Implementación de operaciones WebSocket
     */
    private class WebSocketEventOperationsImpl implements WebSocketEventOperations {

        @Override
        public Mono<Void> sendToUser(String userId, DomainEvent event) {
            return Mono.fromRunnable(() -> {
                        try {
                            String eventJson = objectMapper.writeValueAsString(Map.of(
                                    "type", "domain_event",
                                    "eventType", event.getEventType(),
                                    "data", event.getEventData(),
                                    "timestamp", event.getOccurredAt()
                            ));

                            webSocketSessionManager.sendToUser(userId, eventJson);
                            log.debug("Event sent to user {} via WebSocket: {}", userId, event.getEventType());

                        } catch (Exception e) {
                            log.warn("Failed to send event to user {} via WebSocket: {}", userId, e.getMessage());
                        }
                    })
                    .subscribeOn(Schedulers.boundedElastic()).then();
        }

        @Override
        public Mono<Void> sendToUsers(java.util.Set<String> userIds, DomainEvent event) {
            return Flux.fromIterable(userIds)
                    .flatMap(userId -> sendToUser(userId, event))
                    .then();
        }

        @Override
        public Mono<Void> broadcast(DomainEvent event) {
            return publishToWebSocket(event);
        }

        @Override
        public Mono<Void> sendToDocumentCollaborators(String documentId, DomainEvent event) {
            // En una implementación real, buscarías los colaboradores del documento
            return Mono.fromRunnable(() -> {
                        try {
                            String eventJson = objectMapper.writeValueAsString(Map.of(
                                    "type", "document_event",
                                    "documentId", documentId,
                                    "eventType", event.getEventType(),
                                    "data", event.getEventData(),
                                    "timestamp", event.getOccurredAt()
                            ));

                            webSocketSessionManager.broadcastMessage(eventJson);
                            log.debug("Document event sent to collaborators: {}", event.getEventType());

                        } catch (Exception e) {
                            log.warn("Failed to send document event to collaborators: {}", e.getMessage());
                        }
                    })
                    .subscribeOn(Schedulers.boundedElastic()).then();
        }

        @Override
        public Mono<Void> sendNotification(String userId, WebSocketNotification notification) {
            return Mono.fromRunnable(() -> {
                        try {
                            String notificationJson = objectMapper.writeValueAsString(Map.of(
                                    "type", "notification",
                                    "notification", notification
                            ));

                            webSocketSessionManager.sendToUser(userId, notificationJson);
                            log.debug("Notification sent to user {}: {}", userId, notification.title());

                        } catch (Exception e) {
                            log.warn("Failed to send notification to user {}: {}", userId, e.getMessage());
                        }
                    })
                    .subscribeOn(Schedulers.boundedElastic()).then();
        }
    }

    /**
     * Implementación de operaciones de email
     */
    private class EmailEventOperationsImpl implements EmailEventOperations {

        @Override
        public Mono<Void> sendEventEmail(DomainEvent event, String recipientEmail) {
            return Mono.fromRunnable(() -> {
                        // En una implementación real, configurarías y enviarías emails
                        log.info("Email would be sent to {} for event: {}", recipientEmail, event.getEventType());
                    })
                    .subscribeOn(Schedulers.boundedElastic()).then();
        }

        @Override
        public Mono<Void> sendNotificationEmail(String recipientEmail, EmailNotification notification) {
            return Mono.fromRunnable(() -> {
                        // En una implementación real, usarías un servicio de email
                        log.info("Email notification would be sent to {}: {}", recipientEmail, notification.subject());
                    })
                    .subscribeOn(Schedulers.boundedElastic()).then();
        }

        @Override
        public Mono<Void> sendBulkEmail(List<String> recipients, EmailNotification notification) {
            return Flux.fromIterable(recipients)
                    .flatMap(recipient -> sendNotificationEmail(recipient, notification))
                    .then();
        }
    }
}
