package com.sigep.application.config

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.slf4j.LoggerFactory
import org.springframework.cache.Cache
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.CachingConfigurer
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.interceptor.CacheErrorHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration

@Configuration
@EnableCaching
class RedisConfig : CachingConfigurer {

    companion object {
        private val log = LoggerFactory.getLogger(RedisConfig::class.java)
    }

    private fun redisObjectMapper(): ObjectMapper = ObjectMapper().apply {
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
            .build()
        activateDefaultTyping(ptv, ObjectMapper.DefaultTyping.EVERYTHING, JsonTypeInfo.As.PROPERTY)
    }

    @Bean
    fun cacheManager(connectionFactory: RedisConnectionFactory): RedisCacheManager {
        val objectMapper = redisObjectMapper()
        val valueSerializer = Jackson2JsonRedisSerializer(objectMapper, Any::class.java)

        val config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))
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
            log.warn("[Cache] GET error en '{}' key='{}' — cache miss graceful ({})", cache.name, key, e.message)
            // No re-throw: Spring interpreta esto como cache miss y ejecuta el méthodo real
        }

        override fun handleCachePutError(e: RuntimeException, cache: Cache, key: Any, value: Any?) {
            log.warn("[Cache] PUT error en '{}' key='{}': {}", cache.name, key, e.message)
        }

        override fun handleCacheEvictError(e: RuntimeException, cache: Cache, key: Any) {
            log.warn("[Cache] EVICT error en '{}' key='{}': {}", cache.name, key, e.message)
        }

        override fun handleCacheClearError(e: RuntimeException, cache: Cache) {
            log.warn("[Cache] CLEAR error en '{}': {}", cache.name, e.message)
        }
    }
}

/**
 * Componente para gestión automática del cache
 */
@Component
class CacheManagementService(
    private val redisTemplate: RedisTemplate<String, Any>
) {
    private val logger = LoggerFactory.getLogger(CacheManagementService::class.java)

    /**
     * Limpia el cache de Redis
     */
    @CacheEvict(
        allEntries = true,
        cacheNames = [
            "students",
            "students_detail",
            "courses",
            "enrollments",
            "teachingStaff",
            "nonTeachingStaff",
            "exams",
            "submissions"
        ]
    )
    fun clearAllCache() {
        logger.info("Clearing all Redis cache")
        redisTemplate.connectionFactory?.connection?.serverCommands()?.flushAll()
        logger.info("Redis cache cleared successfully")
    }

    /**
     * Limpia el cache automáticamente cada 12 horas para evitar datos obsoletos
     */
    @Scheduled(fixedRate = 43200000) // 12 horas en milisegundos
    fun scheduledCacheClear() {
        logger.info("Scheduled cache clear started")
        clearAllCache()
    }
}
