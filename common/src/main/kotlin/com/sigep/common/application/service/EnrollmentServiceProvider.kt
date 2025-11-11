package com.sigep.common.application.service

import com.sigep.common.application.dto.EnrollmentSummaryDto

/**
 * Interfaz para servicios de Enrollment
 * Permite a otros módulos obtener información de enrollments sin dependencias circulares
 */
interface EnrollmentServiceProvider {

    /**
     * Obtiene todos los enrollments de un estudiante
     */
    fun getEnrollmentsByStudent(studentId: Long): List<EnrollmentSummaryDto>

    /**
     * Obtiene el enrollment activo actual de un estudiante (si existe)
     */
    fun getCurrentEnrollmentByStudent(studentId: Long): EnrollmentSummaryDto?

    /**
     * Obtiene los enrollments de un estudiante por estado
     */
    fun getEnrollmentsByStudentAndStatus(studentId: Long, status: String): List<EnrollmentSummaryDto>
}

