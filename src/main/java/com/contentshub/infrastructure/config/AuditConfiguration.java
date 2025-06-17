package com.contentshub.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.domain.ReactiveAuditorAware;
import org.springframework.data.mongodb.config.EnableReactiveMongoAuditing;
import org.springframework.data.r2dbc.config.EnableR2dbcAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * Configuración de auditoría para R2DBC y MongoDB
 */
@Configuration
@EnableR2dbcAuditing(auditorAwareRef = "auditorProvider")
@EnableReactiveMongoAuditing(auditorAwareRef = "reactiveAuditorProvider")
public class AuditConfiguration {

    /**
     * Auditor para R2DBC (síncrono)
     */
    @Bean
    public AuditorAware<String> auditorProvider() {
        return new SpringSecurityAuditorAware();
    }

    /**
     * Auditor reactivo para MongoDB
     */
    @Bean
    public ReactiveAuditorAware<String> reactiveAuditorProvider() {
        return new ReactiveSpringSecurityAuditorAware();
    }

    /**
     * Implementación de auditoría para R2DBC
     */
    private static class SpringSecurityAuditorAware implements AuditorAware<String> {
        @Override
        public Optional<String> getCurrentAuditor() {
            // Para R2DBC, usamos un valor por defecto ya que no es reactivo
            // En una implementación real, podrías usar ThreadLocal o RequestScope
            return Optional.of("system");
        }
    }

    /**
     * Implementación reactiva de auditoría para MongoDB
     */
    private static class ReactiveSpringSecurityAuditorAware implements ReactiveAuditorAware<String> {
        @Override
        public Mono<String> getCurrentAuditor() {
            return ReactiveSecurityContextHolder.getContext()
                    .map(SecurityContext::getAuthentication)
                    .filter(Authentication::isAuthenticated)
                    .map(authentication -> {
                        if (authentication.getPrincipal() instanceof
                                com.contentshub.infrastructure.security.JwtAuthenticationConverter.JwtUserPrincipal jwtPrincipal) {
                            return jwtPrincipal.getUsername();
                        }
                        return authentication.getName();
                    })
                    .defaultIfEmpty("system")
                    .onErrorReturn("system");
        }
    }
}
