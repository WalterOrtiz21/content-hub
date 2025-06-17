package com.contentshub.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.ReactiveAuditorAware;
import org.springframework.data.mongodb.config.EnableReactiveMongoAuditing;
import org.springframework.data.r2dbc.config.EnableR2dbcAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import reactor.core.publisher.Mono;

/**
 * Configuración de auditoría para R2DBC y MongoDB (completamente reactiva)
 */
@Configuration
@EnableR2dbcAuditing(auditorAwareRef = "reactiveAuditorProvider")
@EnableReactiveMongoAuditing(auditorAwareRef = "reactiveAuditorProvider")
public class AuditConfiguration {

    /**
     * Auditor reactivo para R2DBC y MongoDB
     */
    @Bean
    public ReactiveAuditorAware<String> reactiveAuditorProvider() {
        return new ReactiveSpringSecurityAuditorAware();
    }

    /**
     * Implementación reactiva de auditoría que funciona tanto para R2DBC como MongoDB
     */
    private static class ReactiveSpringSecurityAuditorAware implements ReactiveAuditorAware<String> {

        @Override
        public Mono<String> getCurrentAuditor() {
            return ReactiveSecurityContextHolder.getContext()
                    .map(SecurityContext::getAuthentication)
                    .filter(Authentication::isAuthenticated)
                    .map(authentication -> {
                        // Extraer el auditor según el tipo de principal
                        Object principal = authentication.getPrincipal();

                        if (principal instanceof com.contentshub.infrastructure.security.JwtAuthenticationConverter.JwtUserPrincipal jwtPrincipal) {
                            return jwtPrincipal.getUsername();
                        }

                        if (principal instanceof org.springframework.security.core.userdetails.UserDetails userDetails) {
                            return userDetails.getUsername();
                        }

                        if (principal instanceof String username) {
                            return username;
                        }

                        // Fallback al nombre de la autenticación
                        return authentication.getName();
                    })
                    .switchIfEmpty(Mono.just("system")) // Usuario por defecto cuando no hay autenticación
                    .onErrorReturn("system"); // En caso de error, usar "system"
        }
    }
}
