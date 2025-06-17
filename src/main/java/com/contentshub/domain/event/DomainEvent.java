package com.contentshub.domain.event;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Clase base para todos los eventos de dominio
 */
@Getter
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "@type")
public abstract class DomainEvent {

    private final String eventId;
    private final LocalDateTime occurredAt;
    private final String eventType;
    private final Integer version;

    protected DomainEvent() {
        this.eventId = UUID.randomUUID().toString();
        this.occurredAt = LocalDateTime.now();
        this.eventType = this.getClass().getSimpleName();
        this.version = 1;
    }

    protected DomainEvent(String eventId, LocalDateTime occurredAt, Integer version) {
        this.eventId = eventId != null ? eventId : UUID.randomUUID().toString();
        this.occurredAt = occurredAt != null ? occurredAt : LocalDateTime.now();
        this.eventType = this.getClass().getSimpleName();
        this.version = version != null ? version : 1;
    }

    /**
     * Obtener datos del evento como mapa para serialización
     */
    public abstract java.util.Map<String, Object> getEventData();

    /**
     * Obtener el agregado que generó el evento
     */
    public abstract String getAggregateId();

    /**
     * Obtener el tipo de agregado
     */
    public abstract String getAggregateType();

    @Override
    public String toString() {
        return String.format("%s{eventId='%s', occurredAt=%s, aggregateId='%s'}",
                eventType, eventId, occurredAt, getAggregateId());
    }
}
