package com.sigep.guardians.infrastructure.repository

import com.sigep.guardians.domain.model.GuardianClientSearchCriteria
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate

class JdbcGuardianClientReadRepositoryTest {
    private val jdbc = mockk<NamedParameterJdbcTemplate>()
    private val repository = JdbcGuardianClientReadRepository(jdbc)

    @Test
    fun `guardian catalog reads active assignments instead of the legacy singular role`() {
        val contentSql = slot<String>()
        every {
            jdbc.query(
                capture(contentSql),
                any<MapSqlParameterSource>(),
                any<RowMapper<*>>()
            )
        } returns emptyList<Any>()
        every {
            jdbc.queryForObject(
                any<String>(),
                any<MapSqlParameterSource>(),
                Long::class.java
            )
        } returns 0L

        repository.search(GuardianClientSearchCriteria(page = 0, size = 20))

        assertTrue(contentSql.captured.contains("FROM user_role_assignments ura"))
        assertTrue(contentSql.captured.contains("ura.role = 'GUARDIAN'"))
        assertTrue(contentSql.captured.contains("ura.revoked_at IS NULL"))
    }
}
