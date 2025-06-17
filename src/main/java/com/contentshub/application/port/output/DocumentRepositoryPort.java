package com.contentshub.application.port.output;

import com.contentshub.domain.model.DocumentModel;
import com.contentshub.domain.valueobject.DocumentStatus;
import com.contentshub.domain.valueobject.DocumentType;
import com.contentshub.domain.valueobject.UserId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Puerto de salida para repositorio de documentos
 * Define el contrato para persistencia de documentos (MongoDB)
 */
public interface DocumentRepositoryPort {

    /**
     * Guardar un documento
     */
    Mono<DocumentModel> save(DocumentModel documentModel);

    /**
     * Buscar documento por ID
     */
    Mono<DocumentModel> findById(String documentId);

    /**
     * Eliminar documento por ID
     */
    Mono<Void> deleteById(String documentId);

    /**
     * Buscar documentos por propietario
     */
    Flux<DocumentModel> findByOwnerId(UserId ownerId);

    /**
     * Buscar documentos por propietario y estado
     */
    Flux<DocumentModel> findByOwnerIdAndStatus(UserId ownerId, DocumentStatus status);

    /**
     * Buscar documentos públicos y publicados
     */
    Flux<DocumentModel> findPublicDocuments();

    /**
     * Buscar documentos públicos por tipo
     */
    Flux<DocumentModel> findPublicDocumentsByType(DocumentType documentType);

    /**
     * Buscar documentos por título (búsqueda de texto)
     */
    Flux<DocumentModel> findByTitleContaining(String titlePattern);

    /**
     * Buscar documentos por contenido (búsqueda de texto completo)
     */
    Flux<DocumentModel> findByContentContaining(String contentPattern);

    /**
     * Buscar documentos por tags
     */
    Flux<DocumentModel> findByTags(Set<String> tags);

    /**
     * Buscar documentos donde el usuario es colaborador
     */
    Flux<DocumentModel> findByCollaboratorId(UserId collaboratorId);

    /**
     * Buscar documentos que el usuario puede leer
     */
    Flux<DocumentModel> findReadableByUser(UserId userId);

    /**
     * Buscar documentos que el usuario puede escribir
     */
    Flux<DocumentModel> findWritableByUser(UserId userId);

    /**
     * Buscar documentos recientes por usuario
     */
    Flux<DocumentModel> findRecentByUser(UserId userId, int limit);

    /**
     * Buscar documentos populares (más vistos)
     */
    Flux<DocumentModel> findMostViewed(int limit);

    /**
     * Buscar documentos más gustados
     */
    Flux<DocumentModel> findMostLiked(int limit);

    /**
     * Buscar documentos creados en un rango de fechas
     */
    Flux<DocumentModel> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    /**
     * Buscar documentos modificados recientemente
     */
    Flux<DocumentModel> findRecentlyModified(LocalDateTime since);

    /**
     * Contar documentos por propietario
     */
    Mono<Long> countByOwnerId(UserId ownerId);

    /**
     * Contar documentos por estado
     */
    Mono<Long> countByStatus(DocumentStatus status);

    /**
     * Contar documentos públicos
     */
    Mono<Long> countPublicDocuments();

    /**
     * Incrementar contador de vistas
     */
    Mono<DocumentModel> incrementViewCount(String documentId);

    /**
     * Incrementar contador de likes
     */
    Mono<DocumentModel> incrementLikeCount(String documentId);

    /**
     * Decrementar contador de likes
     */
    Mono<DocumentModel> decrementLikeCount(String documentId);

    /**
     * Buscar documentos con paginación
     */
    Flux<DocumentModel> findWithPagination(int page, int size);

    /**
     * Buscar documentos por propietario con paginación
     */
    Flux<DocumentModel> findByOwnerIdWithPagination(UserId ownerId, int page, int size);

    /**
     * Búsqueda avanzada con múltiples criterios
     */
    Flux<DocumentModel> findByCriteria(DocumentSearchCriteria criteria);

    /**
     * Buscar documentos archivados para limpieza
     */
    Flux<DocumentModel> findArchivedDocuments(LocalDateTime cutoffDate);

    /**
     * Obtener estadísticas de documento
     */
    Mono<DocumentStatistics> getDocumentStatistics(String documentId);

    /**
     * Buscar documentos relacionados (por tags similares)
     */
    Flux<DocumentModel> findRelatedDocuments(String documentId, int limit);

    /**
     * Verificar si un usuario puede acceder a un documento
     */
    Mono<Boolean> canUserAccessDocument(String documentId, UserId userId);

    /**
     * Buscar versiones de un documento
     */
    Flux<DocumentVersion> findDocumentVersions(String documentId);

    /**
     * DTO para criterios de búsqueda
     */
    record DocumentSearchCriteria(
            String titlePattern,
            String contentPattern,
            Set<String> tags,
            DocumentType documentType,
            DocumentStatus status,
            UserId ownerId,
            LocalDateTime createdAfter,
            LocalDateTime createdBefore,
            Boolean isPublic,
            Integer page,
            Integer size
    ) {}

    /**
     * DTO para estadísticas de documento
     */
    record DocumentStatistics(
            String documentId,
            Long viewCount,
            Long likeCount,
            Integer collaboratorCount,
            LocalDateTime lastModified,
            Integer wordCount,
            Long sizeInBytes
    ) {}

    /**
     * DTO para versiones de documento
     */
    record DocumentVersion(
            String versionId,
            String documentId,
            Integer versionNumber,
            String changesSummary,
            UserId createdBy,
            LocalDateTime createdAt
    ) {}
}
