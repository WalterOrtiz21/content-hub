package com.contentshub.domain.valueobject;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Value;

/**
 * Value Object para identificador de usuario
 * Encapsula y valida los IDs de usuario
 */
@Value
public class UserId {

    Long value;

    private UserId(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("UserId no puede ser null o menor/igual a 0");
        }
        this.value = value;
    }

    @JsonCreator
    public static UserId of(Long value) {
        return new UserId(value);
    }

    @JsonValue
    public Long getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value.toString();
    }

    /**
     * Convertir a String para persistencia
     */
    public String asString() {
        return value.toString();
    }

    /**
     * Crear desde String
     */
    public static UserId fromString(String value) {
        try {
            return of(Long.parseLong(value));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid UserId format: " + value, e);
        }
    }
}
