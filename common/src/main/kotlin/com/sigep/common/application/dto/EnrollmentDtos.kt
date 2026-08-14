package com.sigep.common.application.dto

import java.math.BigDecimal
import java.time.LocalDate

/**
 * DTO simplificado de Enrollment para uso entre módulos
 */
data class EnrollmentSummaryDto(
    val id: Long,
    val studentId: Long,
    val courseId: Long,
    val courseName: String,
    val courseLevel: String,  // BEGINNER, INTERMEDIATE, ADVANCED
    val enrollmentDate: LocalDate,
    val status: String,
    val finalGrade: BigDecimal?,
    val completionDate: LocalDate?
)

/**
 * Enum para estados de enrollment (replicado en common para evitar dependencias)
 */
enum class EnrollmentStatusDto {
    ACTIVE,
    COMPLETED,
    FAILED,
    DROPPED,
    SUSPENDED
}

