package com.contentshub.domain.valueobject;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Value;

import java.util.regex.Pattern;

/**
 * Value Object para direcciones de email
 * Encapsula y valida direcciones de correo electrónico
 */
@Value(staticConstructor = "of")
public class Email {

    private static final Pattern VALID_EMAIL_PATTERN =
            Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");

    String value;

    private Email(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Email no puede ser null o vacío");
        }

        String trimmedValue = value.trim().toLowerCase();

        if (!VALID_EMAIL_PATTERN.matcher(trimmedValue).matches()) {
            throw new IllegalArgumentException("Formato de email inválido: " + value);
        }

        if (trimmedValue.length() > 320) { // RFC 5321 limit
            throw new IllegalArgumentException("Email demasiado largo (máximo 320 caracteres)");
        }

        this.value = trimmedValue;
    }

    @JsonCreator
    public static Email of(String value) {
        return new Email(value);
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
     * Verificar si el email es válido sin crear el objeto
     */
    public static boolean isValid(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }

        String trimmedValue = value.trim().toLowerCase();
        return VALID_EMAIL_PATTERN.matcher(trimmedValue).matches() &&
                trimmedValue.length() <= 320;
    }

    /**
     * Obtener dominio del email
     */
    public String getDomain() {
        return value.substring(value.indexOf('@') + 1);
    }

    /**
     * Obtener parte local del email (antes del @)
     */
    public String getLocalPart() {
        return value.substring(0, value.indexOf('@'));
    }

    /**
     * Verificar si es un email de dominio específico
     */
    public boolean isFromDomain(String domain) {
        return getDomain().equalsIgnoreCase(domain);
    }

    /**
     * Verificar si es un email corporativo (dominios comunes excluidos)
     */
    public boolean isCorporateEmail() {
        String domain = getDomain().toLowerCase();
        return !domain.equals("gmail.com") &&
                !domain.equals("yahoo.com") &&
                !domain.equals("hotmail.com") &&
                !domain.equals("outlook.com") &&
                !domain.equals("mail.com") &&
                !domain.equals("protonmail.com");
    }

    /**
     * Generar email mascarado para mostrar públicamente
     */
    public String getMasked() {
        String localPart = getLocalPart();
        String domain = getDomain();

        if (localPart.length() <= 2) {
            return "*@" + domain;
        }

        return localPart.charAt(0) + "***" + localPart.charAt(localPart.length() - 1) + "@" + domain;
    }
}
