package com.sigep.exams.domain.model

/**
 * Estados del examen en su ciclo de vida
 */
enum class ExamStatus {
    /**
     * Borrador - Solo visible para docentes y administradores
     */
    DRAFT,

    /**
     * Publicado - Visible para estudiantes según ventana de visibilidad
     */
    PUBLISHED,

    /**
     * Cerrado - No se aceptan más cambios, notas finalizadas
     */
    CLOSED,

    /**
     * Cancelado - Examen cancelado (no se considerará)
     */
    CANCELLED
}

