package com.sigep.exams.domain.model

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

// NOTE: Exam.id / ExamSubmission.id / ExamGradeHistory.id keep UUID as own PKs.
// Cross-module references (courseId, createdBy, updatedBy) use Long to match
// the BIGINT PKs of courses and users tables.

/**
 * Aggregate Root - Exam
 * Representa un examen presencial con gestión de calificaciones
 * Fase 1: Solo gestión y carga de notas (exámenes presenciales)
 * Fase 2: Agregará modalidad online con banco de preguntas
 */
@Entity
@Table(
    name = "exams",
    indexes = [
        Index(name = "idx_exam_course", columnList = "course_id"),
        Index(name = "idx_exam_source", columnList = "source_exam_id"),
        Index(name = "idx_exam_status", columnList = "status"),
        Index(name = "idx_exam_scheduled_at", columnList = "scheduled_at")
    ]
)
data class Exam(
    @Id
    @Column(columnDefinition = "UUID")
    val id: UUID = UUID.randomUUID(),

    @Column(name = "course_id", nullable = false)
    val courseId: Long,

    @Column(name = "source_exam_id", columnDefinition = "UUID")
    val sourceExamId: UUID? = null,

    @Column(nullable = false, length = 200)
    val title: String,

    @Column(columnDefinition = "TEXT")
    val description: String? = null,

    // Modalidad del examen - Fase 1 solo OFFLINE
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    val modality: ExamModality = ExamModality.OFFLINE,

    // Estado del examen
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: ExamStatus = ExamStatus.DRAFT,

    // Puntaje y ponderación
    @Column(name = "total_points", nullable = false, precision = 10, scale = 2)
    val totalPoints: BigDecimal = BigDecimal("100.00"),

    @Column(nullable = false, precision = 5, scale = 2)
    val weight: BigDecimal = BigDecimal("1.00"), // Peso relativo en el curso

    // Duración del examen en minutos (informativa para examen presencial)
    @Column(name = "time_limit_minutes")
    val timeLimitMinutes: Int? = null,

    // Fecha y hora programada
    @Column(name = "scheduled_at")
    var scheduledAt: LocalDateTime? = null,

    // Ventana de visibilidad para estudiantes
    @Column(name = "visibility_start")
    var visibilityStart: LocalDateTime? = null,

    @Column(name = "visibility_end")
    var visibilityEnd: LocalDateTime? = null,

    // Docentes asignados (JSON simple para Fase 1)
    @Column(name = "assigned_teachers", columnDefinition = "TEXT")
    var assignedTeachers: String? = null, // JSON array de IDs (Long) de teaching_staff

    // Notas adicionales del examen
    @Column(columnDefinition = "TEXT")
    var notes: String? = null,

    // Información del aula/sala
    @Column(name = "room_info", length = 100)
    var roomInfo: String? = null,

    // Versión para control de concurrencia optimista
    @Version
    var version: Int = 1,

    // Auditoría
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "created_by", nullable = false)
    val createdBy: Long,

    @Column(name = "updated_at")
    var updatedAt: LocalDateTime? = null,

    @Column(name = "updated_by")
    var updatedBy: Long? = null
) {
    fun publish() {
        require(status == ExamStatus.DRAFT) { "Solo se pueden publicar exámenes en borrador" }
        status = ExamStatus.PUBLISHED
    }

    fun close() {
        require(status == ExamStatus.PUBLISHED) { "Solo se pueden cerrar exámenes publicados" }
        status = ExamStatus.CLOSED
    }

    fun cancel() {
        require(status != ExamStatus.CLOSED) { "No se pueden cancelar exámenes cerrados" }
        status = ExamStatus.CANCELLED
    }

    fun isVisibleToStudents(): Boolean {
        if (status == ExamStatus.DRAFT) return false
        val now = LocalDateTime.now()
        return (visibilityStart == null || now.isAfter(visibilityStart)) &&
               (visibilityEnd == null || now.isBefore(visibilityEnd))
    }

    fun canBeEditedBy(isAdmin: Boolean, teacherId: Long): Boolean {
        if (isAdmin) return true
        if (status == ExamStatus.CLOSED) return false
        return assignedTeachers?.contains(teacherId.toString()) == true
    }
}

