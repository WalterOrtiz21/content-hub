package com.contentshub.adapter.input.web;

import com.contentshub.application.port.input.DocumentManagementUseCase;
import com.contentshub.domain.valueobject.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Controlador REST para gestión de documentos
 */
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Document Management", description = "Endpoints para gestión de documentos")
@SecurityRequirement(name = "Bearer Authentication")
public class DocumentController {

    private final DocumentManagementUseCase documentManagementUseCase;

    @PostMapping
    @Operation(
            summary = "Crear nuevo documento",
            description = "Crea un nuevo documento",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Documento creado exitosamente"),
                    @ApiResponse(responseCode = "400", description = "Datos inválidos")
            }
    )
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<DocumentResponse> createDocument(
            @Valid @RequestBody CreateDocumentRequest request,
            @AuthenticationPrincipal Object principal) {

        log.debug("Creating document: {}", request.title());

        UserId ownerId = extractUserIdFromPrincipal(principal);

        DocumentManagementUseCase.CreateDocumentCommand command =
                new DocumentManagementUseCase.CreateDocumentCommand(
                        request.title(),
                        mapToDocumentContent(request.content()),
                        DocumentType.fromValue(request.documentType()),
                        ownerId,
                        request.isPublic(),
                        request.tags()
                );

        return documentManagementUseCase.createDocument(command)
                .map(this::toDocumentResponse)
                .doOnSuccess(response -> log.info("Document created: {} by user: {}",
                        response.title(), response.ownerId()))
                .doOnError(error -> log.error("Error creating document {}: {}",
                        request.title(), error.getMessage()));
    }

    @PostMapping("/from-template")
    @Operation(
            summary = "Crear documento desde template",
            description = "Crea un nuevo documento basado en un template",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Documento creado desde template"),
                    @ApiResponse(responseCode = "404", description = "Template no encontrado")
            }
    )
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<DocumentResponse> createDocumentFromTemplate(
            @Valid @RequestBody CreateFromTemplateRequest request,
            @AuthenticationPrincipal Object principal) {

        log.debug("Creating document from template: {}", request.templateId());

        UserId ownerId = extractUserIdFromPrincipal(principal);

        DocumentManagementUseCase.CreateFromTemplateCommand command =
                new DocumentManagementUseCase.CreateFromTemplateCommand(
                        request.title(),
                        request.templateId(),
                        ownerId,
                        request.isPublic()
                );

        return documentManagementUseCase.createDocumentFromTemplate(command)
                .map(this::toDocumentResponse)
                .doOnSuccess(response -> log.info("Document created from template: {}", response.title()))
                .doOnError(error -> log.error("Error creating document from template: {}", error.getMessage()));
    }

    @GetMapping("/{documentId}")
    @Operation(
            summary = "Obtener documento por ID",
            description = "Retorna un documento específico",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Documento encontrado"),
                    @ApiResponse(responseCode = "404", description = "Documento no encontrado"),
                    @ApiResponse(responseCode = "403", description = "Sin permisos para ver este documento")
            }
    )
    public Mono<DocumentResponse> getDocumentById(
            @Parameter(description = "ID del documento") @PathVariable String documentId,
            @AuthenticationPrincipal Object principal,
            ServerHttpRequest request) {

        log.debug("Getting document: {}", documentId);

        UserId requestingUserId = extractUserIdFromPrincipal(principal);

        return documentManagementUseCase.getDocumentById(documentId, requestingUserId)
                .flatMap(response -> {
                    // Registrar vista del documento
                    String ipAddress = getClientIp(request);
                    return documentManagementUseCase.viewDocument(documentId, requestingUserId, ipAddress)
                            .thenReturn(response);
                })
                .map(this::toDocumentResponse)
                .doOnSuccess(response -> log.debug("Document retrieved: {}", response.title()))
                .doOnError(error -> log.error("Error getting document {}: {}", documentId, error.getMessage()));
    }

    @PutMapping("/{documentId}/content")
    @Operation(
            summary = "Actualizar contenido del documento",
            description = "Actualiza el contenido de un documento",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Contenido actualizado exitosamente"),
                    @ApiResponse(responseCode = "404", description = "Documento no encontrado"),
                    @ApiResponse(responseCode = "403", description = "Sin permisos para editar este documento")
            }
    )
    @PreAuthorize("@documentSecurityService.canEditDocument(authentication, #documentId)")
    public Mono<DocumentResponse> updateDocumentContent(
            @Parameter(description = "ID del documento") @PathVariable String documentId,
            @Valid @RequestBody UpdateContentRequest request,
            @AuthenticationPrincipal Object principal) {

        log.debug("Updating content for document: {}", documentId);

        UserId modifiedBy = extractUserIdFromPrincipal(principal);

        DocumentManagementUseCase.UpdateContentCommand command =
                new DocumentManagementUseCase.UpdateContentCommand(
                        documentId,
                        mapToDocumentContent(request.content()),
                        modifiedBy
                );

        return documentManagementUseCase.updateDocumentContent(command)
                .map(this::toDocumentResponse)
                .doOnSuccess(response -> log.info("Document content updated: {}", response.title()))
                .doOnError(error -> log.error("Error updating document content {}: {}",
                        documentId, error.getMessage()));
    }

    @PutMapping("/{documentId}/title")
    @Operation(
            summary = "Actualizar título del documento",
            description = "Actualiza el título de un documento",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Título actualizado exitosamente"),
                    @ApiResponse(responseCode = "404", description = "Documento no encontrado"),
                    @ApiResponse(responseCode = "403", description = "Sin permisos para editar este documento")
            }
    )
    @PreAuthorize("@documentSecurityService.canEditDocument(authentication, #documentId)")
    public Mono<DocumentResponse> updateDocumentTitle(
            @Parameter(description = "ID del documento") @PathVariable String documentId,
            @Valid @RequestBody UpdateTitleRequest request,
            @AuthenticationPrincipal Object principal) {

        log.debug("Updating title for document: {}", documentId);

        UserId modifiedBy = extractUserIdFromPrincipal(principal);

        DocumentManagementUseCase.UpdateTitleCommand command =
                new DocumentManagementUseCase.UpdateTitleCommand(
                        documentId,
                        request.newTitle(),
                        modifiedBy
                );

        return documentManagementUseCase.updateDocumentTitle(command)
                .map(this::toDocumentResponse)
                .doOnSuccess(response -> log.info("Document title updated: {}", response.title()))
                .doOnError(error -> log.error("Error updating document title {}: {}",
                        documentId, error.getMessage()));
    }

    @PutMapping("/{documentId}/publish")
    @Operation(
            summary = "Publicar documento",
            description = "Cambia el estado del documento a publicado",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Documento publicado exitosamente"),
                    @ApiResponse(responseCode = "404", description = "Documento no encontrado"),
                    @ApiResponse(responseCode = "409", description = "Documento ya está publicado")
            }
    )
    @PreAuthorize("@documentSecurityService.canEditDocument(authentication, #documentId)")
    public Mono<DocumentResponse> publishDocument(
            @Parameter(description = "ID del documento") @PathVariable String documentId,
            @AuthenticationPrincipal Object principal) {

        log.debug("Publishing document: {}", documentId);

        UserId publishedBy = extractUserIdFromPrincipal(principal);

        return documentManagementUseCase.publishDocument(documentId, publishedBy)
                .map(this::toDocumentResponse)
                .doOnSuccess(response -> log.info("Document published: {}", response.title()))
                .doOnError(error -> log.error("Error publishing document {}: {}", documentId, error.getMessage()));
    }

    @PutMapping("/{documentId}/archive")
    @Operation(
            summary = "Archivar documento",
            description = "Cambia el estado del documento a archivado",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Documento archivado exitosamente"),
                    @ApiResponse(responseCode = "404", description = "Documento no encontrado")
            }
    )
    @PreAuthorize("@documentSecurityService.canEditDocument(authentication, #documentId)")
    public Mono<DocumentResponse> archiveDocument(
            @Parameter(description = "ID del documento") @PathVariable String documentId,
            @Valid @RequestBody ArchiveDocumentRequest request,
            @AuthenticationPrincipal Object principal) {

        log.debug("Archiving document: {}", documentId);

        UserId archivedBy = extractUserIdFromPrincipal(principal);

        return documentManagementUseCase.archiveDocument(documentId, archivedBy, request.reason())
                .map(this::toDocumentResponse)
                .doOnSuccess(response -> log.info("Document archived: {}", response.title()))
                .doOnError(error -> log.error("Error archiving document {}: {}", documentId, error.getMessage()));
    }

    @PutMapping("/{documentId}/restore")
    @Operation(
            summary = "Restaurar documento archivado",
            description = "Restaura un documento archivado a estado borrador",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Documento restaurado exitosamente"),
                    @ApiResponse(responseCode = "404", description = "Documento no encontrado"),
                    @ApiResponse(responseCode = "400", description = "Documento no está archivado")
            }
    )
    @PreAuthorize("@documentSecurityService.canEditDocument(authentication, #documentId)")
    public Mono<DocumentResponse> restoreDocument(
            @Parameter(description = "ID del documento") @PathVariable String documentId,
            @AuthenticationPrincipal Object principal) {

        log.debug("Restoring document: {}", documentId);

        UserId restoredBy = extractUserIdFromPrincipal(principal);

        return documentManagementUseCase.restoreDocument(documentId, restoredBy)
                .map(this::toDocumentResponse)
                .doOnSuccess(response -> log.info("Document restored: {}", response.title()))
                .doOnError(error -> log.error("Error restoring document {}: {}", documentId, error.getMessage()));
    }

    @DeleteMapping("/{documentId}")
    @Operation(
            summary = "Eliminar documento",
            description = "Elimina permanentemente un documento",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Documento eliminado exitosamente"),
                    @ApiResponse(responseCode = "404", description = "Documento no encontrado"),
                    @ApiResponse(responseCode = "403", description = "Sin permisos para eliminar este documento")
            }
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@documentSecurityService.canDeleteDocument(authentication, #documentId)")
    public Mono<Void> deleteDocument(
            @Parameter(description = "ID del documento") @PathVariable String documentId,
            @AuthenticationPrincipal Object principal) {

        log.debug("Deleting document: {}", documentId);

        UserId deletedBy = extractUserIdFromPrincipal(principal);

        return documentManagementUseCase.deleteDocument(documentId, deletedBy)
                .doOnSuccess(unused -> log.info("Document deleted: {}", documentId))
                .doOnError(error -> log.error("Error deleting document {}: {}", documentId, error.getMessage()));
    }

    @GetMapping("/my")
    @Operation(
            summary = "Obtener mis documentos",
            description = "Retorna los documentos del usuario autenticado",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Lista de documentos del usuario")
            }
    )
    public Flux<DocumentSummaryResponse> getMyDocuments(
            @Parameter(description = "Número de página") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamaño de página") @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Object principal) {

        log.debug("Getting my documents with pagination: page={}, size={}", page, size);

        UserId userId = extractUserIdFromPrincipal(principal);

        return documentManagementUseCase.getUserDocuments(userId, page, size)
                .map(this::toDocumentSummaryResponse)
                .doOnNext(doc -> log.debug("User document: {}", doc.title()))
                .doOnError(error -> log.error("Error getting user documents: {}", error.getMessage()));
    }

    @GetMapping("/public")
    @Operation(
            summary = "Obtener documentos públicos",
            description = "Retorna los documentos públicos",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Lista de documentos públicos")
            }
    )
    public Flux<DocumentSummaryResponse> getPublicDocuments(
            @Parameter(description = "Número de página") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamaño de página") @RequestParam(defaultValue = "20") int size) {

        log.debug("Getting public documents with pagination: page={}, size={}", page, size);

        return documentManagementUseCase.getPublicDocuments(page, size)
                .map(this::toDocumentSummaryResponse)
                .doOnNext(doc -> log.debug("Public document: {}", doc.title()))
                .doOnError(error -> log.error("Error getting public documents: {}", error.getMessage()));
    }

    @GetMapping("/search")
    @Operation(
            summary = "Buscar documentos",
            description = "Busca documentos por diferentes criterios",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Resultados de búsqueda")
            }
    )
    public Flux<DocumentSummaryResponse> searchDocuments(
            @Parameter(description = "Texto a buscar") @RequestParam(required = false) String query,
            @Parameter(description = "Tags a buscar") @RequestParam(required = false) Set<String> tags,
            @Parameter(description = "Tipo de documento") @RequestParam(required = false) String documentType,
            @Parameter(description = "Estado del documento") @RequestParam(required = false) String status,
            @Parameter(description = "ID del propietario") @RequestParam(required = false) Long ownerId,
            @Parameter(description = "Solo documentos públicos") @RequestParam(defaultValue = "false") boolean onlyPublic,
            @Parameter(description = "Creado después de") @RequestParam(required = false) String createdAfter,
            @Parameter(description = "Creado antes de") @RequestParam(required = false) String createdBefore,
            @Parameter(description = "Número de página") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamaño de página") @RequestParam(defaultValue = "20") int size) {

        log.debug("Searching documents with query: {}", query);

        DocumentManagementUseCase.DocumentSearchCommand command =
                new DocumentManagementUseCase.DocumentSearchCommand(
                        query,
                        tags,
                        documentType != null ? DocumentType.fromValue(documentType) : null,
                        status != null ? DocumentStatus.fromValue(status) : null,
                        ownerId != null ? UserId.of(ownerId) : null,
                        onlyPublic,
                        parseDateTime(createdAfter),
                        parseDateTime(createdBefore),
                        page,
                        size
                );

        return documentManagementUseCase.searchDocuments(command)
                .map(this::toDocumentSummaryResponse)
                .doOnNext(doc -> log.debug("Search result: {}", doc.title()))
                .doOnError(error -> log.error("Error searching documents: {}", error.getMessage()));
    }

    @PostMapping("/{documentId}/collaborators")
    @Operation(
            summary = "Agregar colaborador",
            description = "Agrega un colaborador al documento",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Colaborador agregado exitosamente"),
                    @ApiResponse(responseCode = "404", description = "Documento no encontrado")
            }
    )
    @PreAuthorize("@documentSecurityService.canEditDocument(authentication, #documentId)")
    public Mono<DocumentResponse> addCollaborator(
            @Parameter(description = "ID del documento") @PathVariable String documentId,
            @Valid @RequestBody AddCollaboratorRequest request,
            @AuthenticationPrincipal Object principal) {

        log.debug("Adding collaborator {} to document: {}", request.collaboratorId(), documentId);

        UserId addedBy = extractUserIdFromPrincipal(principal);

        return documentManagementUseCase.addCollaborator(
                        documentId,
                        UserId.of(request.collaboratorId()),
                        addedBy)
                .map(this::toDocumentResponse)
                .doOnSuccess(response -> log.info("Collaborator added to document: {}", response.title()))
                .doOnError(error -> log.error("Error adding collaborator to document {}: {}",
                        documentId, error.getMessage()));
    }

    @DeleteMapping("/{documentId}/collaborators/{collaboratorId}")
    @Operation(
            summary = "Remover colaborador",
            description = "Remueve un colaborador del documento",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Colaborador removido exitosamente"),
                    @ApiResponse(responseCode = "404", description = "Documento no encontrado")
            }
    )
    @PreAuthorize("@documentSecurityService.canEditDocument(authentication, #documentId)")
    public Mono<DocumentResponse> removeCollaborator(
            @Parameter(description = "ID del documento") @PathVariable String documentId,
            @Parameter(description = "ID del colaborador") @PathVariable Long collaboratorId,
            @AuthenticationPrincipal Object principal) {

        log.debug("Removing collaborator {} from document: {}", collaboratorId, documentId);

        UserId removedBy = extractUserIdFromPrincipal(principal);

        return documentManagementUseCase.removeCollaborator(
                        documentId,
                        UserId.of(collaboratorId),
                        removedBy)
                .map(this::toDocumentResponse)
                .doOnSuccess(response -> log.info("Collaborator removed from document: {}", response.title()))
                .doOnError(error -> log.error("Error removing collaborator from document {}: {}",
                        documentId, error.getMessage()));
    }

    @PutMapping("/{documentId}/visibility/public")
    @Operation(
            summary = "Hacer documento público",
            description = "Cambia la visibilidad del documento a público",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Documento hecho público exitosamente")
            }
    )
    @PreAuthorize("@documentSecurityService.canEditDocument(authentication, #documentId)")
    public Mono<DocumentResponse> makeDocumentPublic(
            @Parameter(description = "ID del documento") @PathVariable String documentId,
            @AuthenticationPrincipal Object principal) {

        log.debug("Making document public: {}", documentId);

        UserId modifiedBy = extractUserIdFromPrincipal(principal);

        return documentManagementUseCase.makeDocumentPublic(documentId, modifiedBy)
                .map(this::toDocumentResponse)
                .doOnSuccess(response -> log.info("Document made public: {}", response.title()))
                .doOnError(error -> log.error("Error making document public {}: {}", documentId, error.getMessage()));
    }

    @PutMapping("/{documentId}/visibility/private")
    @Operation(
            summary = "Hacer documento privado",
            description = "Cambia la visibilidad del documento a privado",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Documento hecho privado exitosamente")
            }
    )
    @PreAuthorize("@documentSecurityService.canEditDocument(authentication, #documentId)")
    public Mono<DocumentResponse> makeDocumentPrivate(
            @Parameter(description = "ID del documento") @PathVariable String documentId,
            @AuthenticationPrincipal Object principal) {

        log.debug("Making document private: {}", documentId);

        UserId modifiedBy = extractUserIdFromPrincipal(principal);

        return documentManagementUseCase.makeDocumentPrivate(documentId, modifiedBy)
                .map(this::toDocumentResponse)
                .doOnSuccess(response -> log.info("Document made private: {}", response.title()))
                .doOnError(error -> log.error("Error making document private {}: {}", documentId, error.getMessage()));
    }

    @PostMapping("/{documentId}/like")
    @Operation(
            summary = "Dar like a documento",
            description = "Incrementa el contador de likes del documento",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Like agregado exitosamente")
            }
    )
    public Mono<DocumentResponse> likeDocument(
            @Parameter(description = "ID del documento") @PathVariable String documentId,
            @AuthenticationPrincipal Object principal) {

        log.debug("Liking document: {}", documentId);

        UserId userId = extractUserIdFromPrincipal(principal);

        return documentManagementUseCase.likeDocument(documentId, userId)
                .map(this::toDocumentResponse)
                .doOnSuccess(response -> log.info("Document liked: {}", response.title()))
                .doOnError(error -> log.error("Error liking document {}: {}", documentId, error.getMessage()));
    }

    @DeleteMapping("/{documentId}/like")
    @Operation(
            summary = "Quitar like de documento",
            description = "Decrementa el contador de likes del documento",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Like removido exitosamente")
            }
    )
    public Mono<DocumentResponse> unlikeDocument(
            @Parameter(description = "ID del documento") @PathVariable String documentId,
            @AuthenticationPrincipal Object principal) {

        log.debug("Unliking document: {}", documentId);

        UserId userId = extractUserIdFromPrincipal(principal);

        return documentManagementUseCase.unlikeDocument(documentId, userId)
                .map(this::toDocumentResponse)
                .doOnSuccess(response -> log.info("Document unliked: {}", response.title()))
                .doOnError(error -> log.error("Error unliking document {}: {}", documentId, error.getMessage()));
    }

    @PostMapping("/{documentId}/duplicate")
    @Operation(
            summary = "Duplicar documento",
            description = "Crea una copia del documento",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Documento duplicado exitosamente"),
                    @ApiResponse(responseCode = "404", description = "Documento no encontrado")
            }
    )
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<DocumentResponse> duplicateDocument(
            @Parameter(description = "ID del documento") @PathVariable String documentId,
            @Valid @RequestBody DuplicateDocumentRequest request,
            @AuthenticationPrincipal Object principal) {

        log.debug("Duplicating document: {}", documentId);

        UserId userId = extractUserIdFromPrincipal(principal);

        return documentManagementUseCase.duplicateDocument(documentId, request.newTitle(), userId)
                .map(this::toDocumentResponse)
                .doOnSuccess(response -> log.info("Document duplicated: {}", response.title()))
                .doOnError(error -> log.error("Error duplicating document {}: {}", documentId, error.getMessage()));
    }

    @GetMapping("/{documentId}/export")
    @Operation(
            summary = "Exportar documento",
            description = "Exporta el documento en el formato especificado",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Documento exportado exitosamente"),
                    @ApiResponse(responseCode = "404", description = "Documento no encontrado")
            }
    )
    public Mono<ResponseEntity<byte[]>> exportDocument(
            @Parameter(description = "ID del documento") @PathVariable String documentId,
            @Parameter(description = "Formato de exportación") @RequestParam String format,
            @AuthenticationPrincipal Object principal) {

        log.debug("Exporting document: {} in format: {}", documentId, format);

        UserId userId = extractUserIdFromPrincipal(principal);
        DocumentManagementUseCase.ExportFormat exportFormat =
                DocumentManagementUseCase.ExportFormat.valueOf(format.toUpperCase());

        return documentManagementUseCase.exportDocument(documentId, exportFormat, userId)
                .map(response -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"" + response.fileName() + "\"")
                        .contentType(MediaType.parseMediaType(response.mimeType()))
                        .body(response.content()))
                .doOnSuccess(response -> log.info("Document exported: {}", documentId))
                .doOnError(error -> log.error("Error exporting document {}: {}", documentId, error.getMessage()));
    }

    @GetMapping("/{documentId}/statistics")
    @Operation(
            summary = "Obtener estadísticas del documento",
            description = "Retorna estadísticas detalladas del documento",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Estadísticas del documento"),
                    @ApiResponse(responseCode = "404", description = "Documento no encontrado")
            }
    )
    @PreAuthorize("@documentSecurityService.canViewDocument(authentication, #documentId)")
    public Mono<DocumentStatisticsResponse> getDocumentStatistics(
            @Parameter(description = "ID del documento") @PathVariable String documentId,
            @AuthenticationPrincipal Object principal) {

        log.debug("Getting statistics for document: {}", documentId);

        UserId requestingUserId = extractUserIdFromPrincipal(principal);

        return documentManagementUseCase.getDocumentStatistics(documentId, requestingUserId)
                .map(this::toDocumentStatisticsResponse)
                .doOnSuccess(stats -> log.debug("Document statistics retrieved for: {}", documentId))
                .doOnError(error -> log.error("Error getting document statistics {}: {}",
                        documentId, error.getMessage()));
    }

    @GetMapping("/{documentId}/related")
    @Operation(
            summary = "Obtener documentos relacionados",
            description = "Retorna documentos relacionados por tags similares",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Lista de documentos relacionados")
            }
    )
    public Flux<DocumentSummaryResponse> getRelatedDocuments(
            @Parameter(description = "ID del documento") @PathVariable String documentId,
            @Parameter(description = "Límite de resultados") @RequestParam(defaultValue = "10") int limit) {

        log.debug("Getting related documents for: {}", documentId);

        return documentManagementUseCase.getRelatedDocuments(documentId, limit)
                .map(this::toDocumentSummaryResponse)
                .doOnNext(doc -> log.debug("Related document: {}", doc.title()))
                .doOnError(error -> log.error("Error getting related documents for {}: {}",
                        documentId, error.getMessage()));
    }

    /**
     * Métodos auxiliares
     */
    private UserId extractUserIdFromPrincipal(Object principal) {
        if (principal instanceof com.contentshub.infrastructure.security.JwtAuthenticationConverter.JwtUserPrincipal jwtPrincipal) {
            return UserId.of(jwtPrincipal.getUserId());
        }

        // Fallback para casos de testing o desarrollo
        log.warn("Extracting user ID from principal of type: {}", principal.getClass());
        return UserId.of(1L);
    }

    private String getClientIp(ServerHttpRequest request) {
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddress() != null ?
                request.getRemoteAddress().getAddress().getHostAddress() : "unknown";
    }

    private LocalDateTime parseDateTime(String dateTime) {
        if (dateTime == null) return null;
        try {
            return LocalDateTime.parse(dateTime);
        } catch (Exception e) {
            log.warn("Invalid date format: {}", dateTime);
            return null;
        }
    }

    private DocumentContent mapToDocumentContent(ContentRequest contentRequest) {
        if (contentRequest == null) {
            return DocumentContent.empty();
        }

        return switch (contentRequest.type().toLowerCase()) {
            case "plain-text" -> DocumentContent.plainText(contentRequest.text());
            case "markdown" -> DocumentContent.markdown(contentRequest.text());
            case "rich-text" -> DocumentContent.richText(
                    contentRequest.blocks() != null ?
                            contentRequest.blocks().stream()
                                    .map(this::mapToContentBlock)
                                    .toList() :
                            java.util.List.of()
            );
            default -> DocumentContent.plainText(contentRequest.text());
        };
    }

    private DocumentContent.ContentBlock mapToContentBlock(BlockRequest blockRequest) {
        return DocumentContent.ContentBlock.builder()
                .id(blockRequest.id())
                .type(blockRequest.type())
                .text(blockRequest.text())
                .properties(blockRequest.properties())
                .build();
    }

    /**
     * Mappers
     */
    private DocumentResponse toDocumentResponse(DocumentManagementUseCase.DocumentResponse response) {
        return new DocumentResponse(
                response.id(),
                response.title(),
                mapFromDocumentContent(response.content()),
                response.documentType().getValue(),
                response.status().getValue(),
                response.ownerId().getValue(),
                response.ownerName(),
                response.isPublic(),
                response.tags(),
                response.version(),
                response.collaborators().stream()
                        .map(UserId::getValue)
                        .collect(java.util.stream.Collectors.toSet()),
                response.viewCount(),
                response.likeCount(),
                response.createdAt(),
                response.updatedAt(),
                response.publishedAt(),
                response.lastModifiedBy() != null ? response.lastModifiedBy().getValue() : null,
                response.canEdit(),
                response.canDelete(),
                response.canShare()
        );
    }

    private DocumentSummaryResponse toDocumentSummaryResponse(DocumentManagementUseCase.DocumentSummaryResponse response) {
        return new DocumentSummaryResponse(
                response.id(),
                response.title(),
                response.documentType().getValue(),
                response.status().getValue(),
                response.ownerId().getValue(),
                response.ownerName(),
                response.isPublic(),
                response.tags(),
                response.viewCount(),
                response.likeCount(),
                response.createdAt(),
                response.updatedAt(),
                response.excerpt()
        );
    }

    private DocumentStatisticsResponse toDocumentStatisticsResponse(DocumentManagementUseCase.DocumentStatisticsResponse response) {
        return new DocumentStatisticsResponse(
                response.documentId(),
                response.viewCount(),
                response.likeCount(),
                response.collaboratorCount(),
                response.lastModified(),
                response.wordCount(),
                response.sizeInBytes(),
                response.viewsByDay(),
                response.topViewers(),
                response.recentCollaborators()
        );
    }

    private ContentResponse mapFromDocumentContent(DocumentContent content) {
        if (content == null) {
            return new ContentResponse("plain-text", "", java.util.List.of(), java.util.Map.of());
        }

        return new ContentResponse(
                content.getType().getValue(),
                content.getText(),
                content.getBlocks().stream()
                        .map(block -> new BlockResponse(
                                block.getId(),
                                block.getType(),
                                block.getText(),
                                block.getProperties()
                        ))
                        .toList(),
                content.getMetadata()
        );
    }

    /**
     * DTOs de Request
     */
    public record CreateDocumentRequest(
            @NotBlank(message = "Título es requerido")
            @Size(max = 255, message = "Título no puede exceder 255 caracteres")
            String title,

            ContentRequest content,

            @NotBlank(message = "Tipo de documento es requerido")
            String documentType,

            boolean isPublic,
            Set<String> tags
    ) {}

    public record CreateFromTemplateRequest(
            @NotBlank(message = "Título es requerido")
            String title,

            @NotBlank(message = "ID de template es requerido")
            String templateId,

            boolean isPublic
    ) {}

    public record UpdateContentRequest(
            @NotNull(message = "Contenido es requerido")
            ContentRequest content
    ) {}

    public record UpdateTitleRequest(
            @NotBlank(message = "Nuevo título es requerido")
            @Size(max = 255, message = "Título no puede exceder 255 caracteres")
            String newTitle
    ) {}

    public record ArchiveDocumentRequest(String reason) {}

    public record AddCollaboratorRequest(
            @NotNull(message = "ID de colaborador es requerido")
            Long collaboratorId
    ) {}

    public record DuplicateDocumentRequest(
            @NotBlank(message = "Nuevo título es requerido")
            String newTitle
    ) {}

    public record ContentRequest(
            String type,
            String text,
            java.util.List<BlockRequest> blocks,
            java.util.Map<String, Object> metadata
    ) {}

    public record BlockRequest(
            String id,
            String type,
            String text,
            java.util.Map<String, Object> properties
    ) {}

    /**
     * DTOs de Response
     */
    public record DocumentResponse(
            String id,
            String title,
            ContentResponse content,
            String documentType,
            String status,
            Long ownerId,
            String ownerName,
            boolean isPublic,
            Set<String> tags,
            Integer version,
            Set<Long> collaborators,
            Long viewCount,
            Long likeCount,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime publishedAt,
            Long lastModifiedBy,
            boolean canEdit,
            boolean canDelete,
            boolean canShare
    ) {}

    public record DocumentSummaryResponse(
            String id,
            String title,
            String documentType,
            String status,
            Long ownerId,
            String ownerName,
            boolean isPublic,
            Set<String> tags,
            Long viewCount,
            Long likeCount,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            String excerpt
    ) {}

    public record DocumentStatisticsResponse(
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

    public record ContentResponse(
            String type,
            String text,
            java.util.List<BlockResponse> blocks,
            java.util.Map<String, Object> metadata
    ) {}

    public record BlockResponse(
            String id,
            String type,
            String text,
            java.util.Map<String, Object> properties
    ) {}
}
