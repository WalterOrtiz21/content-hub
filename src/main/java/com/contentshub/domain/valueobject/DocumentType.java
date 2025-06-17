package com.contentshub.domain.valueobject;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Map;
import java.util.Set;

/**
 * Value Object para el tipo de documento
 * Representa los diferentes tipos de documentos soportados
 */
public enum DocumentType {
    PLAIN_TEXT("plain-text", "Texto Plano", "txt", Set.of("text", "note")),
    MARKDOWN("markdown", "Markdown", "md", Set.of("documentation", "readme", "wiki")),
    RICH_TEXT("rich-text", "Texto Enriquecido", "html", Set.of("article", "blog", "content")),
    STRUCTURED("structured", "Documento Estructurado", "json", Set.of("form", "template", "schema")),
    SPREADSHEET("spreadsheet", "Hoja de Cálculo", "csv", Set.of("data", "table", "calculation")),
    PRESENTATION("presentation", "Presentación", "ppt", Set.of("slides", "deck")),
    DIAGRAM("diagram", "Diagrama", "svg", Set.of("flowchart", "mindmap", "uml"));

    private final String value;
    private final String displayName;
    private final String defaultExtension;
    private final Set<String> categories;

    // Configuraciones específicas por tipo
    private static final Map<DocumentType, TypeConfiguration> TYPE_CONFIGS = Map.of(
            PLAIN_TEXT, new TypeConfiguration(1_000_000, false, true),
            MARKDOWN, new TypeConfiguration(5_000_000, true, true),
            RICH_TEXT, new TypeConfiguration(10_000_000, true, true),
            STRUCTURED, new TypeConfiguration(2_000_000, true, false),
            SPREADSHEET, new TypeConfiguration(50_000_000, false, false),
            PRESENTATION, new TypeConfiguration(100_000_000, true, false),
            DIAGRAM, new TypeConfiguration(10_000_000, true, false)
    );

    DocumentType(String value, String displayName, String defaultExtension, Set<String> categories) {
        this.value = value;
        this.displayName = displayName;
        this.defaultExtension = defaultExtension;
        this.categories = categories;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDefaultExtension() {
        return defaultExtension;
    }

    public Set<String> getCategories() {
        return categories;
    }

    @JsonCreator
    public static DocumentType fromValue(String value) {
        if (value == null) {
            return PLAIN_TEXT; // Default value
        }

        for (DocumentType type : DocumentType.values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown document type: " + value);
    }

    /**
     * Crear desde extensión de archivo
     */
    public static DocumentType fromExtension(String extension) {
        if (extension == null) {
            return PLAIN_TEXT;
        }

        String ext = extension.toLowerCase().startsWith(".") ?
                extension.substring(1) : extension;

        return switch (ext) {
            case "txt", "text" -> PLAIN_TEXT;
            case "md", "markdown" -> MARKDOWN;
            case "html", "htm", "rtf" -> RICH_TEXT;
            case "json", "xml", "yaml", "yml" -> STRUCTURED;
            case "csv", "tsv", "xls", "xlsx" -> SPREADSHEET;
            case "ppt", "pptx" -> PRESENTATION;
            case "svg", "drawio" -> DIAGRAM;
            default -> PLAIN_TEXT;
        };
    }

    /**
     * Obtener configuración del tipo
     */
    public TypeConfiguration getConfiguration() {
        return TYPE_CONFIGS.get(this);
    }

    /**
     * Verificar si soporta colaboración en tiempo real
     */
    public boolean supportsRealTimeCollaboration() {
        return getConfiguration().supportsRealTime;
    }

    /**
     * Verificar si soporta versionado
     */
    public boolean supportsVersioning() {
        return getConfiguration().supportsVersioning;
    }

    /**
     * Obtener tamaño máximo permitido
     */
    public long getMaxSizeBytes() {
        return getConfiguration().maxSizeBytes;
    }

    /**
     * Verificar si pertenece a una categoría
     */
    public boolean isInCategory(String category) {
        return categories.contains(category.toLowerCase());
    }

    /**
     * Verificar si es un tipo de texto
     */
    public boolean isTextType() {
        return this == PLAIN_TEXT || this == MARKDOWN || this == RICH_TEXT;
    }

    /**
     * Verificar si es un tipo estructurado
     */
    public boolean isStructuredType() {
        return this == STRUCTURED || this == SPREADSHEET;
    }

    /**
     * Verificar si es un tipo visual
     */
    public boolean isVisualType() {
        return this == PRESENTATION || this == DIAGRAM;
    }

    /**
     * Obtener tipos compatibles para conversión
     */
    public Set<DocumentType> getCompatibleTypes() {
        return switch (this) {
            case PLAIN_TEXT -> Set.of(MARKDOWN, RICH_TEXT);
            case MARKDOWN -> Set.of(PLAIN_TEXT, RICH_TEXT);
            case RICH_TEXT -> Set.of(PLAIN_TEXT, MARKDOWN);
            case STRUCTURED -> Set.of(SPREADSHEET);
            case SPREADSHEET -> Set.of(STRUCTURED);
            case PRESENTATION -> Set.of();
            case DIAGRAM -> Set.of();
        };
    }

    /**
     * Verificar si se puede convertir a otro tipo
     */
    public boolean canConvertTo(DocumentType targetType) {
        return getCompatibleTypes().contains(targetType);
    }

    /**
     * Obtener MIME type
     */
    public String getMimeType() {
        return switch (this) {
            case PLAIN_TEXT -> "text/plain";
            case MARKDOWN -> "text/markdown";
            case RICH_TEXT -> "text/html";
            case STRUCTURED -> "application/json";
            case SPREADSHEET -> "text/csv";
            case PRESENTATION -> "application/vnd.ms-powerpoint";
            case DIAGRAM -> "image/svg+xml";
        };
    }

    @Override
    public String toString() {
        return displayName;
    }

    /**
     * Configuración específica de cada tipo de documento
     */
    public record TypeConfiguration(
            long maxSizeBytes,
            boolean supportsRealTime,
            boolean supportsVersioning
    ) {}
}
