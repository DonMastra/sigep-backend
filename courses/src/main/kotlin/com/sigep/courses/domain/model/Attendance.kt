package com.sigep.courses.domain.model

import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(name = "course_attendance", indexes = [
    Index(name = "idx_attendance_enrollment", columnList = "enrollment_id"),
    Index(name = "idx_attendance_date", columnList = "attendance_date")
])
data class Attendance(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enrollment_id", nullable = false)
    val enrollment: Enrollment,

    @Column(nullable = false)
    val attendanceDate: LocalDate,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: AttendanceStatus,

    @Column(length = 500)
    val notes: String? = null, // Razón de ausencia o notas adicionales

    @Column(nullable = false)
    val recordedBy: Long, // ID del usuario que registró la asistencia (teacher/admin)

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now()
)

enum class AttendanceStatus {
    PRESENT,           // Presente
    ABSENT,            // Ausente
    LATE,              // Llegó tarde
    EXCUSED_ABSENCE,   // Ausencia justificada
    SICK_LEAVE         // Ausencia por enfermedad
}

