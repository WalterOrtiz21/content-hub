package com.contentshub.application.port.output;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * Puerto de salida para repositorio de cache
 * Define el contrato para operaciones de cache (Redis)
 */
public interface CacheRepositoryPort {

    /**
     * Obtener valor del cache
     */
    <T> Mono<T> get(String key, Class<T> valueType);

    /**
     * Guardar valor en cache
     */
    <T> Mono<Void> set(String key, T value);

    /**
     * Guardar valor en cache con TTL
     */
    <T> Mono<Void> set(String key, T value, Duration ttl);

    /**
     * Verificar si existe una clave
     */
    Mono<Boolean> exists(String key);

    /**
     * Eliminar una clave
     */
    Mono<Void> delete(String key);

    /**
     * Eliminar múltiples claves
     */
    Mono<Void> delete(Set<String> keys);

    /**
     * Establecer TTL a una clave existente
     */
    Mono<Void> expire(String key, Duration ttl);

    /**
     * Obtener TTL de una clave
     */
    Mono<Duration> getTtl(String key);

    /**
     * Incrementar valor numérico
     */
    Mono<Long> increment(String key);

    /**
     * Incrementar valor numérico por cantidad
     */
    Mono<Long> incrementBy(String key, long delta);

    /**
     * Decrementar valor numérico
     */
    Mono<Long> decrement(String key);

    /**
     * Operaciones de Hash
     */
    <T> Mono<T> hashGet(String key, String field, Class<T> valueType);

    <T> Mono<Void> hashSet(String key, String field, T value);

    <T> Mono<Map<String, T>> hashGetAll(String key, Class<T> valueType);

    <T> Mono<Void> hashSetAll(String key, Map<String, T> values);

    Mono<Void> hashDelete(String key, String field);

    Mono<Boolean> hashExists(String key, String field);

    /**
     * Operaciones de Set
     */
    <T> Mono<Void> setAdd(String key, T value);

    <T> Mono<Void> setAddAll(String key, Set<T> values);

    <T> Mono<Boolean> setIsMember(String key, T value);

    <T> Mono<Set<T>> setMembers(String key, Class<T> valueType);

    <T> Mono<Void> setRemove(String key, T value);

    Mono<Long> setSize(String key);

    /**
     * Operaciones de Lista
     */
    <T> Mono<Void> listPush(String key, T value);

    <T> Mono<T> listPop(String key, Class<T> valueType);

    <T> Mono<Flux<T>> listRange(String key, long start, long end, Class<T> valueType);

    Mono<Long> listSize(String key);

    /**
     * Buscar claves por patrón
     */
    Flux<String> findKeysByPattern(String pattern);

    /**
     * Limpiar todo el cache
     */
    Mono<Void> clear();

    /**
     * Limpiar claves por patrón
     */
    Mono<Void> clearByPattern(String pattern);

    /**
     * Obtener información del cache
     */
    Mono<CacheInfo> getInfo();

    /**
     * Operaciones atómicas con pipeline
     */
    <T> Mono<T> executePipeline(CachePipelineOperation<T> operation);

    /**
     * Información del cache
     */
    record CacheInfo(
            long totalKeys,
            long usedMemory,
            long maxMemory,
            double hitRate,
            long connectedClients
    ) {}

    /**
     * Operación de pipeline para transacciones atómicas
     */
    @FunctionalInterface
    interface CachePipelineOperation<T> {
        T execute(CachePipeline pipeline);
    }

    /**
     * Interface para operaciones de pipeline
     */
    interface CachePipeline {
        <T> CachePipeline set(String key, T value);
        <T> CachePipeline set(String key, T value, Duration ttl);
        CachePipeline delete(String key);
        CachePipeline expire(String key, Duration ttl);
        <T> CachePipeline hashSet(String key, String field, T value);
        <T> CachePipeline setAdd(String key, T value);
    }

    /**
     * Operaciones específicas para sesiones
     */
    interface SessionOperations {
        Mono<Void> storeSession(String sessionId, Map<String, Object> sessionData, Duration ttl);
        Mono<Map<String, Object>> getSession(String sessionId);
        Mono<Void> deleteSession(String sessionId);
        Mono<Void> updateSessionTtl(String sessionId, Duration ttl);
        Flux<String> getActiveSessions();
    }

    /**
     * Operaciones específicas para rate limiting
     */
    interface RateLimitOperations {
        Mono<Boolean> isAllowed(String key, int limit, Duration window);
        Mono<Long> getCurrentCount(String key);
        Mono<Duration> getTimeToReset(String key);
        Mono<Void> resetLimit(String key);
    }

    /**
     * Operaciones específicas para pub/sub
     */
    interface PubSubOperations {
        Mono<Void> publish(String channel, Object message);
        Flux<Object> subscribe(String channel, Class<?> messageType);
        Mono<Void> unsubscribe(String channel);
        Flux<String> getActiveChannels();
    }

    /**
     * Obtener operaciones específicas
     */
    SessionOperations sessions();
    RateLimitOperations rateLimits();
    PubSubOperations pubSub();
}
