package com.sigep.exams.domain.repository

import com.sigep.exams.domain.model.ExamGradeHistory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ExamGradeHistoryRepository : JpaRepository<ExamGradeHistory, UUID> {

    fun findBySubmissionIdOrderByChangedAtDesc(submissionId: UUID): List<ExamGradeHistory>

    @Query("""
        SELECT h FROM ExamGradeHistory h
        WHERE h.submissionId IN :submissionIds
        ORDER BY h.changedAt DESC
    """)
    fun findBySubmissionIds(@Param("submissionIds") submissionIds: List<UUID>): List<ExamGradeHistory>
}

