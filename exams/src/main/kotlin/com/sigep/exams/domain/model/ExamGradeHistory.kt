package com.sigep.exams.domain.model

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

/**
 * Entity - ExamGradeHistory
 * Auditoría de cambios en las calificaciones
 */
@Entity
@Table(
    name = "exam_grade_history",
    indexes = [
        Index(name = "idx_grade_history_submission", columnList = "submission_id"),
        Index(name = "idx_grade_history_changed_at", columnList = "changed_at")
    ]
)
data class ExamGradeHistory(
    @Id
    @Column(columnDefinition = "UUID")
    val id: UUID = UUID.randomUUID(),

    @Column(name = "submission_id", nullable = false, columnDefinition = "UUID")
    val submissionId: UUID,

    @Column(name = "changed_at", nullable = false)
    val changedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "changed_by", nullable = false, columnDefinition = "UUID")
    val changedBy: UUID,

    @Column(name = "previous_score", precision = 10, scale = 2)
    val previousScore: BigDecimal?,

    @Column(name = "new_score", nullable = false, precision = 10, scale = 2)
    val newScore: BigDecimal,

    @Column(columnDefinition = "TEXT")
    val reason: String? = null,

    // Auditoría
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "created_by", nullable = false, columnDefinition = "UUID")
    val createdBy: UUID,

    @Column(name = "updated_at")
    var updatedAt: LocalDateTime? = null,

    @Column(name = "updated_by", columnDefinition = "UUID")
    var updatedBy: UUID? = null
)

