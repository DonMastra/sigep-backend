package com.sigep.courses.domain.repository

import com.sigep.courses.domain.model.SessionException
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface SessionExceptionRepository : JpaRepository<SessionException, Long> {

    fun findBySessionId(sessionId: Long): List<SessionException>

    fun findBySessionIdAndExceptionDate(sessionId: Long, date: LocalDate): SessionException?

    fun findByExceptionDate(date: LocalDate): List<SessionException>
}

