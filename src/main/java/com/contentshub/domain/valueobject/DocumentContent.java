package com.contentshub.domain.valueobject;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.With;

import java.util.List;
import java.util.Map;

/**
 * Value Object para el contenido de un documento
 * Representa contenido flexible que puede ser de diferentes tipos
 */
@Value
@Builder(toBuilder = true)
@With
public class DocumentContent {

    @JsonProperty("type")
    ContentType type;

    @JsonProperty("text")
    String text;

    @JsonProperty("blocks")
    List<ContentBlock> blocks;

    @JsonProperty("metadata")
    Map<String, Object> metadata;

    @JsonCreator
    public DocumentContent(
            @JsonProperty("type") ContentType type,
            @JsonProperty("text") String text,
            @JsonProperty("blocks") List<ContentBlock> blocks,
            @JsonProperty("metadata") Map<String, Object> metadata) {
        this.type = type != null ? type : ContentType.PLAIN_TEXT;
        this.text = text != null ? text : "";
        this.blocks = blocks != null ? blocks : List.of();
        this.metadata = metadata != null ? metadata : Map.of();
    }

    /**
     * Crear contenido de texto plano
     */
    public static DocumentContent plainText(String text) {
        return DocumentContent.builder()
                .type(ContentType.PLAIN_TEXT)
                .text(text != null ? text : "")
                .blocks(List.of())
                .metadata(Map.of())
                .build();
    }

    /**
     * Crear contenido Markdown
     */
    public static DocumentContent markdown(String markdownText) {
        return DocumentContent.builder()
                .type(ContentType.MARKDOWN)
                .text(markdownText != null ? markdownText : "")
                .blocks(List.of())
                .metadata(Map.of("format", "markdown"))
                .build();
    }

    /**
     * Crear contenido de rich text con bloques
     */
    public static DocumentContent richText(List<ContentBlock> blocks) {
        return DocumentContent.builder()
                .type(ContentType.RICH_TEXT)
                .text("") // Se genera desde los bloques
                .blocks(blocks != null ? blocks : List.of())
                .metadata(Map.of("format", "rich-text"))
                .build();
    }

    /**
     * Crear contenido estructurado
     */
    public static DocumentContent structured(List<ContentBlock> blocks, Map<String, Object> structure) {
        return DocumentContent.builder()
                .type(ContentType.STRUCTURED)
                .text("")
                .blocks(blocks != null ? blocks : List.of())
                .metadata(structure != null ? structure : Map.of())
                .build();
    }

    /**
     * Crear contenido vacío
     */
    public static DocumentContent empty() {
        return DocumentContent.builder()
                .type(ContentType.PLAIN_TEXT)
                .text("")
                .blocks(List.of())
                .metadata(Map.of())
                .build();
    }

    /**
     * Verificar si el contenido está vacío
     */
    public boolean isEmpty() {
        return (text == null || text.trim().isEmpty()) &&
                (blocks == null || blocks.isEmpty());
    }

    /**
     * Obtener longitud del contenido
     */
    public int getLength() {
        if (type == ContentType.RICH_TEXT || type == ContentType.STRUCTURED) {
            return blocks.stream()
                    .mapToInt(block -> block.getText().length())
                    .sum();
        }
        return text.length();
    }

    /**
     * Obtener número de palabras
     */
    public int getWordCount() {
        String allText = getAllText();
        if (allText.trim().isEmpty()) {
            return 0;
        }
        return allText.trim().split("\\s+").length;
    }

    /**
     * Obtener todo el texto como string
     */
    public String getAllText() {
        if (type == ContentType.RICH_TEXT || type == ContentType.STRUCTURED) {
            return blocks.stream()
                    .map(ContentBlock::getText)
                    .reduce("", (a, b) -> a + " " + b)
                    .trim();
        }
        return text;
    }

    /**
     * Agregar bloque al contenido
     */
    public DocumentContent addBlock(ContentBlock block) {
        List<ContentBlock> newBlocks = new java.util.ArrayList<>(blocks);
        newBlocks.add(block);
        return this.withBlocks(newBlocks);
    }

    /**
     * Actualizar texto (para contenido no estructurado)
     */
    public DocumentContent updateText(String newText) {
        return this.withText(newText != null ? newText : "");
    }

    /**
     * Convertir a tipo específico
     */
    public DocumentContent convertTo(ContentType newType) {
        if (this.type == newType) {
            return this;
        }

        return switch (newType) {
            case PLAIN_TEXT -> plainText(getAllText());
            case MARKDOWN -> markdown(getAllText());
            case RICH_TEXT -> richText(this.blocks);
            case STRUCTURED -> structured(this.blocks, this.metadata);
        };
    }

    /**
     * Enumeration para tipos de contenido
     */
    public enum ContentType {
        PLAIN_TEXT("plain-text"),
        MARKDOWN("markdown"),
        RICH_TEXT("rich-text"),
        STRUCTURED("structured");

        private final String value;

        ContentType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        @JsonCreator
        public static ContentType fromValue(String value) {
            for (ContentType type : ContentType.values()) {
                if (type.value.equals(value)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown content type: " + value);
        }
    }

    /**
     * Bloque de contenido individual
     */
    @Value
    @Builder
    @With
    public static class ContentBlock {
        @JsonProperty("id")
        String id;

        @JsonProperty("type")
        String type;

        @JsonProperty("text")
        String text;

        @JsonProperty("properties")
        Map<String, Object> properties;

        @JsonCreator
        public ContentBlock(
                @JsonProperty("id") String id,
                @JsonProperty("type") String type,
                @JsonProperty("text") String text,
                @JsonProperty("properties") Map<String, Object> properties) {
            this.id = id != null ? id : java.util.UUID.randomUUID().toString();
            this.type = type != null ? type : "paragraph";
            this.text = text != null ? text : "";
            this.properties = properties != null ? properties : Map.of();
        }

        public static ContentBlock paragraph(String text) {
            return ContentBlock.builder()
                    .type("paragraph")
                    .text(text)
                    .build();
        }

        public static ContentBlock heading(String text, int level) {
            return ContentBlock.builder()
                    .type("heading")
                    .text(text)
                    .properties(Map.of("level", level))
                    .build();
        }

        public static ContentBlock list(String text, boolean ordered) {
            return ContentBlock.builder()
                    .type("list")
                    .text(text)
                    .properties(Map.of("ordered", ordered))
                    .build();
        }

        public static ContentBlock image(String url, String alt) {
            return ContentBlock.builder()
                    .type("image")
                    .text(alt != null ? alt : "")
                    .properties(Map.of("url", url))
                    .build();
        }
    }
}
