package com.contentshub.infrastructure.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Convertidor de autenticación JWT para Spring WebFlux Security
 */
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationConverter implements ServerAuthenticationConverter {

    private final JwtProvider jwtProvider;
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public Mono<Authentication> convert(ServerWebExchange exchange) {
        return extractToken(exchange)
                .filter(jwtProvider::validateToken)
                .map(this::createAuthentication)
                .doOnNext(auth -> log.debug("JWT authentication created for user: {}", auth.getName()))
                .doOnError(error -> log.debug("JWT authentication failed: {}", error.getMessage()));
    }

    /**
     * Extraer token del header Authorization
     */
    private Mono<String> extractToken(ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
            String authHeader = exchange.getRequest()
                    .getHeaders()
                    .getFirst(AUTHORIZATION_HEADER);

            if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
                return authHeader.substring(BEARER_PREFIX.length());
            }

            return null;
        }).filter(token -> token != null && !token.trim().isEmpty());
    }

    /**
     * Crear objeto Authentication desde token JWT
     */
    private Authentication createAuthentication(String token) {
        try {
            String username = jwtProvider.getUsernameFromToken(token);
            Long userId = jwtProvider.getUserIdFromToken(token);
            String email = jwtProvider.getEmailFromToken(token);
            List<SimpleGrantedAuthority> authorities = jwtProvider.getAuthoritiesFromToken(token);

            // Crear principal personalizado con información adicional
            JwtUserPrincipal principal = new JwtUserPrincipal(
                    userId,
                    username,
                    email,
                    token
            );

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, authorities);

            // Agregar detalles adicionales
            authentication.setDetails(new JwtAuthenticationDetails(token, userId, email));

            return authentication;

        } catch (Exception e) {
            log.error("Error creating authentication from JWT: {}", e.getMessage());
            throw new RuntimeException("Invalid JWT token", e);
        }
    }

    /**
     * Principal personalizado para JWT
     */
    public static class JwtUserPrincipal {
        private final Long userId;
        private final String username;
        private final String email;
        private final String token;

        public JwtUserPrincipal(Long userId, String username, String email, String token) {
            this.userId = userId;
            this.username = username;
            this.email = email;
            this.token = token;
        }

        public Long getUserId() {
            return userId;
        }

        public String getUsername() {
            return username;
        }

        public String getEmail() {
            return email;
        }

        public String getToken() {
            return token;
        }

        @Override
        public String toString() {
            return username;
        }
    }

    /**
     * Detalles de autenticación JWT
     */
    public static class JwtAuthenticationDetails {
        private final String token;
        private final Long userId;
        private final String email;

        public JwtAuthenticationDetails(String token, Long userId, String email) {
            this.token = token;
            this.userId = userId;
            this.email = email;
        }

        public String getToken() {
            return token;
        }

        public Long getUserId() {
            return userId;
        }

        public String getEmail() {
            return email;
        }
    }
}
