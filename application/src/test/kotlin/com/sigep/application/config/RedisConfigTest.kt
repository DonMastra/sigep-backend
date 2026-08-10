package com.sigep.application.config

import com.sigep.common.application.dto.PageResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

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

    private data class CachedStaff(
        val linkedUserId: Long?
    )
}
