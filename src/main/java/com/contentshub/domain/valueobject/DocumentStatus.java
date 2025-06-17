package com.contentshub.domain.valueobject;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Value Object para el estado de un documento
 * Representa los diferentes estados en el ciclo de vida de un documento
 */
public enum DocumentStatus {
    DRAFT("DRAFT", "Borrador"),
    PUBLISHED("PUBLISHED", "Publicado"),
    ARCHIVED("ARCHIVED", "Archivado"),
    DELETED("DELETED", "Eliminado");

    private final String value;
    private final String displayName;

    DocumentStatus(String value, String displayName) {
        this.value = value;
        this.displayName = displayName;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public String getDisplayName() {
        return displayName;
    }

    @JsonCreator
    public static DocumentStatus fromValue(String value) {
        if (value == null) {
            return DRAFT; // Default value
        }

        for (DocumentStatus status : DocumentStatus.values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown document status: " + value);
    }

    /**
     * Verificar si el documento es visible públicamente
     */
    public boolean isPubliclyVisible() {
        return this == PUBLISHED;
    }

    /**
     * Verificar si el documento se puede editar
     */
    public boolean isEditable() {
        return this == DRAFT;
    }

    /**
     * Verificar si el documento está activo (no eliminado ni archivado)
     */
    public boolean isActive() {
        return this == DRAFT || this == PUBLISHED;
    }

    /**
     * Verificar si el documento está eliminado
     */
    public boolean isDeleted() {
        return this == DELETED;
    }

    /**
     * Verificar si el documento está archivado
     */
    public boolean isArchived() {
        return this == ARCHIVED;
    }

    /**
     * Obtener estados válidos para transición desde el estado actual
     */
    public java.util.Set<DocumentStatus> getValidTransitions() {
        return switch (this) {
            case DRAFT -> java.util.Set.of(PUBLISHED, ARCHIVED, DELETED);
            case PUBLISHED -> java.util.Set.of(DRAFT, ARCHIVED, DELETED);
            case ARCHIVED -> java.util.Set.of(DRAFT, DELETED);
            case DELETED -> java.util.Set.of(); // No se puede cambiar desde eliminado
        };
    }

    /**
     * Verificar si se puede hacer transición a otro estado
     */
    public boolean canTransitionTo(DocumentStatus newStatus) {
        return getValidTransitions().contains(newStatus);
    }

    /**
     * Obtener el siguiente estado lógico
     */
    public DocumentStatus getNextStatus() {
        return switch (this) {
            case DRAFT -> PUBLISHED;
            case PUBLISHED -> ARCHIVED;
            case ARCHIVED -> DELETED;
            case DELETED -> DELETED; // No hay siguiente estado
        };
    }

    /**
     * Obtener el estado anterior lógico
     */
    public DocumentStatus getPreviousStatus() {
        return switch (this) {
            case DRAFT -> DRAFT; // No hay estado anterior
            case PUBLISHED -> DRAFT;
            case ARCHIVED -> PUBLISHED;
            case DELETED -> ARCHIVED;
        };
    }

    @Override
    public String toString() {
        return displayName;
    }
}
