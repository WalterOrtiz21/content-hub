package com.contentshub.adapter.output.persistence.redis;

import com.contentshub.application.port.output.CacheRepositoryPort;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Implementación del repositorio de cache usando Redis
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class CacheRepositoryAdapter implements CacheRepositoryPort {

    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final ReactiveStringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public <T> Mono<T> get(String key, Class<T> valueType) {
        return redisTemplate.opsForValue()
                .get(key)
                .cast(valueType)
                .doOnSuccess(value -> {
                    if (value != null) {
                        log.debug("Cache hit for key: {}", key);
                    } else {
                        log.debug("Cache miss for key: {}", key);
                    }
                })
                .doOnError(error -> log.error("Error getting cache key {}: {}", key, error.getMessage()));
    }

    @Override
    public <T> Mono<Void> set(String key, T value) {
        return redisTemplate.opsForValue()
                .set(key, value)
                .then()
                .doOnSuccess(unused -> log.debug("Value cached for key: {}", key))
                .doOnError(error -> log.error("Error setting cache key {}: {}", key, error.getMessage()));
    }

    @Override
    public <T> Mono<Void> set(String key, T value, Duration ttl) {
        return redisTemplate.opsForValue()
                .set(key, value, ttl)
                .then()
                .doOnSuccess(unused -> log.debug("Value cached for key: {} with TTL: {}", key, ttl))
                .doOnError(error -> log.error("Error setting cache key {} with TTL: {}", key, error.getMessage()));
    }

    @Override
    public Mono<Boolean> exists(String key) {
        return redisTemplate.hasKey(key)
                .doOnNext(exists -> log.debug("Key {} exists: {}", key, exists))
                .doOnError(error -> log.error("Error checking existence of key {}: {}", key, error.getMessage()));
    }

    @Override
    public Mono<Void> delete(String key) {
        return redisTemplate.delete(key)
                .then()
                .doOnSuccess(unused -> log.debug("Key deleted: {}", key))
                .doOnError(error -> log.error("Error deleting key {}: {}", key, error.getMessage()));
    }

    @Override
    public Mono<Void> delete(Set<String> keys) {
        return redisTemplate.delete(keys)
                .then()
                .doOnSuccess(unused -> log.debug("Keys deleted: {}", keys))
                .doOnError(error -> log.error("Error deleting keys {}: {}", keys, error.getMessage()));
    }

    @Override
    public Mono<Void> expire(String key, Duration ttl) {
        return redisTemplate.expire(key, ttl)
                .then()
                .doOnSuccess(unused -> log.debug("TTL set for key {}: {}", key, ttl))
                .doOnError(error -> log.error("Error setting TTL for key {}: {}", key, error.getMessage()));
    }

    @Override
    public Mono<Duration> getTtl(String key) {
        return redisTemplate.getExpire(key)
                .map(Duration::ofSeconds)
                .doOnNext(ttl -> log.debug("TTL for key {}: {}", key, ttl))
                .doOnError(error -> log.error("Error getting TTL for key {}: {}", key, error.getMessage()));
    }

    @Override
    public Mono<Long> increment(String key) {
        return stringRedisTemplate.opsForValue()
                .increment(key)
                .doOnNext(value -> log.debug("Key {} incremented to: {}", key, value))
                .doOnError(error -> log.error("Error incrementing key {}: {}", key, error.getMessage()));
    }

    @Override
    public Mono<Long> incrementBy(String key, long delta) {
        return stringRedisTemplate.opsForValue()
                .increment(key, delta)
                .doOnNext(value -> log.debug("Key {} incremented by {} to: {}", key, delta, value))
                .doOnError(error -> log.error("Error incrementing key {} by {}: {}", key, delta, error.getMessage()));
    }

    @Override
    public Mono<Long> decrement(String key) {
        return stringRedisTemplate.opsForValue()
                .decrement(key)
                .doOnNext(value -> log.debug("Key {} decremented to: {}", key, value))
                .doOnError(error -> log.error("Error decrementing key {}: {}", key, error.getMessage()));
    }

    @Override
    public <T> Mono<T> hashGet(String key, String field, Class<T> valueType) {
        return redisTemplate.opsForHash()
                .get(key, field)
                .cast(valueType)
                .doOnNext(value -> log.debug("Hash field {}:{} retrieved", key, field))
                .doOnError(error -> log.error("Error getting hash field {}:{}: {}", key, field, error.getMessage()));
    }

    @Override
    public <T> Mono<Void> hashSet(String key, String field, T value) {
        return redisTemplate.opsForHash()
                .put(key, field, value)
                .then()
                .doOnSuccess(unused -> log.debug("Hash field {}:{} set", key, field))
                .doOnError(error -> log.error("Error setting hash field {}:{}: {}", key, field, error.getMessage()));
    }

    @Override
    public <T> Mono<Map<String, T>> hashGetAll(String key, Class<T> valueType) {
        return redisTemplate.opsForHash()
                .entries(key)
                .cast(Map.class)
                .doOnNext(map -> log.debug("Hash {} retrieved with {} fields", key, map.size()))
                .doOnError(error -> log.error("Error getting hash {}: {}", key, error.getMessage()));
    }

    @Override
    public <T> Mono<Void> hashSetAll(String key, Map<String, T> values) {
        return redisTemplate.opsForHash()
                .putAll(key, values)
                .then()
                .doOnSuccess(unused -> log.debug("Hash {} set with {} fields", key, values.size()))
                .doOnError(error -> log.error("Error setting hash {}: {}", key, error.getMessage()));
    }

    @Override
    public Mono<Void> hashDelete(String key, String field) {
        return redisTemplate.opsForHash()
                .remove(key, field)
                .then()
                .doOnSuccess(unused -> log.debug("Hash field {}:{} deleted", key, field))
                .doOnError(error -> log.error("Error deleting hash field {}:{}: {}", key, field, error.getMessage()));
    }

    @Override
    public Mono<Boolean> hashExists(String key, String field) {
        return redisTemplate.opsForHash()
                .hasKey(key, field)
                .doOnNext(exists -> log.debug("Hash field {}:{} exists: {}", key, field, exists))
                .doOnError(error -> log.error("Error checking hash field {}:{}: {}", key, field, error.getMessage()));
    }

    @Override
    public <T> Mono<Void> setAdd(String key, T value) {
        return redisTemplate.opsForSet()
                .add(key, value)
                .then()
                .doOnSuccess(unused -> log.debug("Value added to set: {}", key))
                .doOnError(error -> log.error("Error adding to set {}: {}", key, error.getMessage()));
    }

    @Override
    public <T> Mono<Void> setAddAll(String key, Set<T> values) {
        return redisTemplate.opsForSet()
                .add(key, values.toArray())
                .then()
                .doOnSuccess(unused -> log.debug("Values added to set {}: {}", key, values.size()))
                .doOnError(error -> log.error("Error adding to set {}: {}", key, error.getMessage()));
    }

    @Override
    public <T> Mono<Boolean> setIsMember(String key, T value) {
        return redisTemplate.opsForSet()
                .isMember(key, value)
                .doOnNext(isMember -> log.debug("Set {} contains value: {}", key, isMember))
                .doOnError(error -> log.error("Error checking set membership {}: {}", key, error.getMessage()));
    }

    @Override
    public <T> Mono<Set<T>> setMembers(String key, Class<T> valueType) {
        return redisTemplate.opsForSet()
                .members(key)
                .cast(valueType)
                .collect(java.util.stream.Collectors.toSet())
                .doOnNext(members -> log.debug("Set {} has {} members", key, members.size()))
                .doOnError(error -> log.error("Error getting set members {}: {}", key, error.getMessage()));
    }

    @Override
    public <T> Mono<Void> setRemove(String key, T value) {
        return redisTemplate.opsForSet()
                .remove(key, value)
                .then()
                .doOnSuccess(unused -> log.debug("Value removed from set: {}", key))
                .doOnError(error -> log.error("Error removing from set {}: {}", key, error.getMessage()));
    }

    @Override
    public Mono<Long> setSize(String key) {
        return redisTemplate.opsForSet()
                .size(key)
                .doOnNext(size -> log.debug("Set {} size: {}", key, size))
                .doOnError(error -> log.error("Error getting set size {}: {}", key, error.getMessage()));
    }

    @Override
    public <T> Mono<Void> listPush(String key, T value) {
        return redisTemplate.opsForList()
                .rightPush(key, value)
                .then()
                .doOnSuccess(unused -> log.debug("Value pushed to list: {}", key))
                .doOnError(error -> log.error("Error pushing to list {}: {}", key, error.getMessage()));
    }

    @Override
    public <T> Mono<T> listPop(String key, Class<T> valueType) {
        return redisTemplate.opsForList()
                .leftPop(key)
                .cast(valueType)
                .doOnNext(value -> log.debug("Value popped from list: {}", key))
                .doOnError(error -> log.error("Error popping from list {}: {}", key, error.getMessage()));
    }

    @Override
    public <T> Mono<Flux<T>> listRange(String key, long start, long end, Class<T> valueType) {
        return Mono.just(
                redisTemplate.opsForList()
                        .range(key, start, end)
                        .cast(valueType)
                        .doOnNext(value -> log.debug("Value from list range {}: {}", key, value))
                        .doOnError(error -> log.error("Error getting list range {}: {}", key, error.getMessage()))
        );
    }

    @Override
    public Mono<Long> listSize(String key) {
        return redisTemplate.opsForList()
                .size(key)
                .doOnNext(size -> log.debug("List {} size: {}", key, size))
                .doOnError(error -> log.error("Error getting list size {}: {}", key, error.getMessage()));
    }

    @Override
    public Flux<String> findKeysByPattern(String pattern) {
        return redisTemplate.keys(pattern)
                .doOnNext(key -> log.debug("Found key matching pattern {}: {}", pattern, key))
                .doOnError(error -> log.error("Error finding keys by pattern {}: {}", pattern, error.getMessage()));
    }

    @Override
    public Mono<Void> clear() {
        return redisTemplate.getConnectionFactory()
                .getReactiveConnection()
                .serverCommands()
                .flushAll()
                .then()
                .doOnSuccess(unused -> log.warn("All cache cleared"))
                .doOnError(error -> log.error("Error clearing cache: {}", error.getMessage()));
    }

    @Override
    public Mono<Void> clearByPattern(String pattern) {
        return findKeysByPattern(pattern)
                .collectList()
                .flatMap(keys -> {
                    if (keys.isEmpty()) {
                        return Mono.empty();
                    }
                    return redisTemplate.delete(keys).then();
                })
                .doOnSuccess(unused -> log.debug("Keys cleared by pattern: {}", pattern))
                .doOnError(error -> log.error("Error clearing keys by pattern {}: {}", pattern, error.getMessage()));
    }

    @Override
    public Mono<CacheInfo> getInfo() {
        return redisTemplate.getConnectionFactory()
                .getReactiveConnection()
                .serverCommands()
                .info("memory")
                .map(info -> {
                    // Parse Redis INFO response to extract metrics
                    return new CacheInfo(0L, 0L, 0L, 0.0, 0L);
                })
                .doOnError(error -> log.error("Error getting cache info: {}", error.getMessage()));
    }

    @Override
    public <T> Mono<T> executePipeline(CachePipelineOperation<T> operation) {
        // Implementación simplificada - en un entorno real usarías transacciones Redis
        return Mono.fromCallable(() -> operation.execute(new SimpleCachePipeline()))
                .doOnSuccess(result -> log.debug("Pipeline operation executed"))
                .doOnError(error -> log.error("Error executing pipeline: {}", error.getMessage()));
    }

    @Override
    public SessionOperations sessions() {
        return new RedisSessionOperations();
    }

    @Override
    public RateLimitOperations rateLimits() {
        return new RedisRateLimitOperations();
    }

    @Override
    public PubSubOperations pubSub() {
        return new RedisPubSubOperations();
    }

    /**
     * Implementación simple de CachePipeline
     */
    private class SimpleCachePipeline implements CachePipeline {
        @Override
        public <T> CachePipeline set(String key, T value) {
            redisTemplate.opsForValue().set(key, value).subscribe();
            return this;
        }

        @Override
        public <T> CachePipeline set(String key, T value, Duration ttl) {
            redisTemplate.opsForValue().set(key, value, ttl).subscribe();
            return this;
        }

        @Override
        public CachePipeline delete(String key) {
            redisTemplate.delete(key).subscribe();
            return this;
        }

        @Override
        public CachePipeline expire(String key, Duration ttl) {
            redisTemplate.expire(key, ttl).subscribe();
            return this;
        }

        @Override
        public <T> CachePipeline hashSet(String key, String field, T value) {
            redisTemplate.opsForHash().put(key, field, value).subscribe();
            return this;
        }

        @Override
        public <T> CachePipeline setAdd(String key, T value) {
            redisTemplate.opsForSet().add(key, value).subscribe();
            return this;
        }
    }

    /**
     * Implementación de operaciones de sesión
     */
    private class RedisSessionOperations implements SessionOperations {
        private final String SESSION_PREFIX = "session:";

        @Override
        public Mono<Void> storeSession(String sessionId, Map<String, Object> sessionData, Duration ttl) {
            String key = SESSION_PREFIX + sessionId;
            return redisTemplate.opsForHash()
                    .putAll(key, sessionData)
                    .then(redisTemplate.expire(key, ttl))
                    .then()
                    .doOnSuccess(unused -> log.debug("Session stored: {}", sessionId));
        }

        @Override
        public Mono<Map<String, Object>> getSession(String sessionId) {
            String key = SESSION_PREFIX + sessionId;
            return redisTemplate.opsForHash()
                    .entries(key)
                    .cast(Map.class)
                    .doOnNext(session -> log.debug("Session retrieved: {}", sessionId));
        }

        @Override
        public Mono<Void> deleteSession(String sessionId) {
            String key = SESSION_PREFIX + sessionId;
            return redisTemplate.delete(key)
                    .then()
                    .doOnSuccess(unused -> log.debug("Session deleted: {}", sessionId));
        }

        @Override
        public Mono<Void> updateSessionTtl(String sessionId, Duration ttl) {
            String key = SESSION_PREFIX + sessionId;
            return redisTemplate.expire(key, ttl)
                    .then()
                    .doOnSuccess(unused -> log.debug("Session TTL updated: {}", sessionId));
        }

        @Override
        public Flux<String> getActiveSessions() {
            return redisTemplate.keys(SESSION_PREFIX + "*")
                    .map(key -> key.substring(SESSION_PREFIX.length()));
        }
    }

    /**
     * Implementación de operaciones de rate limiting
     */
    private class RedisRateLimitOperations implements RateLimitOperations {
        private final String RATE_LIMIT_PREFIX = "rate_limit:";

        @Override
        public Mono<Boolean> isAllowed(String key, int limit, Duration window) {
            String redisKey = RATE_LIMIT_PREFIX + key;
            return stringRedisTemplate.opsForValue()
                    .increment(redisKey)
                    .flatMap(count -> {
                        if (count == 1) {
                            return stringRedisTemplate.expire(redisKey, window)
                                    .then(Mono.just(true));
                        }
                        return Mono.just(count <= limit);
                    });
        }

        @Override
        public Mono<Long> getCurrentCount(String key) {
            String redisKey = RATE_LIMIT_PREFIX + key;
            return stringRedisTemplate.opsForValue()
                    .get(redisKey)
                    .map(Long::valueOf)
                    .defaultIfEmpty(0L);
        }

        @Override
        public Mono<Duration> getTimeToReset(String key) {
            String redisKey = RATE_LIMIT_PREFIX + key;
            return stringRedisTemplate.getExpire(redisKey)
                    .map(Duration::ofSeconds);
        }

        @Override
        public Mono<Void> resetLimit(String key) {
            String redisKey = RATE_LIMIT_PREFIX + key;
            return stringRedisTemplate.delete(redisKey).then();
        }
    }

    /**
     * Implementación de operaciones pub/sub
     */
    private class RedisPubSubOperations implements PubSubOperations {
        @Override
        public Mono<Void> publish(String channel, Object message) {
            return redisTemplate.convertAndSend(channel, message)
                    .then()
                    .doOnSuccess(unused -> log.debug("Message published to channel: {}", channel));
        }

        @Override
        public Flux<Object> subscribe(String channel, Class<?> messageType) {
            return redisTemplate.listenTo(org.springframework.data.redis.listener.ChannelTopic.of(channel))
                    .map(message -> message.getMessage())
                    .doOnNext(message -> log.debug("Message received from channel: {}", channel));
        }

        @Override
        public Mono<Void> unsubscribe(String channel) {
            // Implementación simplificada
            return Mono.empty();
        }

        @Override
        public Flux<String> getActiveChannels() {
            return redisTemplate.getConnectionFactory()
                    .getReactiveConnection()
                    .pubSubCommands()
                    .pubsubChannels()
                    .map(String::valueOf);
        }
    }
}
