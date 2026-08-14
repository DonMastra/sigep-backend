package com.sigep.common.application.service

/**
 * Contrato compartido para resolver información básica de docentes entre módulos.
 */
interface TeacherInfoProvider {

    /**
     * Obtiene nombres de docentes para una colección de IDs.
     */
    fun getTeacherNamesByIds(teacherIds: Collection<Long>): Map<Long, String>

    /**
     * Obtiene el nombre de un docente por ID, si existe y está activo.
     */
    fun getTeacherNameById(teacherId: Long): String? =
        getTeacherNamesByIds(listOf(teacherId))[teacherId]
}

