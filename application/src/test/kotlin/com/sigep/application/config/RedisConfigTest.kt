package com.sigep.application.config

import com.sigep.common.application.dto.PageResponse
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.cache.Cache

class RedisConfigTest {

    private val objectMapper = RedisConfig().redisObjectMapper()

    @Test
    fun `deserializes cached page with nullable boxed long`() {
        val cachedPage = PageResponse(
            content = listOf(CachedStaff(linkedUserId = 42L)),
            page = 0,
            size = 1000,
            totalElements = 1L,
            totalPages = 1
        )

        val serialized = objectMapper.writeValueAsBytes(cachedPage)
        val restored = objectMapper.readValue(serialized, Any::class.java) as PageResponse<*>

        assertThat(restored.content).containsExactly(CachedStaff(linkedUserId = 42L))
    }

    @Test
    fun `prefixes cache names with the configured environment namespace`() {
        val config = RedisConfig("sigep-prod")

        assertThat(config.cachePrefix("students")).isEqualTo("sigep-prod::students::")
    }

    @Test
    fun `rejects cache namespaces containing Redis glob characters`() {
        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            RedisConfig("sigep-*")
        }
    }

    @Test
    fun `cache failures are swallowed so persistent operations can continue`() {
        val handler = RedisConfig("sigep-prod").errorHandler()
        val cache = mockk<Cache> {
            io.mockk.every { name } returns "students"
        }
        val failure = IllegalStateException("redis unavailable")

        handler.handleCacheGetError(failure, cache, "student-key")
        handler.handleCachePutError(failure, cache, "student-key", "value")
        handler.handleCacheEvictError(failure, cache, "student-key")
        handler.handleCacheClearError(failure, cache)
    }

    private data class CachedStaff(
        val linkedUserId: Long?
    )
}
