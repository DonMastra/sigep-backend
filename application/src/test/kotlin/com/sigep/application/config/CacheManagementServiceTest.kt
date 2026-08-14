package com.sigep.application.config

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.Cursor
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ScanOptions
import kotlin.test.assertEquals

class CacheManagementServiceTest {
    private val redisTemplate = mockk<RedisTemplate<String, Any>>()
    private val cursor = mockk<Cursor<String>>()

    @Test
    fun `clears only keys returned by the environment namespace scan`() {
        val scanOptions = slot<ScanOptions>()
        every { redisTemplate.scan(capture(scanOptions)) } returns cursor
        every { cursor.hasNext() } returnsMany listOf(true, true, false)
        every { cursor.next() } returnsMany listOf(
            "sigep-prod::students::1",
            "sigep-prod::courses::2"
        )
        every { cursor.close() } just Runs
        every { redisTemplate.delete(any<Collection<String>>()) } returns 2L

        CacheManagementService(redisTemplate, "sigep-prod").clearAllCache()

        assertEquals("sigep-prod::*", scanOptions.captured.pattern)
        verify(exactly = 1) {
            redisTemplate.delete(
                listOf("sigep-prod::students::1", "sigep-prod::courses::2")
            )
        }
    }
}
