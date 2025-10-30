package com.sigep.exams.domain.model

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

/**
 * Entity - ExamSubmission
 * Representa un intento de examen de un estudiante
 * En Fase 1 es solo registro de la calificación del examen presencial
 */
@Entity
@Table(
    name = "exam_submissions",
    indexes = [
        Index(name = "idx_submission_exam", columnList = "exam_id"),
        Index(name = "idx_submission_student", columnList = "student_id"),
        Index(name = "idx_submission_status", columnList = "status")
    ],
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_exam_student_attempt",
            columnNames = ["exam_id", "student_id", "attempt_number"]
        )
    ]
)
data class ExamSubmission(
    @Id
    @Column(columnDefinition = "UUID")
    val id: UUID = UUID.randomUUID(),

    @Column(name = "exam_id", nullable = false, columnDefinition = "UUID")
    val examId: UUID,

    @Column(name = "student_id", nullable = false, columnDefinition = "UUID")
    val studentId: UUID,

    @Column(name = "attempt_number", nullable = false)
    val attemptNumber: Int = 1,

    // Estado del intento
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: SubmissionStatus = SubmissionStatus.PENDING,

    // Fechas del intento
    @Column(name = "started_at")
    var startedAt: LocalDateTime? = null,

    @Column(name = "submitted_at")
    var submittedAt: LocalDateTime? = null,

    // Calificación
    @Column(precision = 10, scale = 2)
    var score: BigDecimal? = null,

    // Información del evaluador
    @Column(name = "graded_by", columnDefinition = "UUID")
    var gradedBy: UUID? = null,

    @Column(name = "graded_at")
    var gradedAt: LocalDateTime? = null,

    // Feedback del docente
    @Column(columnDefinition = "TEXT")
    var feedback: String? = null,

    // Ruta del archivo escaneado del examen
    @Column(name = "scanned_file_path", length = 500)
    var scannedFilePath: String? = null,

    // Observaciones adicionales
    @Column(columnDefinition = "TEXT")
    var notes: String? = null,

    // Versión para control de concurrencia
    @Version
    var version: Int = 1,

    // Auditoría
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "created_by", nullable = false, columnDefinition = "UUID")
    val createdBy: UUID,

    @Column(name = "updated_at")
    var updatedAt: LocalDateTime? = null,

    @Column(name = "updated_by", columnDefinition = "UUID")
    var updatedBy: UUID? = null
) {
    fun grade(score: BigDecimal, gradedBy: UUID, feedback: String? = null) {
        require(this.status != SubmissionStatus.CANCELLED) {
            "No se puede calificar un intento cancelado"
        }
        this.score = score
        this.gradedBy = gradedBy
        this.gradedAt = LocalDateTime.now()
        this.feedback = feedback
        this.status = SubmissionStatus.GRADED
    }

    fun updateScore(newScore: BigDecimal, gradedBy: UUID, feedback: String? = null) {
        require(this.status == SubmissionStatus.GRADED) {
            "Solo se pueden actualizar calificaciones ya realizadas"
        }
        this.score = newScore
        this.gradedBy = gradedBy
        this.gradedAt = LocalDateTime.now()
        this.feedback = feedback
    }

    fun cancel() {
        require(this.status != SubmissionStatus.GRADED) {
            "No se puede cancelar un intento ya calificado"
        }
        this.status = SubmissionStatus.CANCELLED
    }

    fun attachScannedFile(filePath: String) {
        this.scannedFilePath = filePath
    }
}

