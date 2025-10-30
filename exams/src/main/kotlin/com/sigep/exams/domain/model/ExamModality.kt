package com.sigep.exams.domain.model

/**
 * Modalidad del examen
 * Fase 1: Solo OFFLINE (presencial con carga de notas)
 * Fase 2: ONLINE (rendición en plataforma)
 */
enum class ExamModality {
    /**
     * Examen presencial - El docente carga las notas manualmente
     */
    OFFLINE,

    /**
     * Examen online - Para Fase 2 (futuro)
     * Permitirá banco de preguntas, auto-corrección, etc.
     */
    ONLINE
}

