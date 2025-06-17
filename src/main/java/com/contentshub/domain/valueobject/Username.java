package com.contentshub.domain.valueobject;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Value;

import java.util.regex.Pattern;

/**
 * Value Object para nombre de usuario
 * Encapsula y valida los nombres de usuario
 */
@Value(staticConstructor = "of")
public class Username {

    private static final Pattern VALID_USERNAME_PATTERN =
            Pattern.compile("^[a-zA-Z0-9._-]{3,50}$");

    String value;

    private Username(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Username no puede ser null o vacío");
        }

        String trimmedValue = value.trim();

        if (!VALID_USERNAME_PATTERN.matcher(trimmedValue).matches()) {
            throw new IllegalArgumentException(
                    "Username inválido. Debe tener entre 3-50 caracteres y solo contener letras, números, puntos, guiones y guiones bajos");
        }

        this.value = trimmedValue;
    }

    @JsonCreator
    public static Username of(String value) {
        return new Username(value);
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }

    /**
     * Verificar si el username es válido sin crear el objeto
     */
    public static boolean isValid(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        return VALID_USERNAME_PATTERN.matcher(value.trim()).matches();
    }

    /**
     * Crear username con normalización
     */
    public static Username normalize(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Username no puede ser null");
        }
        return of(value.toLowerCase().trim());
    }

    /**
     * Verificar si contiene caracteres especiales
     */
    public boolean hasSpecialCharacters() {
        return value.contains(".") || value.contains("-") || value.contains("_");
    }

    /**
     * Obtener longitud
     */
    public int length() {
        return value.length();
    }
}
