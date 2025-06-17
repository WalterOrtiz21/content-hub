package com.contentshub.domain.model;

import com.contentshub.domain.valueobject.DocumentContent;
import com.contentshub.domain.valueobject.DocumentStatus;
import com.contentshub.domain.valueobject.DocumentType;
import com.contentshub.domain.valueobject.UserId;
import lombok.Builder;
import lombok.Value;
import lombok.With;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document as MongoDocument;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Entidad de dominio Document
 * Representa un documento almacenado en MongoDB con contenido flexible
 */
@Value
@Builder(toBuilder = true)
@With
@MongoDocument(collection = "documents")
public class Document {

    @Id
    String id; // MongoDB ObjectId

    @Field("title")
    String title;

    @Field("content")
    DocumentContent content;

    @Field("owner_id")
    UserId ownerId;

    @Field("status")
    DocumentStatus status;

    @Field("document_type")
    DocumentType documentType;

    @Field("tags")
    Set<String> tags;

    @Field("version")
    Integer version;

    @Field("is_public")
    Boolean isPublic;

    @Field("collaborators")
    Set<UserId> collaborators;

    @Field("view_count")
    Long viewCount;

    @Field("like_count")
    Long likeCount;

    @CreatedDate
    @Field("created_at")
    LocalDateTime createdAt;

    @LastModifiedDate
    @Field("updated_at")
    LocalDateTime updatedAt;

    @Field("last_modified_by")
    UserId lastModifiedBy;

    @Field("published_at")
    LocalDateTime publishedAt;

    @Field("archived_at")
    LocalDateTime archivedAt;

    // Metadatos adicionales
    @Field("metadata")
    DocumentMetadata metadata;

    /**
     * Factory method para crear un nuevo documento
     */
    public static Document createNew(String title, DocumentContent content, UserId ownerId, DocumentType documentType) {
        return Document.builder()
                .title(title)
                .content(content)
                .ownerId(ownerId)
                .documentType(documentType)
                .status(DocumentStatus.DRAFT)
                .version(1)
                .isPublic(false)
                .viewCount(0L)
                .likeCount(0L)
                .lastModifiedBy(ownerId)
                .tags(Set.of())
                .collaborators(Set.of())
                .metadata(DocumentMetadata.createDefault())
                .build();
    }

    /**
     * Factory method para crear desde template
     */
    public static Document createFromTemplate(String title, DocumentContent templateContent,
                                              UserId ownerId, DocumentType documentType) {
        return Document.builder()
                .title(title)
                .content(templateContent)
                .ownerId(ownerId)
                .documentType(documentType)
                .status(DocumentStatus.DRAFT)
                .version(1)
                .isPublic(false)
                .viewCount(0L)
                .likeCount(0L)
                .lastModifiedBy(ownerId)
                .tags(Set.of())
                .collaborators(Set.of())
                .metadata(DocumentMetadata.createFromTemplate())
                .build();
    }

    /**
     * Actualizar contenido del documento
     */
    public Document updateContent(DocumentContent newContent, UserId modifiedBy) {
        return this.withContent(newContent)
                .withLastModifiedBy(modifiedBy)
                .withVersion(version + 1);
    }

    /**
     * Actualizar título
     */
    public Document updateTitle(String newTitle, UserId modifiedBy) {
        return this.withTitle(newTitle)
                .withLastModifiedBy(modifiedBy);
    }

    /**
     * Publicar documento
     */
    public Document publish(UserId publishedBy) {
        return this.withStatus(DocumentStatus.PUBLISHED)
                .withPublishedAt(LocalDateTime.now())
                .withLastModifiedBy(publishedBy);
    }

    /**
     * Archivar documento
     */
    public Document archive(UserId archivedBy) {
        return this.withStatus(DocumentStatus.ARCHIVED)
                .withArchivedAt(LocalDateTime.now())
                .withLastModifiedBy(archivedBy);
    }

    /**
     * Restaurar documento archivado
     */
    public Document restore(UserId restoredBy) {
        return this.withStatus(DocumentStatus.DRAFT)
                .withArchivedAt(null)
                .withLastModifiedBy(restoredBy);
    }

    /**
     * Hacer público
     */
    public Document makePublic(UserId modifiedBy) {
        return this.withIsPublic(true)
                .withLastModifiedBy(modifiedBy);
    }

    /**
     * Hacer privado
     */
    public Document makePrivate(UserId modifiedBy) {
        return this.withIsPublic(false)
                .withLastModifiedBy(modifiedBy);
    }

    /**
     * Agregar colaborador
     */
    public Document addCollaborator(UserId collaboratorId, UserId modifiedBy) {
        Set<UserId> newCollaborators = new java.util.HashSet<>(collaborators != null ? collaborators : Set.of());
        newCollaborators.add(collaboratorId);
        return this.withCollaborators(newCollaborators)
                .withLastModifiedBy(modifiedBy);
    }

    /**
     * Remover colaborador
     */
    public Document removeCollaborator(UserId collaboratorId, UserId modifiedBy) {
        Set<UserId> newCollaborators = new java.util.HashSet<>(collaborators != null ? collaborators : Set.of());
        newCollaborators.remove(collaboratorId);
        return this.withCollaborators(newCollaborators)
                .withLastModifiedBy(modifiedBy);
    }

    /**
     * Agregar tags
     */
    public Document addTags(Set<String> newTags, UserId modifiedBy) {
        Set<String> allTags = new java.util.HashSet<>(tags != null ? tags : Set.of());
        allTags.addAll(newTags);
        return this.withTags(allTags)
                .withLastModifiedBy(modifiedBy);
    }

    /**
     * Remover tags
     */
    public Document removeTags(Set<String> tagsToRemove, UserId modifiedBy) {
        Set<String> newTags = new java.util.HashSet<>(tags != null ? tags : Set.of());
        newTags.removeAll(tagsToRemove);
        return this.withTags(newTags)
                .withLastModifiedBy(modifiedBy);
    }

    /**
     * Incrementar contador de vistas
     */
    public Document incrementViewCount() {
        return this.withViewCount((viewCount != null ? viewCount : 0L) + 1);
    }

    /**
     * Incrementar contador de likes
     */
    public Document incrementLikeCount() {
        return this.withLikeCount((likeCount != null ? likeCount : 0L) + 1);
    }

    /**
     * Decrementar contador de likes
     */
    public Document decrementLikeCount() {
        long currentCount = likeCount != null ? likeCount : 0L;
        return this.withLikeCount(Math.max(0L, currentCount - 1));
    }

    /**
     * Verificar si el usuario puede leer el documento
     */
    public boolean canRead(UserId userId) {
        // El propietario siempre puede leer
        if (ownerId.equals(userId)) {
            return true;
        }

        // Si es público, cualquiera puede leer
        if (Boolean.TRUE.equals(isPublic) && status == DocumentStatus.PUBLISHED) {
            return true;
        }

        // Si es colaborador puede leer
        return collaborators != null && collaborators.contains(userId);
    }

    /**
     * Verificar si el usuario puede escribir el documento
     */
    public boolean canWrite(UserId userId) {
        // El propietario siempre puede escribir
        if (ownerId.equals(userId)) {
            return true;
        }

        // Los colaboradores pueden escribir
        return collaborators != null && collaborators.contains(userId);
    }

    /**
     * Verificar si el documento está publicado
     */
    public boolean isPublished() {
        return status == DocumentStatus.PUBLISHED;
    }

    /**
     * Verificar si el documento está archivado
     */
    public boolean isArchived() {
        return status == DocumentStatus.ARCHIVED;
    }

    /**
     * Verificar si el documento es borrador
     */
    public boolean isDraft() {
        return status == DocumentStatus.DRAFT;
    }

    /**
     * Metadatos del documento
     */
    @Value
    @Builder(toBuilder = true)
    @With
    public static class DocumentMetadata {
        String language;
        Integer readingTimeMinutes;
        Integer wordCount;
        Boolean hasImages;
        Boolean hasLinks;
        List<String> headings;
        String excerpt;

        public static DocumentMetadata createDefault() {
            return DocumentMetadata.builder()
                    .language("es")
                    .readingTimeMinutes(0)
                    .wordCount(0)
                    .hasImages(false)
                    .hasLinks(false)
                    .headings(List.of())
                    .excerpt("")
                    .build();
        }

        public static DocumentMetadata createFromTemplate() {
            return createDefault();
        }
    }
}
