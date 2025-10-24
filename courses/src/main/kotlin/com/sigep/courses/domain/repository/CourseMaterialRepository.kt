package com.sigep.courses.domain.repository

import com.sigep.courses.domain.model.CourseMaterial
import com.sigep.courses.domain.model.MaterialType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface CourseMaterialRepository : JpaRepository<CourseMaterial, Long> {

    fun findByCourseId(courseId: Long, pageable: Pageable): Page<CourseMaterial>

    fun findByCourseIdAndIsVisibleTrue(courseId: Long, pageable: Pageable): Page<CourseMaterial>

    fun findByCourseIdOrderByOrderIndexAsc(courseId: Long): List<CourseMaterial>

    fun findByCourseIdAndType(courseId: Long, type: MaterialType, pageable: Pageable): Page<CourseMaterial>

    @Query("SELECT cm FROM CourseMaterial cm WHERE cm.course.id = :courseId AND cm.isVisible = true ORDER BY cm.orderIndex ASC")
    fun findVisibleByCourseIdOrderByIndex(courseId: Long): List<CourseMaterial>

    fun countByCourseId(courseId: Long): Long

    fun countByCourseIdAndType(courseId: Long, type: MaterialType): Long
}

