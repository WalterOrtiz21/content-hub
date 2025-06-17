package com.contentshub.application.usecase;

import com.contentshub.application.port.input.DocumentManagementUseCase;
import com.contentshub.application.port.output.CacheRepositoryPort;
import com.contentshub.application.port.output.DocumentRepositoryPort;
import com.contentshub.application.port.output.EventPublisherPort;
import com.contentshub.application.port.output.UserRepositoryPort;
import com.contentshub.domain.event.DocumentEvents;
import com.contentshub.domain.model.DocumentModel;
import com.contentshub.domain.valueobject.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Implementación de casos de uso para gestión de documentos
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentManagementService implements DocumentManagementUseCase {

    private final DocumentRepositoryPort documentRepository;
    private final UserRepositoryPort userRepository;
    private final CacheRepositoryPort cacheRepository;
    private final EventPublisherPort eventPublisher;

    // Cache keys
    private static final String DOCUMENT_CACHE_PREFIX = "document:";
    private static final String USER_DOCUMENTS_CACHE_PREFIX = "user_docs:";
    private static final String PUBLIC_DOCUMENTS_CACHE_KEY = "public_documents";
    private static final Duration DOCUMENT_CACHE_TTL = Duration.ofMinutes(30);

    @Override
    public Mono<DocumentResponse> createDocument(CreateDocumentCommand command) {
        log.debug("Creating document: {} for user: {}", command.title(), command.ownerId());

        return validateDocumentCreation(command)
                .then(createDocumentEntity(command))
                .flatMap(documentRepository::save)
                .flatMap(this::cacheDocument)
                .flatMap(this::publishDocumentCreatedEvent)
                .flatMap(this::convertToResponse)
                .doOnSuccess(response -> log.info("Document created: {} by user: {}",
                        response.title(), response.ownerId()))
                .doOnError(error -> log.error("Error creating document {}: {}",
                        command.title(), error.getMessage()));
    }

    @Override
    public Mono<DocumentResponse> createDocumentFromTemplate(CreateFromTemplateCommand command) {
        log.debug("Creating document from template: {} for user: {}", command.templateId(), command.ownerId());

        return loadTemplate(command.templateId())
                .map(templateContent -> DocumentModel.createFromTemplate(
                        command.title(),
                        templateContent,
                        command.ownerId(),
                        DocumentType.fromValue("template") // Ajustar según el template
                ))
                .flatMap(documentModel -> command.isPublic() ?
                        Mono.just(documentModel.makePublic(command.ownerId())) :
                        Mono.just(documentModel))
                .flatMap(documentRepository::save)
                .flatMap(this::cacheDocument)
                .flatMap(this::publishDocumentCreatedEvent)
                .flatMap(this::convertToResponse)
                .doOnSuccess(response -> log.info("Document created from template: {}", response.title()))
                .doOnError(error -> log.error("Error creating document from template: {}", error.getMessage()));
    }

    @Override
    public Mono<DocumentResponse> getDocumentById(String documentId, UserId requestingUserId) {
        log.debug("Getting document: {} for user: {}", documentId, requestingUserId);

        String cacheKey = DOCUMENT_CACHE_PREFIX + documentId;

        return cacheRepository.get(cacheKey, DocumentModel.class)
                .switchIfEmpty(documentRepository.findById(documentId)
                        .flatMap(this::cacheDocument))
                .switchIfEmpty(Mono.error(new DocumentNotFoundException(documentId)))
                .flatMap(documentModel -> validateDocumentAccess(documentModel, requestingUserId))
                .flatMap(documentModel -> recordDocumentView(documentModel, requestingUserId))
                .flatMap(this::convertToResponse)
                .doOnSuccess(response -> log.debug("Document retrieved: {}", response.title()))
                .doOnError(error -> log.error("Error getting document {}: {}", documentId, error.getMessage()));
    }

    @Override
    public Mono<DocumentResponse> updateDocumentContent(UpdateContentCommand command) {
        log.debug("Updating content for document: {}", command.documentId());

        return documentRepository.findById(command.documentId())
                .switchIfEmpty(Mono.error(new DocumentNotFoundException(command.documentId())))
                .flatMap(documentModel -> validateDocumentWriteAccess(documentModel, command.modifiedBy()))
                .map(documentModel -> documentModel.updateContent(command.content(), command.modifiedBy()))
                .flatMap(documentRepository::save)
                .flatMap(this::invalidateDocumentCache)
                .flatMap(this::publishContentUpdatedEvent)
                .flatMap(this::convertToResponse)
                .doOnSuccess(response -> log.info("Document content updated: {}", response.title()))
                .doOnError(error -> log.error("Error updating document content {}: {}",
                        command.documentId(), error.getMessage()));
    }

    @Override
    public Mono<DocumentResponse> updateDocumentTitle(UpdateTitleCommand command) {
        log.debug("Updating title for document: {}", command.documentId());

        return documentRepository.findById(command.documentId())
                .switchIfEmpty(Mono.error(new DocumentNotFoundException(command.documentId())))
                .flatMap(documentModel -> validateDocumentWriteAccess(documentModel, command.modifiedBy()))
                .map(documentModel -> {
                    String oldTitle = documentModel.getTitle();
                    DocumentModel updatedDocumentModel = documentModel.updateTitle(command.newTitle(), command.modifiedBy());
                    return new DocumentWithOldTitle(updatedDocumentModel, oldTitle);
                })
                .flatMap(docWithOldTitle -> documentRepository.save(docWithOldTitle.documentModel())
                        .flatMap(this::invalidateDocumentCache)
                        .flatMap(doc -> publishTitleUpdatedEvent(doc, docWithOldTitle.oldTitle())))
                .flatMap(this::convertToResponse)
                .doOnSuccess(response -> log.info("Document title updated: {}", response.title()))
                .doOnError(error -> log.error("Error updating document title {}: {}",
                        command.documentId(), error.getMessage()));
    }

    @Override
    public Mono<DocumentResponse> publishDocument(String documentId, UserId publishedBy) {
        log.debug("Publishing document: {}", documentId);

        return documentRepository.findById(documentId)
                .switchIfEmpty(Mono.error(new DocumentNotFoundException(documentId)))
                .flatMap(documentModel -> validateDocumentWriteAccess(documentModel, publishedBy))
                .filter(documentModel -> documentModel.getStatus() != DocumentStatus.PUBLISHED)
                .switchIfEmpty(Mono.error(new DocumentAlreadyPublishedException(documentId)))
                .map(documentModel -> documentModel.publish(publishedBy))
                .flatMap(documentRepository::save)
                .flatMap(this::invalidateDocumentCache)
                .flatMap(this::publishDocumentPublishedEvent)
                .flatMap(this::convertToResponse)
                .doOnSuccess(response -> log.info("Document published: {}", response.title()))
                .doOnError(error -> log.error("Error publishing document {}: {}", documentId, error.getMessage()));
    }

    @Override
    public Mono<DocumentResponse> archiveDocument(String documentId, UserId archivedBy, String reason) {
        log.debug("Archiving document: {}", documentId);

        return documentRepository.findById(documentId)
                .switchIfEmpty(Mono.error(new DocumentNotFoundException(documentId)))
                .flatMap(documentModel -> validateDocumentWriteAccess(documentModel, archivedBy))
                .map(documentModel -> documentModel.archive(archivedBy))
                .flatMap(documentRepository::save)
                .flatMap(this::invalidateDocumentCache)
                .flatMap(documentModel -> publishDocumentArchivedEvent(documentModel, archivedBy, reason))
                .flatMap(this::convertToResponse)
                .doOnSuccess(response -> log.info("Document archived: {}", response.title()))
                .doOnError(error -> log.error("Error archiving document {}: {}", documentId, error.getMessage()));
    }

    @Override
    public Mono<DocumentResponse> restoreDocument(String documentId, UserId restoredBy) {
        log.debug("Restoring document: {}", documentId);

        return documentRepository.findById(documentId)
                .switchIfEmpty(Mono.error(new DocumentNotFoundException(documentId)))
                .flatMap(documentModel -> validateDocumentWriteAccess(documentModel, restoredBy))
                .filter(DocumentModel::isArchived)
                .switchIfEmpty(Mono.error(new InvalidDocumentStateException(
                        "Document is not archived and cannot be restored")))
                .map(documentModel -> documentModel.restore(restoredBy))
                .flatMap(documentRepository::save)
                .flatMap(this::invalidateDocumentCache)
                .flatMap(this::convertToResponse)
                .doOnSuccess(response -> log.info("Document restored: {}", response.title()))
                .doOnError(error -> log.error("Error restoring document {}: {}", documentId, error.getMessage()));
    }

    @Override
    public Mono<Void> deleteDocument(String documentId, UserId deletedBy) {
        log.debug("Deleting document: {}", documentId);

        return documentRepository.findById(documentId)
                .switchIfEmpty(Mono.error(new DocumentNotFoundException(documentId)))
                .filter(documentModel -> documentModel.getOwnerId().equals(deletedBy))
                .switchIfEmpty(Mono.error(new DocumentAccessDeniedException(documentId, deletedBy)))
                .flatMap(documentModel -> publishDocumentDeletedEvent(documentModel, deletedBy))
                .then(documentRepository.deleteById(documentId))
                .then(invalidateDocumentCacheById(documentId))
                .doOnSuccess(unused -> log.info("Document deleted: {}", documentId))
                .doOnError(error -> log.error("Error deleting document {}: {}", documentId, error.getMessage()));
    }

    @Override
    public Flux<DocumentSummaryResponse> getUserDocuments(UserId userId, int page, int size) {
        log.debug("Getting documents for user: {} (page: {}, size: {})", userId, page, size);

        return documentRepository.findByOwnerIdWithPagination(userId, page, size)
                .flatMap(this::convertToSummaryResponse)
                .doOnNext(doc -> log.debug("User document: {}", doc.title()))
                .doOnError(error -> log.error("Error getting user documents: {}", error.getMessage()));
    }

    @Override
    public Flux<DocumentSummaryResponse> getPublicDocuments(int page, int size) {
        log.debug("Getting public documents (page: {}, size: {})", page, size);

        return documentRepository.findPublicDocuments()
                .skip(page * size)
                .take(size)
                .flatMap(this::convertToSummaryResponse)
                .doOnNext(doc -> log.debug("Public document: {}", doc.title()))
                .doOnError(error -> log.error("Error getting public documents: {}", error.getMessage()));
    }

    @Override
    public Flux<DocumentSummaryResponse> searchDocuments(DocumentSearchCommand command) {
        log.debug("Searching documents with criteria: {}", command);

        DocumentRepositoryPort.DocumentSearchCriteria criteria =
                new DocumentRepositoryPort.DocumentSearchCriteria(
                        command.query(),
                        command.query(), // También buscar en contenido
                        command.tags(),
                        command.documentType(),
                        command.status(),
                        command.ownerId(),
                        command.createdAfter(),
                        command.createdBefore(),
                        command.onlyPublic(),
                        command.page(),
                        command.size()
                );

        return documentRepository.findByCriteria(criteria)
                .flatMap(this::convertToSummaryResponse)
                .doOnNext(doc -> log.debug("Search result: {}", doc.title()))
                .doOnError(error -> log.error("Error searching documents: {}", error.getMessage()));
    }

    @Override
    public Mono<DocumentResponse> addCollaborator(String documentId, UserId collaboratorId, UserId addedBy) {
        log.debug("Adding collaborator {} to document: {}", collaboratorId, documentId);

        return documentRepository.findById(documentId)
                .switchIfEmpty(Mono.error(new DocumentNotFoundException(documentId)))
                .flatMap(documentModel -> validateDocumentWriteAccess(documentModel, addedBy))
                .map(documentModel -> documentModel.addCollaborator(collaboratorId, addedBy))
                .flatMap(documentRepository::save)
                .flatMap(this::invalidateDocumentCache)
                .flatMap(documentModel -> publishCollaboratorAddedEvent(documentModel, collaboratorId, addedBy))
                .flatMap(this::convertToResponse)
                .doOnSuccess(response -> log.info("Collaborator added to document: {}", response.title()))
                .doOnError(error -> log.error("Error adding collaborator to document {}: {}",
                        documentId, error.getMessage()));
    }

    @Override
    public Mono<DocumentResponse> removeCollaborator(String documentId, UserId collaboratorId, UserId removedBy) {
        log.debug("Removing collaborator {} from document: {}", collaboratorId, documentId);

        return documentRepository.findById(documentId)
                .switchIfEmpty(Mono.error(new DocumentNotFoundException(documentId)))
                .flatMap(documentModel -> validateDocumentWriteAccess(documentModel, removedBy))
                .map(documentModel -> documentModel.removeCollaborator(collaboratorId, removedBy))
                .flatMap(documentRepository::save)
                .flatMap(this::invalidateDocumentCache)
                .flatMap(documentModel -> publishCollaboratorRemovedEvent(documentModel, collaboratorId, removedBy))
                .flatMap(this::convertToResponse)
                .doOnSuccess(response -> log.info("Collaborator removed from document: {}", response.title()))
                .doOnError(error -> log.error("Error removing collaborator from document {}: {}",
                        documentId, error.getMessage()));
    }

    @Override
    public Mono<DocumentResponse> makeDocumentPublic(String documentId, UserId modifiedBy) {
        log.debug("Making document public: {}", documentId);

        return documentRepository.findById(documentId)
                .switchIfEmpty(Mono.error(new DocumentNotFoundException(documentId)))
                .flatMap(documentModel -> validateDocumentWriteAccess(documentModel, modifiedBy))
                .map(documentModel -> documentModel.makePublic(modifiedBy))
                .flatMap(documentRepository::save)
                .flatMap(this::invalidateDocumentCache)
                .flatMap(this::convertToResponse)
                .doOnSuccess(response -> log.info("Document made public: {}", response.title()))
                .doOnError(error -> log.error("Error making document public {}: {}", documentId, error.getMessage()));
    }

    @Override
    public Mono<DocumentResponse> makeDocumentPrivate(String documentId, UserId modifiedBy) {
        log.debug("Making document private: {}", documentId);

        return documentRepository.findById(documentId)
                .switchIfEmpty(Mono.error(new DocumentNotFoundException(documentId)))
                .flatMap(documentModel -> validateDocumentWriteAccess(documentModel, modifiedBy))
                .map(documentModel -> documentModel.makePrivate(modifiedBy))
                .flatMap(documentRepository::save)
                .flatMap(this::invalidateDocumentCache)
                .flatMap(this::convertToResponse)
                .doOnSuccess(response -> log.info("Document made private: {}", response.title()))
                .doOnError(error -> log.error("Error making document private {}: {}", documentId, error.getMessage()));
    }

    @Override
    public Mono<DocumentResponse> addTags(String documentId, Set<String> tags, UserId modifiedBy) {
        log.debug("Adding tags {} to document: {}", tags, documentId);

        return documentRepository.findById(documentId)
                .switchIfEmpty(Mono.error(new DocumentNotFoundException(documentId)))
                .flatMap(documentModel -> validateDocumentWriteAccess(documentModel, modifiedBy))
                .map(documentModel -> {
                    Set<String> oldTags = documentModel.getTags();
                    DocumentModel updatedDocumentModel = documentModel.addTags(tags, modifiedBy);
                    return new DocumentWithOldTags(updatedDocumentModel, oldTags);
                })
                .flatMap(docWithOldTags -> documentRepository.save(docWithOldTags.documentModel())
                        .flatMap(this::invalidateDocumentCache)
                        .flatMap(doc -> publishTagsUpdatedEvent(doc, docWithOldTags.oldTags())))
                .flatMap(this::convertToResponse)
                .doOnSuccess(response -> log.info("Tags added to document: {}", response.title()))
                .doOnError(error -> log.error("Error adding tags to document {}: {}", documentId, error.getMessage()));
    }

    @Override
    public Mono<DocumentResponse> removeTags(String documentId, Set<String> tags, UserId modifiedBy) {
        log.debug("Removing tags {} from document: {}", tags, documentId);

        return documentRepository.findById(documentId)
                .switchIfEmpty(Mono.error(new DocumentNotFoundException(documentId)))
                .flatMap(documentModel -> validateDocumentWriteAccess(documentModel, modifiedBy))
                .map(documentModel -> {
                    Set<String> oldTags = documentModel.getTags();
                    DocumentModel updatedDocumentModel = documentModel.removeTags(tags, modifiedBy);
                    return new DocumentWithOldTags(updatedDocumentModel, oldTags);
                })
                .flatMap(docWithOldTags -> documentRepository.save(docWithOldTags.documentModel())
                        .flatMap(this::invalidateDocumentCache)
                        .flatMap(doc -> publishTagsUpdatedEvent(doc, docWithOldTags.oldTags())))
                .flatMap(this::convertToResponse)
                .doOnSuccess(response -> log.info("Tags removed from document: {}", response.title()))
                .doOnError(error -> log.error("Error removing tags from document {}: {}", documentId, error.getMessage()));
    }

    @Override
    public Mono<DocumentResponse> likeDocument(String documentId, UserId userId) {
        log.debug("User {} liking document: {}", userId, documentId);

        return documentRepository.incrementLikeCount(documentId)
                .switchIfEmpty(Mono.error(new DocumentNotFoundException(documentId)))
                .flatMap(this::invalidateDocumentCache)
                .flatMap(documentModel -> publishDocumentLikedEvent(documentModel, userId))
                .flatMap(this::convertToResponse)
                .doOnSuccess(response -> log.info("Document liked: {}", response.title()))
                .doOnError(error -> log.error("Error liking document {}: {}", documentId, error.getMessage()));
    }

    @Override
    public Mono<DocumentResponse> unlikeDocument(String documentId, UserId userId) {
        log.debug("User {} unliking document: {}", userId, documentId);

        return documentRepository.decrementLikeCount(documentId)
                .switchIfEmpty(Mono.error(new DocumentNotFoundException(documentId)))
                .flatMap(this::invalidateDocumentCache)
                .flatMap(this::convertToResponse)
                .doOnSuccess(response -> log.info("Document unliked: {}", response.title()))
                .doOnError(error -> log.error("Error unliking document {}: {}", documentId, error.getMessage()));
    }

    @Override
    public Mono<DocumentResponse> viewDocument(String documentId, UserId userId, String ipAddress) {
        log.debug("User {} viewing document: {}", userId, documentId);

        return documentRepository.incrementViewCount(documentId)
                .switchIfEmpty(Mono.error(new DocumentNotFoundException(documentId)))
                .flatMap(documentModel -> publishDocumentViewedEvent(documentModel, userId, ipAddress))
                .flatMap(this::convertToResponse)
                .doOnSuccess(response -> log.debug("Document view recorded: {}", response.title()))
                .doOnError(error -> log.error("Error recording document view {}: {}", documentId, error.getMessage()));
    }

    @Override
    public Flux<DocumentVersionResponse> getDocumentVersions(String documentId, UserId requestingUserId) {
        log.debug("Getting versions for document: {}", documentId);

        return documentRepository.findById(documentId)
                .switchIfEmpty(Mono.error(new DocumentNotFoundException(documentId)))
                .flatMap(documentModel -> validateDocumentAccess(documentModel, requestingUserId))
                .flatMapMany(documentModel -> documentRepository.findDocumentVersions(documentId))
                .map(version -> new DocumentVersionResponse(
                        version.versionId(),
                        version.documentId(),
                        version.versionNumber(),
                        version.changesSummary(),
                        version.createdBy(),
                        "Unknown", // createdByName - obtener del repositorio de usuarios
                        version.createdAt(),
                        0 // contentLength - calcular si es necesario
                ))
                .doOnNext(version -> log.debug("Document version: {}", version.versionNumber()))
                .doOnError(error -> log.error("Error getting document versions {}: {}", documentId, error.getMessage()));
    }

    @Override
    public Mono<DocumentStatisticsResponse> getDocumentStatistics(String documentId, UserId requestingUserId) {
        log.debug("Getting statistics for document: {}", documentId);

        return documentRepository.findById(documentId)
                .switchIfEmpty(Mono.error(new DocumentNotFoundException(documentId)))
                .flatMap(documentModel -> validateDocumentAccess(documentModel, requestingUserId))
                .flatMap(documentModel -> documentRepository.getDocumentStatistics(documentId))
                .map(stats -> new DocumentStatisticsResponse(
                        stats.documentId(),
                        stats.viewCount(),
                        stats.likeCount(),
                        stats.collaboratorCount(),
                        stats.lastModified(),
                        stats.wordCount(),
                        stats.sizeInBytes(),
                        java.util.Map.of(), // viewsByDay - implementar si es necesario
                        java.util.List.of(), // topViewers - implementar si es necesario
                        java.util.List.of() // recentCollaborators - implementar si es necesario
                ))
                .doOnSuccess(stats -> log.debug("Document statistics retrieved for: {}", documentId))
                .doOnError(error -> log.error("Error getting document statistics {}: {}", documentId, error.getMessage()));
    }

    @Override
    public Flux<DocumentSummaryResponse> getRelatedDocuments(String documentId, int limit) {
        log.debug("Getting related documents for: {}", documentId);

        return documentRepository.findRelatedDocuments(documentId, limit)
                .flatMap(this::convertToSummaryResponse)
                .doOnNext(doc -> log.debug("Related document: {}", doc.title()))
                .doOnError(error -> log.error("Error getting related documents for {}: {}", documentId, error.getMessage()));
    }

    @Override
    public Mono<DocumentResponse> duplicateDocument(String documentId, String newTitle, UserId userId) {
        log.debug("Duplicating document: {} with new title: {}", documentId, newTitle);

        return documentRepository.findById(documentId)
                .switchIfEmpty(Mono.error(new DocumentNotFoundException(documentId)))
                .flatMap(documentModel -> validateDocumentAccess(documentModel, userId))
                .map(originalDocumentModel -> DocumentModel.createNew(
                                newTitle,
                                originalDocumentModel.getContent(),
                                userId,
                                originalDocumentModel.getDocumentType())
                        .withTags(originalDocumentModel.getTags())
                        .withIsPublic(false)) // Always create duplicates as private
                .flatMap(documentRepository::save)
                .flatMap(this::cacheDocument)
                .flatMap(this::publishDocumentCreatedEvent)
                .flatMap(this::convertToResponse)
                .doOnSuccess(response -> log.info("Document duplicated: {}", response.title()))
                .doOnError(error -> log.error("Error duplicating document {}: {}", documentId, error.getMessage()));
    }

    @Override
    public Mono<DocumentExportResponse> exportDocument(String documentId, ExportFormat format, UserId userId) {
        log.debug("Exporting document: {} in format: {}", documentId, format);

        return documentRepository.findById(documentId)
                .switchIfEmpty(Mono.error(new DocumentNotFoundException(documentId)))
                .flatMap(documentModel -> validateDocumentAccess(documentModel, userId))
                .flatMap(documentModel -> convertDocumentToFormat(documentModel, format))
                .doOnSuccess(response -> log.info("Document exported: {} in format: {}", documentId, format))
                .doOnError(error -> log.error("Error exporting document {}: {}", documentId, error.getMessage()));
    }

    /**
     * Métodos auxiliares privados
     */

    private Mono<Void> validateDocumentCreation(CreateDocumentCommand command) {
        // Validaciones de negocio para creación
        return Mono.empty();
    }

    private Mono<DocumentModel> createDocumentEntity(CreateDocumentCommand command) {
        return Mono.fromCallable(() -> {
            DocumentModel documentModel = DocumentModel.createNew(
                    command.title(),
                    command.content(),
                    command.ownerId(),
                    command.documentType()
            );

            if (command.isPublic()) {
                documentModel = documentModel.makePublic(command.ownerId());
            }

            if (command.tags() != null && !command.tags().isEmpty()) {
                documentModel = documentModel.addTags(command.tags(), command.ownerId());
            }

            return documentModel;
        });
    }

    private Mono<DocumentContent> loadTemplate(String templateId) {
        // En una implementación real, cargarías el template desde la base de datos
        return Mono.just(DocumentContent.plainText("Template content"));
    }

    private Mono<DocumentModel> validateDocumentAccess(DocumentModel documentModel, UserId userId) {
        if (!documentModel.canRead(userId)) {
            return Mono.error(new DocumentAccessDeniedException(documentModel.getId(), userId));
        }
        return Mono.just(documentModel);
    }

    private Mono<DocumentModel> validateDocumentWriteAccess(DocumentModel documentModel, UserId userId) {
        if (!documentModel.canWrite(userId)) {
            return Mono.error(new DocumentAccessDeniedException(documentModel.getId(), userId));
        }
        return Mono.just(documentModel);
    }

    private Mono<DocumentModel> recordDocumentView(DocumentModel documentModel, UserId userId) {
        return documentRepository.incrementViewCount(documentModel.getId())
                .flatMap(updatedDoc -> publishDocumentViewedEvent(updatedDoc, userId, ""))
                .thenReturn(documentModel);
    }

    private Mono<DocumentModel> cacheDocument(DocumentModel documentModel) {
        if (documentModel.getId() == null) {
            return Mono.just(documentModel);
        }

        String cacheKey = DOCUMENT_CACHE_PREFIX + documentModel.getId();
        return cacheRepository.set(cacheKey, documentModel, DOCUMENT_CACHE_TTL)
                .thenReturn(documentModel);
    }

    private Mono<DocumentModel> invalidateDocumentCache(DocumentModel documentModel) {
        String cacheKey = DOCUMENT_CACHE_PREFIX + documentModel.getId();
        return cacheRepository.delete(cacheKey)
                .then(cacheRepository.delete(USER_DOCUMENTS_CACHE_PREFIX + documentModel.getOwnerId().getValue()))
                .then(cacheRepository.delete(PUBLIC_DOCUMENTS_CACHE_KEY))
                .thenReturn(documentModel);
    }

    private Mono<Void> invalidateDocumentCacheById(String documentId) {
        String cacheKey = DOCUMENT_CACHE_PREFIX + documentId;
        return cacheRepository.delete(cacheKey);
    }

    private Mono<DocumentResponse> convertToResponse(DocumentModel documentModel) {
        return userRepository.findById(documentModel.getOwnerId())
                .map(owner -> new DocumentResponse(
                        documentModel.getId(),
                        documentModel.getTitle(),
                        documentModel.getContent(),
                        documentModel.getDocumentType(),
                        documentModel.getStatus(),
                        documentModel.getOwnerId(),
                        owner.getFullName(),
                        documentModel.getIsPublic(),
                        documentModel.getTags(),
                        documentModel.getVersion(),
                        documentModel.getCollaborators(),
                        documentModel.getViewCount(),
                        documentModel.getLikeCount(),
                        documentModel.getCreatedAt(),
                        documentModel.getUpdatedAt(),
                        documentModel.getPublishedAt(),
                        documentModel.getLastModifiedBy(),
                        true, // canEdit - calcular según permisos
                        true, // canDelete - calcular según permisos
                        true  // canShare - calcular según permisos
                ))
                .defaultIfEmpty(new DocumentResponse(
                        documentModel.getId(),
                        documentModel.getTitle(),
                        documentModel.getContent(),
                        documentModel.getDocumentType(),
                        documentModel.getStatus(),
                        documentModel.getOwnerId(),
                        "Unknown User",
                        documentModel.getIsPublic(),
                        documentModel.getTags(),
                        documentModel.getVersion(),
                        documentModel.getCollaborators(),
                        documentModel.getViewCount(),
                        documentModel.getLikeCount(),
                        documentModel.getCreatedAt(),
                        documentModel.getUpdatedAt(),
                        documentModel.getPublishedAt(),
                        documentModel.getLastModifiedBy(),
                        false, false, false
                ));
    }

    private Mono<DocumentSummaryResponse> convertToSummaryResponse(DocumentModel documentModel) {
        return userRepository.findById(documentModel.getOwnerId())
                .map(owner -> new DocumentSummaryResponse(
                        documentModel.getId(),
                        documentModel.getTitle(),
                        documentModel.getDocumentType(),
                        documentModel.getStatus(),
                        documentModel.getOwnerId(),
                        owner.getFullName(),
                        documentModel.getIsPublic(),
                        documentModel.getTags(),
                        documentModel.getViewCount(),
                        documentModel.getLikeCount(),
                        documentModel.getCreatedAt(),
                        documentModel.getUpdatedAt(),
                        generateExcerpt(documentModel.getContent())
                ))
                .defaultIfEmpty(new DocumentSummaryResponse(
                        documentModel.getId(),
                        documentModel.getTitle(),
                        documentModel.getDocumentType(),
                        documentModel.getStatus(),
                        documentModel.getOwnerId(),
                        "Unknown User",
                        documentModel.getIsPublic(),
                        documentModel.getTags(),
                        documentModel.getViewCount(),
                        documentModel.getLikeCount(),
                        documentModel.getCreatedAt(),
                        documentModel.getUpdatedAt(),
                        generateExcerpt(documentModel.getContent())
                ));
    }

    private String generateExcerpt(DocumentContent content) {
        if (content == null) return "";
        String text = content.getAllText();
        return text.length() > 200 ? text.substring(0, 200) + "..." : text;
    }

    private Mono<DocumentExportResponse> convertDocumentToFormat(DocumentModel documentModel, ExportFormat format) {
        return Mono.fromCallable(() -> {
            // En una implementación real, convertirías el documento al formato solicitado
            String content = documentModel.getContent().getAllText();
            byte[] bytes = content.getBytes();
            String fileName = documentModel.getTitle() + "." + format.getExtension();

            return new DocumentExportResponse(
                    documentModel.getId(),
                    format,
                    bytes,
                    fileName,
                    format.getMimeType(),
                    LocalDateTime.now()
            );
        });
    }

    /**
     * Eventos de dominio
     */

    private Mono<DocumentModel> publishDocumentCreatedEvent(DocumentModel documentModel) {
        DocumentEvents.DocumentCreated event = DocumentEvents.DocumentCreated.builder()
                .documentId(documentModel.getId())
                .title(documentModel.getTitle())
                .documentType(documentModel.getDocumentType())
                .ownerId(documentModel.getOwnerId())
                .isPublic(documentModel.getIsPublic())
                .build();

        return eventPublisher.publish(event).thenReturn(documentModel);
    }

    private Mono<DocumentModel> publishContentUpdatedEvent(DocumentModel documentModel) {
        DocumentEvents.DocumentContentUpdated event = DocumentEvents.DocumentContentUpdated.builder()
                .documentId(documentModel.getId())
                .newVersion(documentModel.getVersion())
                .modifiedBy(documentModel.getLastModifiedBy())
                .contentType(documentModel.getContent().getType())
                .contentLength(documentModel.getContent().getLength())
                .build();

        return eventPublisher.publish(event).thenReturn(documentModel);
    }

    private Mono<DocumentModel> publishTitleUpdatedEvent(DocumentModel documentModel, String oldTitle) {
        DocumentEvents.DocumentTitleUpdated event = DocumentEvents.DocumentTitleUpdated.builder()
                .documentId(documentModel.getId())
                .oldTitle(oldTitle)
                .newTitle(documentModel.getTitle())
                .modifiedBy(documentModel.getLastModifiedBy())
                .build();

        return eventPublisher.publish(event).thenReturn(documentModel);
    }

    private Mono<DocumentModel> publishDocumentPublishedEvent(DocumentModel documentModel) {
        DocumentEvents.DocumentPublished event = DocumentEvents.DocumentPublished.builder()
                .documentId(documentModel.getId())
                .title(documentModel.getTitle())
                .publishedBy(documentModel.getLastModifiedBy())
                .publishedAt(documentModel.getPublishedAt())
                .build();

        return eventPublisher.publish(event).thenReturn(documentModel);
    }

    private Mono<DocumentModel> publishDocumentArchivedEvent(DocumentModel documentModel, UserId archivedBy, String reason) {
        DocumentEvents.DocumentArchived event = DocumentEvents.DocumentArchived.builder()
                .documentId(documentModel.getId())
                .title(documentModel.getTitle())
                .archivedBy(archivedBy)
                .archivedAt(documentModel.getArchivedAt())
                .reason(reason)
                .build();

        return eventPublisher.publish(event).thenReturn(documentModel);
    }

    private Mono<DocumentModel> publishDocumentDeletedEvent(DocumentModel documentModel, UserId deletedBy) {
        DocumentEvents.DocumentDeleted event = DocumentEvents.DocumentDeleted.builder()
                .documentId(documentModel.getId())
                .title(documentModel.getTitle())
                .deletedBy(deletedBy)
                .deletedAt(LocalDateTime.now())
                .build();

        return eventPublisher.publish(event).thenReturn(documentModel);
    }

    private Mono<DocumentModel> publishCollaboratorAddedEvent(DocumentModel documentModel, UserId collaboratorId, UserId addedBy) {
        DocumentEvents.DocumentCollaboratorAdded event = DocumentEvents.DocumentCollaboratorAdded.builder()
                .documentId(documentModel.getId())
                .collaboratorId(collaboratorId)
                .addedBy(addedBy)
                .permission("WRITE")
                .build();

        return eventPublisher.publish(event).thenReturn(documentModel);
    }

    private Mono<DocumentModel> publishCollaboratorRemovedEvent(DocumentModel documentModel, UserId collaboratorId, UserId removedBy) {
        DocumentEvents.DocumentCollaboratorRemoved event = DocumentEvents.DocumentCollaboratorRemoved.builder()
                .documentId(documentModel.getId())
                .collaboratorId(collaboratorId)
                .removedBy(removedBy)
                .build();

        return eventPublisher.publish(event).thenReturn(documentModel);
    }

    private Mono<DocumentModel> publishDocumentViewedEvent(DocumentModel documentModel, UserId viewedBy, String ipAddress) {
        DocumentEvents.DocumentViewed event = DocumentEvents.DocumentViewed.builder()
                .documentId(documentModel.getId())
                .viewedBy(viewedBy)
                .viewedAt(LocalDateTime.now())
                .ipAddress(ipAddress)
                .build();

        return eventPublisher.publish(event).thenReturn(documentModel);
    }

    private Mono<DocumentModel> publishDocumentLikedEvent(DocumentModel documentModel, UserId likedBy) {
        DocumentEvents.DocumentLiked event = DocumentEvents.DocumentLiked.builder()
                .documentId(documentModel.getId())
                .likedBy(likedBy)
                .likedAt(LocalDateTime.now())
                .build();

        return eventPublisher.publish(event).thenReturn(documentModel);
    }

    private Mono<DocumentModel> publishTagsUpdatedEvent(DocumentModel documentModel, Set<String> oldTags) {
        DocumentEvents.DocumentTagsUpdated event = DocumentEvents.DocumentTagsUpdated.builder()
                .documentId(documentModel.getId())
                .oldTags(oldTags)
                .newTags(documentModel.getTags())
                .modifiedBy(documentModel.getLastModifiedBy())
                .build();

        return eventPublisher.publish(event).thenReturn(documentModel);
    }

    /**
     * Records auxiliares
     */
    private record DocumentWithOldTitle(DocumentModel documentModel, String oldTitle) {}
    private record DocumentWithOldTags(DocumentModel documentModel, Set<String> oldTags) {}
}
