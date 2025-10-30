package com.sigep.exams.domain.model

/**
 * Estados de un intento/submission de examen
 */
enum class SubmissionStatus {
    /**
     * Pendiente de calificación
     */
    PENDING,

    /**
     * Calificado
     */
    GRADED,

    /**
     * Cancelado (por ausencia, copia, etc.)
     */
    CANCELLED,

    /**
     * Pendiente de revisión (para apelaciones)
     */
    UNDER_REVIEW
}

