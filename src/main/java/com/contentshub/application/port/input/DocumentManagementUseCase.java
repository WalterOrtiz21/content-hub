package com.contentshub.application.port.input;

import com.contentshub.domain.valueobject.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Puerto de entrada para casos de uso de gestión de documentos
 */
public interface DocumentManagementUseCase {

    /**
     * Crear nuevo documento
     */
    Mono<DocumentResponse> createDocument(CreateDocumentCommand command);

    /**
     * Crear documento desde template
     */
    Mono<DocumentResponse> createDocumentFromTemplate(CreateFromTemplateCommand command);

    /**
     * Obtener documento por ID
     */
    Mono<DocumentResponse> getDocumentById(String documentId, UserId requestingUserId);

    /**
     * Actualizar contenido del documento
     */
    Mono<DocumentResponse> updateDocumentContent(UpdateContentCommand command);

    /**
     * Actualizar título del documento
     */
    Mono<DocumentResponse> updateDocumentTitle(UpdateTitleCommand command);

    /**
     * Publicar documento
     */
    Mono<DocumentResponse> publishDocument(String documentId, UserId publishedBy);

    /**
     * Archivar documento
     */
    Mono<DocumentResponse> archiveDocument(String documentId, UserId archivedBy, String reason);

    /**
     * Restaurar documento archivado
     */
    Mono<DocumentResponse> restoreDocument(String documentId, UserId restoredBy);

    /**
     * Eliminar documento
     */
    Mono<Void> deleteDocument(String documentId, UserId deletedBy);

    /**
     * Obtener documentos del usuario
     */
    Flux<DocumentSummaryResponse> getUserDocuments(UserId userId, int page, int size);

    /**
     * Obtener documentos públicos
     */
    Flux<DocumentSummaryResponse> getPublicDocuments(int page, int size);

    /**
     * Buscar documentos
     */
    Flux<DocumentSummaryResponse> searchDocuments(DocumentSearchCommand command);

    /**
     * Agregar colaborador
     */
    Mono<DocumentResponse> addCollaborator(String documentId, UserId collaboratorId, UserId addedBy);

    /**
     * Remover colaborador
     */
    Mono<DocumentResponse> removeCollaborator(String documentId, UserId collaboratorId, UserId removedBy);

    /**
     * Hacer documento público
     */
    Mono<DocumentResponse> makeDocumentPublic(String documentId, UserId modifiedBy);

    /**
     * Hacer documento privado
     */
    Mono<DocumentResponse> makeDocumentPrivate(String documentId, UserId modifiedBy);

    /**
     * Agregar tags al documento
     */
    Mono<DocumentResponse> addTags(String documentId, Set<String> tags, UserId modifiedBy);

    /**
     * Remover tags del documento
     */
    Mono<DocumentResponse> removeTags(String documentId, Set<String> tags, UserId modifiedBy);

    /**
     * Dar like a documento
     */
    Mono<DocumentResponse> likeDocument(String documentId, UserId userId);

    /**
     * Quitar like de documento
     */
    Mono<DocumentResponse> unlikeDocument(String documentId, UserId userId);

    /**
     * Registrar vista de documento
     */
    Mono<DocumentResponse> viewDocument(String documentId, UserId userId, String ipAddress);

    /**
     * Obtener versiones del documento
     */
    Flux<DocumentVersionResponse> getDocumentVersions(String documentId, UserId requestingUserId);

    /**
     * Obtener estadísticas del documento
     */
    Mono<DocumentStatisticsResponse> getDocumentStatistics(String documentId, UserId requestingUserId);

    /**
     * Obtener documentos relacionados
     */
    Flux<DocumentSummaryResponse> getRelatedDocuments(String documentId, int limit);

    /**
     * Duplicar documento
     */
    Mono<DocumentResponse> duplicateDocument(String documentId, String newTitle, UserId userId);

    /**
     * Exportar documento
     */
    Mono<DocumentExportResponse> exportDocument(String documentId, ExportFormat format, UserId userId);

    /**
     * Commands (DTOs de entrada)
     */
    record CreateDocumentCommand(
            String title,
            DocumentContent content,
            DocumentType documentType,
            UserId ownerId,
            boolean isPublic,
            Set<String> tags
    ) {}

    record CreateFromTemplateCommand(
            String title,
            String templateId,
            UserId ownerId,
            boolean isPublic
    ) {}

    record UpdateContentCommand(
            String documentId,
            DocumentContent content,
            UserId modifiedBy
    ) {}

    record UpdateTitleCommand(
            String documentId,
            String newTitle,
            UserId modifiedBy
    ) {}

    record DocumentSearchCommand(
            String query,
            Set<String> tags,
            DocumentType documentType,
            DocumentStatus status,
            UserId ownerId,
            boolean onlyPublic,
            LocalDateTime createdAfter,
            LocalDateTime createdBefore,
            int page,
            int size
    ) {}

    /**
     * Responses (DTOs de salida)
     */
    record DocumentResponse(
            String id,
            String title,
            DocumentContent content,
            DocumentType documentType,
            DocumentStatus status,
            UserId ownerId,
            String ownerName,
            boolean isPublic,
            Set<String> tags,
            Integer version,
            Set<UserId> collaborators,
            Long viewCount,
            Long likeCount,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime publishedAt,
            UserId lastModifiedBy,
            boolean canEdit,
            boolean canDelete,
            boolean canShare
    ) {}

    record DocumentSummaryResponse(
            String id,
            String title,
            DocumentType documentType,
            DocumentStatus status,
            UserId ownerId,
            String ownerName,
            boolean isPublic,
            Set<String> tags,
            Long viewCount,
            Long likeCount,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            String excerpt
    ) {}

    record DocumentVersionResponse(
            String versionId,
            String documentId,
            Integer versionNumber,
            String changesSummary,
            UserId createdBy,
            String createdByName,
            LocalDateTime createdAt,
            Integer contentLength
    ) {}

    record DocumentStatisticsResponse(
            String documentId,
            Long viewCount,
            Long likeCount,
            Integer collaboratorCount,
            LocalDateTime lastModified,
            Integer wordCount,
            Long sizeInBytes,
            java.util.Map<String, Long> viewsByDay,
            java.util.List<String> topViewers,
            java.util.List<String> recentCollaborators
    ) {}

    record DocumentExportResponse(
            String documentId,
            ExportFormat format,
            byte[] content,
            String fileName,
            String mimeType,
            LocalDateTime exportedAt
    ) {}

    /**
     * Formato de exportación
     */
    enum ExportFormat {
        PDF("pdf", "application/pdf"),
        HTML("html", "text/html"),
        MARKDOWN("md", "text/markdown"),
        TXT("txt", "text/plain"),
        DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
        JSON("json", "application/json");

        private final String extension;
        private final String mimeType;

        ExportFormat(String extension, String mimeType) {
            this.extension = extension;
            this.mimeType = mimeType;
        }

        public String getExtension() {
            return extension;
        }

        public String getMimeType() {
            return mimeType;
        }
    }

    /**
     * Excepciones específicas de documentos
     */
    class DocumentException extends RuntimeException {
        public DocumentException(String message) {
            super(message);
        }
        public DocumentException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    class DocumentNotFoundException extends DocumentException {
        public DocumentNotFoundException(String documentId) {
            super("Document not found: " + documentId);
        }
    }

    class DocumentAccessDeniedException extends DocumentException {
        public DocumentAccessDeniedException(String documentId, UserId userId) {
            super(String.format("User %s does not have access to document %s", userId, documentId));
        }
    }

    class DocumentAlreadyPublishedException extends DocumentException {
        public DocumentAlreadyPublishedException(String documentId) {
            super("Document is already published: " + documentId);
        }
    }

    class InvalidDocumentStateException extends DocumentException {
        public InvalidDocumentStateException(String message) {
            super(message);
        }
    }

    class DocumentSizeLimitExceededException extends DocumentException {
        public DocumentSizeLimitExceededException(long size, long maxSize) {
            super(String.format("Document size %d exceeds maximum allowed size %d", size, maxSize));
        }
    }

    class UnsupportedDocumentFormatException extends DocumentException {
        public UnsupportedDocumentFormatException(String format) {
            super("Unsupported document format: " + format);
        }
    }

    class CollaboratorLimitExceededException extends DocumentException {
        public CollaboratorLimitExceededException(int current, int max) {
            super(String.format("Collaborator limit exceeded: %d/%d", current, max));
        }
    }
}
