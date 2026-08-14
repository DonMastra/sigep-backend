package com.sigep.application.config

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.Cache
import org.springframework.cache.annotation.CachingConfigurer
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.interceptor.CacheErrorHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.ScanOptions
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration

@Configuration
@EnableCaching
class RedisConfig(
    @Value("\${app.cache.namespace:sigep-local}") cacheNamespace: String = "sigep-local"
) : CachingConfigurer {

    private val cacheNamespace = normalizeCacheNamespace(cacheNamespace)

    companion object {
        private val log = LoggerFactory.getLogger(RedisConfig::class.java)
    }

    internal fun redisObjectMapper(): ObjectMapper = ObjectMapper().apply {
        registerKotlinModule()
        registerModule(JavaTimeModule())
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        // EVERYTHING typing: necesario para que los Kotlin data class (que son `final`)
        // también reciban @class en el JSON de Redis.
        // Con NON_FINAL, las clases final no reciben @class → falla al deserializar desde Any.
        // El validador restringe los tipos permitidos a los paquetes conocidos (seguro).
        val ptv = BasicPolymorphicTypeValidator.builder()
            .allowIfSubType("com.sigep.")
            .allowIfSubType("java.util.")
            .allowIfSubType("java.math.")
            .allowIfSubType("java.time.")
            // EVERYTHING also emits type ids for boxed nullable values. Keep this
            // allowlist explicit instead of trusting the complete java.lang package.
            .allowIfSubType(String::class.java)
            .allowIfSubType(Boolean::class.javaObjectType)
            .allowIfSubType(Byte::class.javaObjectType)
            .allowIfSubType(Short::class.javaObjectType)
            .allowIfSubType(Int::class.javaObjectType)
            .allowIfSubType(Long::class.javaObjectType)
            .allowIfSubType(Float::class.javaObjectType)
            .allowIfSubType(Double::class.javaObjectType)
            .allowIfSubType(Char::class.javaObjectType)
            .build()
        activateDefaultTyping(ptv, ObjectMapper.DefaultTyping.EVERYTHING, JsonTypeInfo.As.PROPERTY)
    }

    internal fun cachePrefix(cacheName: String): String = "$cacheNamespace::$cacheName::"

    @Bean
    fun cacheManager(connectionFactory: RedisConnectionFactory): RedisCacheManager {
        val objectMapper = redisObjectMapper()
        val valueSerializer = Jackson2JsonRedisSerializer(objectMapper, Any::class.java)

        val config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))
            .computePrefixWith(::cachePrefix)
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(StringRedisSerializer())
            )
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer)
            )
            .disableCachingNullValues()

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(config)
            .build()
    }

    @Bean
    fun redisTemplate(connectionFactory: RedisConnectionFactory): RedisTemplate<String, Any> {
        val objectMapper = redisObjectMapper()
        val valueSerializer = Jackson2JsonRedisSerializer(objectMapper, Any::class.java)

        val template = RedisTemplate<String, Any>()
        template.connectionFactory = connectionFactory
        template.keySerializer = StringRedisSerializer()
        template.valueSerializer = valueSerializer
        template.hashKeySerializer = StringRedisSerializer()
        template.hashValueSerializer = valueSerializer
        return template
    }

    /**
     * Handler resiliente: ante cualquier error de caché (lectura/escritura), loguea y continúa.
     * - GET error → cache miss graceful → Spring llama al méthodo real → la caché se auto-sana
     * - PUT/EVICT/CLEAR error → loguea y continúa sin explotar
     * Esto hace que la caché sea transparente y no bloquee el flujo de la aplicación.
     */
    override fun errorHandler(): CacheErrorHandler = object : CacheErrorHandler {

        override fun handleCacheGetError(e: RuntimeException, cache: Cache, key: Any) {
            log.warn("[Cache] GET error in '{}' - cache miss graceful ({})", cache.name, e.javaClass.simpleName)
            // No re-throw: Spring interpreta esto como cache miss y ejecuta el méthodo real
        }

        override fun handleCachePutError(e: RuntimeException, cache: Cache, key: Any, value: Any?) {
            log.warn("[Cache] PUT error in '{}' ({})", cache.name, e.javaClass.simpleName)
        }

        override fun handleCacheEvictError(e: RuntimeException, cache: Cache, key: Any) {
            log.warn("[Cache] EVICT error in '{}' ({})", cache.name, e.javaClass.simpleName)
        }

        override fun handleCacheClearError(e: RuntimeException, cache: Cache) {
            log.warn("[Cache] CLEAR error in '{}' ({})", cache.name, e.javaClass.simpleName)
        }
    }
}

/**
 * Componente para gestión automática del cache
 */
@Component
class CacheManagementService(
    private val redisTemplate: RedisTemplate<String, Any>,
    @Value("\${app.cache.namespace:sigep-local}") cacheNamespace: String = "sigep-local"
) {
    private val logger = LoggerFactory.getLogger(CacheManagementService::class.java)
    private val cacheNamespace = normalizeCacheNamespace(cacheNamespace)

    /**
     * Limpia el cache de Redis
     */
    fun clearAllCache() {
        val pattern = "$cacheNamespace::*"
        var deletedKeys = 0L

        redisTemplate.scan(
            ScanOptions.scanOptions()
                .match(pattern)
                .count(500)
                .build()
        ).use { cursor ->
            val batch = ArrayList<String>(500)
            while (cursor.hasNext()) {
                batch.add(cursor.next())
                if (batch.size == 500) {
                    deletedKeys += redisTemplate.delete(batch)
                    batch.clear()
                }
            }
            if (batch.isNotEmpty()) {
                deletedKeys += redisTemplate.delete(batch)
            }
        }

        logger.info("Cleared {} keys from cache namespace '{}'", deletedKeys, cacheNamespace)
    }

    /**
     * Limpia el cache automáticamente cada 12 horas para evitar datos obsoletos
     */
    @Scheduled(
        fixedRate = 43200000,
        initialDelay = 43200000
    ) // 12 horas en milisegundos
    fun scheduledCacheClear() {
        logger.info("Scheduled cache clear started")
        clearAllCache()
    }
}

internal fun normalizeCacheNamespace(value: String): String {
    val normalized = value.trim()
    require(normalized.matches(Regex("[A-Za-z0-9._-]+"))) {
        "app.cache.namespace must contain only letters, numbers, dot, underscore or hyphen"
    }
    return normalized
}
