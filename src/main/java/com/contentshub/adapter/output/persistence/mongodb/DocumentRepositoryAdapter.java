package com.contentshub.adapter.output.persistence.mongodb;

import com.contentshub.application.port.output.DocumentRepositoryPort;
import com.contentshub.domain.model.Document;
import com.contentshub.domain.valueobject.DocumentStatus;
import com.contentshub.domain.valueobject.DocumentType;
import com.contentshub.domain.valueobject.UserId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Implementación del repositorio de documentos usando MongoDB
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class DocumentRepositoryAdapter implements DocumentRepositoryPort {

    private final ReactiveMongoTemplate mongoTemplate;

    @Override
    public Mono<Document> save(Document document) {
        return mongoTemplate.save(document)
                .doOnSuccess(savedDoc -> log.debug("Document saved: {}", savedDoc.getId()))
                .doOnError(error -> log.error("Error saving document: {}", error.getMessage()));
    }

    @Override
    public Mono<Document> findById(String documentId) {
        return mongoTemplate.findById(documentId, Document.class)
                .doOnSuccess(doc -> {
                    if (doc != null) {
                        log.debug("Document found: {}", documentId);
                    } else {
                        log.debug("Document not found: {}", documentId);
                    }
                })
                .doOnError(error -> log.error("Error finding document {}: {}", documentId, error.getMessage()));
    }

    @Override
    public Mono<Void> deleteById(String documentId) {
        return mongoTemplate.remove(Query.query(Criteria.where("id").is(documentId)), Document.class)
                .then()
                .doOnSuccess(unused -> log.debug("Document deleted: {}", documentId))
                .doOnError(error -> log.error("Error deleting document {}: {}", documentId, error.getMessage()));
    }

    @Override
    public Flux<Document> findByOwnerId(UserId ownerId) {
        Query query = Query.query(Criteria.where("ownerId").is(ownerId));
        query.with(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "updatedAt"));

        return mongoTemplate.find(query, Document.class)
                .doOnNext(doc -> log.debug("Found document for owner {}: {}", ownerId, doc.getId()));
    }

    @Override
    public Flux<Document> findByOwnerIdAndStatus(UserId ownerId, DocumentStatus status) {
        Query query = Query.query(Criteria.where("ownerId").is(ownerId)
                .and("status").is(status));
        query.with(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "updatedAt"));

        return mongoTemplate.find(query, Document.class);
    }

    @Override
    public Flux<Document> findPublicDocuments() {
        Query query = Query.query(Criteria.where("isPublic").is(true)
                .and("status").is(DocumentStatus.PUBLISHED));
        query.with(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "publishedAt"));

        return mongoTemplate.find(query, Document.class);
    }

    @Override
    public Flux<Document> findPublicDocumentsByType(DocumentType documentType) {
        Query query = Query.query(Criteria.where("isPublic").is(true)
                .and("status").is(DocumentStatus.PUBLISHED)
                .and("documentType").is(documentType));
        query.with(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "publishedAt"));

        return mongoTemplate.find(query, Document.class);
    }

    @Override
    public Flux<Document> findByTitleContaining(String titlePattern) {
        Query query = Query.query(Criteria.where("title").regex(titlePattern, "i"));
        query.with(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "updatedAt"));

        return mongoTemplate.find(query, Document.class);
    }

    @Override
    public Flux<Document> findByContentContaining(String contentPattern) {
        Query query = Query.query(Criteria.where("$text").is(contentPattern));

        return mongoTemplate.find(query, Document.class);
    }

    @Override
    public Flux<Document> findByTags(Set<String> tags) {
        Query query = Query.query(Criteria.where("tags").in(tags));
        query.with(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "updatedAt"));

        return mongoTemplate.find(query, Document.class);
    }

    @Override
    public Flux<Document> findByCollaboratorId(UserId collaboratorId) {
        Query query = Query.query(Criteria.where("collaborators").in(collaboratorId));
        query.with(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "updatedAt"));

        return mongoTemplate.find(query, Document.class);
    }

    @Override
    public Flux<Document> findReadableByUser(UserId userId) {
        Criteria criteria = new Criteria().orOperator(
                // Documents owned by user
                Criteria.where("ownerId").is(userId),
                // Public published documents
                Criteria.where("isPublic").is(true).and("status").is(DocumentStatus.PUBLISHED),
                // Documents where user is collaborator
                Criteria.where("collaborators").in(userId)
        );

        Query query = Query.query(criteria);
        query.with(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "updatedAt"));

        return mongoTemplate.find(query, Document.class);
    }

    @Override
    public Flux<Document> findWritableByUser(UserId userId) {
        Criteria criteria = new Criteria().orOperator(
                // Documents owned by user
                Criteria.where("ownerId").is(userId),
                // Documents where user is collaborator
                Criteria.where("collaborators").in(userId)
        );

        Query query = Query.query(criteria);
        query.with(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "updatedAt"));

        return mongoTemplate.find(query, Document.class);
    }

    @Override
    public Flux<Document> findRecentByUser(UserId userId, int limit) {
        Criteria criteria = new Criteria().orOperator(
                Criteria.where("ownerId").is(userId),
                Criteria.where("collaborators").in(userId)
        );

        Query query = Query.query(criteria).limit(limit);
        query.with(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "updatedAt"));

        return mongoTemplate.find(query, Document.class);
    }

    @Override
    public Flux<Document> findMostViewed(int limit) {
        Query query = Query.query(Criteria.where("status").is(DocumentStatus.PUBLISHED))
                .limit(limit);
        query.with(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "viewCount"));

        return mongoTemplate.find(query, Document.class);
    }

    @Override
    public Flux<Document> findMostLiked(int limit) {
        Query query = Query.query(Criteria.where("status").is(DocumentStatus.PUBLISHED))
                .limit(limit);
        query.with(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "likeCount"));

        return mongoTemplate.find(query, Document.class);
    }

    @Override
    public Flux<Document> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end) {
        Query query = Query.query(Criteria.where("createdAt").gte(start).lte(end));
        query.with(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));

        return mongoTemplate.find(query, Document.class);
    }

    @Override
    public Flux<Document> findRecentlyModified(LocalDateTime since) {
        Query query = Query.query(Criteria.where("updatedAt").gte(since));
        query.with(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "updatedAt"));

        return mongoTemplate.find(query, Document.class);
    }

    @Override
    public Mono<Long> countByOwnerId(UserId ownerId) {
        Query query = Query.query(Criteria.where("ownerId").is(ownerId));
        return mongoTemplate.count(query, Document.class);
    }

    @Override
    public Mono<Long> countByStatus(DocumentStatus status) {
        Query query = Query.query(Criteria.where("status").is(status));
        return mongoTemplate.count(query, Document.class);
    }

    @Override
    public Mono<Long> countPublicDocuments() {
        Query query = Query.query(Criteria.where("isPublic").is(true)
                .and("status").is(DocumentStatus.PUBLISHED));
        return mongoTemplate.count(query, Document.class);
    }

    @Override
    public Mono<Document> incrementViewCount(String documentId) {
        Query query = Query.query(Criteria.where("id").is(documentId));
        Update update = new Update().inc("viewCount", 1);

        return mongoTemplate.findAndModify(query, update, Document.class)
                .doOnSuccess(doc -> log.debug("View count incremented for document: {}", documentId));
    }

    @Override
    public Mono<Document> incrementLikeCount(String documentId) {
        Query query = Query.query(Criteria.where("id").is(documentId));
        Update update = new Update().inc("likeCount", 1);

        return mongoTemplate.findAndModify(query, update, Document.class)
                .doOnSuccess(doc -> log.debug("Like count incremented for document: {}", documentId));
    }

    @Override
    public Mono<Document> decrementLikeCount(String documentId) {
        Query query = Query.query(Criteria.where("id").is(documentId));
        Update update = new Update().inc("likeCount", -1);

        return mongoTemplate.findAndModify(query, update, Document.class)
                .doOnSuccess(doc -> log.debug("Like count decremented for document: {}", documentId));
    }

    @Override
    public Flux<Document> findWithPagination(int page, int size) {
        Query query = new Query()
                .skip(page * size)
                .limit(size);
        query.with(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "updatedAt"));

        return mongoTemplate.find(query, Document.class);
    }

    @Override
    public Flux<Document> findByOwnerIdWithPagination(UserId ownerId, int page, int size) {
        Query query = Query.query(Criteria.where("ownerId").is(ownerId))
                .skip(page * size)
                .limit(size);
        query.with(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "updatedAt"));

        return mongoTemplate.find(query, Document.class);
    }

    @Override
    public Flux<Document> findByCriteria(DocumentSearchCriteria criteria) {
        Query query = buildQueryFromCriteria(criteria);

        if (criteria.page() != null && criteria.size() != null) {
            query.skip(criteria.page() * criteria.size()).limit(criteria.size());
        }

        query.with(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "updatedAt"));

        return mongoTemplate.find(query, Document.class);
    }

    @Override
    public Flux<Document> findArchivedDocuments(LocalDateTime cutoffDate) {
        Query query = Query.query(Criteria.where("status").is(DocumentStatus.ARCHIVED)
                .and("archivedAt").lte(cutoffDate));
        query.with(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.ASC, "archivedAt"));

        return mongoTemplate.find(query, Document.class);
    }

    @Override
    public Mono<DocumentStatistics> getDocumentStatistics(String documentId) {
        return findById(documentId)
                .map(doc -> new DocumentStatistics(
                        doc.getId(),
                        doc.getViewCount() != null ? doc.getViewCount() : 0L,
                        doc.getLikeCount() != null ? doc.getLikeCount() : 0L,
                        doc.getCollaborators() != null ? doc.getCollaborators().size() : 0,
                        doc.getUpdatedAt(),
                        doc.getContent() != null ? doc.getContent().getWordCount() : 0,
                        (long) (doc.getContent() != null ? doc.getContent().getLength() : 0)
                ));
    }

    @Override
    public Flux<Document> findRelatedDocuments(String documentId, int limit) {
        return findById(documentId)
                .flatMapMany(document -> {
                    if (document.getTags() == null || document.getTags().isEmpty()) {
                        return Flux.empty();
                    }

                    Query query = Query.query(Criteria.where("tags").in(document.getTags())
                                    .and("id").ne(documentId)
                                    .and("status").is(DocumentStatus.PUBLISHED))
                            .limit(limit);
                    query.with(org.springframework.data.domain.Sort.by(
                            org.springframework.data.domain.Sort.Direction.DESC, "viewCount"));

                    return mongoTemplate.find(query, Document.class);
                });
    }

    @Override
    public Mono<Boolean> canUserAccessDocument(String documentId, UserId userId) {
        return findById(documentId)
                .map(document -> document.canRead(userId))
                .defaultIfEmpty(false);
    }

    @Override
    public Flux<DocumentVersion> findDocumentVersions(String documentId) {
        // Esta implementación sería más compleja en un sistema real
        // Por ahora retornamos versiones simuladas
        return Flux.empty();
    }

    /**
     * Construir query desde criterios de búsqueda
     */
    private Query buildQueryFromCriteria(DocumentSearchCriteria criteria) {
        Criteria mongoCriteria = new Criteria();

        if (criteria.titlePattern() != null && !criteria.titlePattern().trim().isEmpty()) {
            mongoCriteria = mongoCriteria.and("title").regex(criteria.titlePattern(), "i");
        }

        if (criteria.contentPattern() != null && !criteria.contentPattern().trim().isEmpty()) {
            mongoCriteria = mongoCriteria.and("$text").is(criteria.contentPattern());
        }

        if (criteria.tags() != null && !criteria.tags().isEmpty()) {
            mongoCriteria = mongoCriteria.and("tags").in(criteria.tags());
        }

        if (criteria.documentType() != null) {
            mongoCriteria = mongoCriteria.and("documentType").is(criteria.documentType());
        }

        if (criteria.status() != null) {
            mongoCriteria = mongoCriteria.and("status").is(criteria.status());
        }

        if (criteria.ownerId() != null) {
            mongoCriteria = mongoCriteria.and("ownerId").is(criteria.ownerId());
        }

        if (criteria.isPublic() != null) {
            mongoCriteria = mongoCriteria.and("isPublic").is(criteria.isPublic());
        }

        if (criteria.createdAfter() != null) {
            mongoCriteria = mongoCriteria.and("createdAt").gte(criteria.createdAfter());
        }

        if (criteria.createdBefore() != null) {
            mongoCriteria = mongoCriteria.and("createdAt").lte(criteria.createdBefore());
        }

        return Query.query(mongoCriteria);
    }
}
