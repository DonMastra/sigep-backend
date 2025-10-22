package com.sigep.courses.domain.model

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(name = "enrollments")
data class Enrollment(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false)
    val studentId: Long,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    val course: Course,

    @Column(nullable = false)
    val enrollmentDate: LocalDate = LocalDate.now(),

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: EnrollmentStatus = EnrollmentStatus.ACTIVE,

    @Column(precision = 5, scale = 2)
    val finalGrade: BigDecimal? = null, // Nota final (null si aún no completó)

    @Column
    val completionDate: LocalDate? = null,

    @Column(length = 1000)
    val notes: String? = null, // Notas adicionales del profesor

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now()
)

enum class EnrollmentStatus {
    ACTIVE,      // Cursando actualmente
    COMPLETED,   // Completado exitosamente
    FAILED,      // Reprobado
    DROPPED,     // Abandonó el curso
    SUSPENDED    // Suspendido temporalmente
}

