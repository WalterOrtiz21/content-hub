package com.contentshub.domain.event;

import com.contentshub.domain.valueobject.*;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

/**
 * Eventos relacionados con documentos
 */
public class DocumentEvents {

    /**
     * Evento cuando se crea un nuevo documento
     */
    @Value
    @Builder
    public static class DocumentCreated extends DomainEvent {
        String documentId;
        String title;
        DocumentType documentType;
        UserId ownerId;
        boolean isPublic;

        @Override
        public Map<String, Object> getEventData() {
            return Map.of(
                    "documentId", documentId,
                    "title", title,
                    "documentType", documentType.getValue(),
                    "ownerId", ownerId.getValue(),
                    "isPublic", isPublic
            );
        }

        @Override
        public String getAggregateId() {
            return documentId;
        }

        @Override
        public String getAggregateType() {
            return "Document";
        }
    }

    /**
     * Evento cuando se actualiza el contenido de un documento
     */
    @Value
    @Builder
    public static class DocumentContentUpdated extends DomainEvent {
        String documentId;
        Integer newVersion;
        UserId modifiedBy;
        DocumentContent.ContentType contentType;
        int contentLength;

        @Override
        public Map<String, Object> getEventData() {
            return Map.of(
                    "documentId", documentId,
                    "newVersion", newVersion,
                    "modifiedBy", modifiedBy.getValue(),
                    "contentType", contentType.getValue(),
                    "contentLength", contentLength
            );
        }

        @Override
        public String getAggregateId() {
            return documentId;
        }

        @Override
        public String getAggregateType() {
            return "Document";
        }
    }

    /**
     * Evento cuando se publica un documento
     */
    @Value
    @Builder
    public static class DocumentPublished extends DomainEvent {
        String documentId;
        String title;
        UserId publishedBy;
        LocalDateTime publishedAt;

        @Override
        public Map<String, Object> getEventData() {
            return Map.of(
                    "documentId", documentId,
                    "title", title,
                    "publishedBy", publishedBy.getValue(),
                    "publishedAt", publishedAt.toString()
            );
        }

        @Override
        public String getAggregateId() {
            return documentId;
        }

        @Override
        public String getAggregateType() {
            return "Document";
        }
    }

    /**
     * Evento cuando se archiva un documento
     */
    @Value
    @Builder
    public static class DocumentArchived extends DomainEvent {
        String documentId;
        String title;
        UserId archivedBy;
        LocalDateTime archivedAt;
        String reason;

        @Override
        public Map<String, Object> getEventData() {
            return Map.of(
                    "documentId", documentId,
                    "title", title,
                    "archivedBy", archivedBy.getValue(),
                    "archivedAt", archivedAt.toString(),
                    "reason", reason != null ? reason : "manual_archive"
            );
        }

        @Override
        public String getAggregateId() {
            return documentId;
        }

        @Override
        public String getAggregateType() {
            return "Document";
        }
    }

    /**
     * Evento cuando se elimina un documento
     */
    @Value
    @Builder
    public static class DocumentDeleted extends DomainEvent {
        String documentId;
        String title;
        UserId deletedBy;
        LocalDateTime deletedAt;

        @Override
        public Map<String, Object> getEventData() {
            return Map.of(
                    "documentId", documentId,
                    "title", title,
                    "deletedBy", deletedBy.getValue(),
                    "deletedAt", deletedAt.toString()
            );
        }

        @Override
        public String getAggregateId() {
            return documentId;
        }

        @Override
        public String getAggregateType() {
            return "Document";
        }
    }

    /**
     * Evento cuando se agrega un colaborador a un documento
     */
    @Value
    @Builder
    public static class DocumentCollaboratorAdded extends DomainEvent {
        String documentId;
        UserId collaboratorId;
        UserId addedBy;
        String permission;

        @Override
        public Map<String, Object> getEventData() {
            return Map.of(
                    "documentId", documentId,
                    "collaboratorId", collaboratorId.getValue(),
                    "addedBy", addedBy.getValue(),
                    "permission", permission != null ? permission : "READ"
            );
        }

        @Override
        public String getAggregateId() {
            return documentId;
        }

        @Override
        public String getAggregateType() {
            return "Document";
        }
    }

    /**
     * Evento cuando se remueve un colaborador de un documento
     */
    @Value
    @Builder
    public static class DocumentCollaboratorRemoved extends DomainEvent {
        String documentId;
        UserId collaboratorId;
        UserId removedBy;

        @Override
        public Map<String, Object> getEventData() {
            return Map.of(
                    "documentId", documentId,
                    "collaboratorId", collaboratorId.getValue(),
                    "removedBy", removedBy.getValue()
            );
        }

        @Override
        public String getAggregateId() {
            return documentId;
        }

        @Override
        public String getAggregateType() {
            return "Document";
        }
    }

    /**
     * Evento cuando se visualiza un documento
     */
    @Value
    @Builder
    public static class DocumentViewed extends DomainEvent {
        String documentId;
        UserId viewedBy;
        LocalDateTime viewedAt;
        String ipAddress;

        @Override
        public Map<String, Object> getEventData() {
            return Map.of(
                    "documentId", documentId,
                    "viewedBy", viewedBy.getValue(),
                    "viewedAt", viewedAt.toString(),
                    "ipAddress", ipAddress != null ? ipAddress : ""
            );
        }

        @Override
        public String getAggregateId() {
            return documentId;
        }

        @Override
        public String getAggregateType() {
            return "Document";
        }
    }

    /**
     * Evento cuando se hace like a un documento
     */
    @Value
    @Builder
    public static class DocumentLiked extends DomainEvent {
        String documentId;
        UserId likedBy;
        LocalDateTime likedAt;

        @Override
        public Map<String, Object> getEventData() {
            return Map.of(
                    "documentId", documentId,
                    "likedBy", likedBy.getValue(),
                    "likedAt", likedAt.toString()
            );
        }

        @Override
        public String getAggregateId() {
            return documentId;
        }

        @Override
        public String getAggregateType() {
            return "Document";
        }
    }

    /**
     * Evento cuando se inicia una sesión de colaboración en tiempo real
     */
    @Value
    @Builder
    public static class CollaborationSessionStarted extends DomainEvent {
        String documentId;
        UserId userId;
        String sessionId;
        LocalDateTime startedAt;

        @Override
        public Map<String, Object> getEventData() {
            return Map.of(
                    "documentId", documentId,
                    "userId", userId.getValue(),
                    "sessionId", sessionId,
                    "startedAt", startedAt.toString()
            );
        }

        @Override
        public String getAggregateId() {
            return documentId;
        }

        @Override
        public String getAggregateType() {
            return "Document";
        }
    }

    /**
     * Evento cuando se actualiza el título de un documento
     */
    @Value
    @Builder
    public static class DocumentTitleUpdated extends DomainEvent {
        String documentId;
        String oldTitle;
        String newTitle;
        UserId modifiedBy;

        @Override
        public Map<String, Object> getEventData() {
            return Map.of(
                    "documentId", documentId,
                    "oldTitle", oldTitle,
                    "newTitle", newTitle,
                    "modifiedBy", modifiedBy.getValue()
            );
        }

        @Override
        public String getAggregateId() {
            return documentId;
        }

        @Override
        public String getAggregateType() {
            return "Document";
        }
    }

    /**
     * Evento cuando se actualizan los tags de un documento
     */
    @Value
    @Builder
    public static class DocumentTagsUpdated extends DomainEvent {
        String documentId;
        Set<String> oldTags;
        Set<String> newTags;
        UserId modifiedBy;

        @Override
        public Map<String, Object> getEventData() {
            return Map.of(
                    "documentId", documentId,
                    "oldTags", oldTags,
                    "newTags", newTags,
                    "modifiedBy", modifiedBy.getValue()
            );
        }

        @Override
        public String getAggregateId() {
            return documentId;
        }

        @Override
        public String getAggregateType() {
            return "Document";
        }
    }
}
