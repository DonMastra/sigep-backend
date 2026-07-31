package com.sigep.courses.domain.repository

import com.sigep.courses.domain.model.Course
import com.sigep.courses.domain.model.CourseStatus
import com.sigep.courses.domain.model.CourseLevel
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.math.BigDecimal

@Repository
interface CourseRepository : JpaRepository<Course, Long> {

    fun existsByCodeIgnoreCase(code: String): Boolean

    fun findByStatus(status: CourseStatus, pageable: Pageable): Page<Course>

    fun findByTeacherId(teacherId: Long, pageable: Pageable): Page<Course>

    fun findByTeacherIdAndIsPublishedTrue(teacherId: Long, pageable: Pageable): Page<Course>

    fun findAllByTeacherId(teacherId: Long): List<Course>

    @Query("SELECT c.id FROM Course c WHERE c.teacherId = :teacherId")
    fun findIdsByTeacherId(@Param("teacherId") teacherId: Long): Set<Long>

    fun findByLevel(level: CourseLevel, pageable: Pageable): Page<Course>

    fun findByIsPublishedTrue(pageable: Pageable): Page<Course>

    fun countByStatus(status: CourseStatus): Long

    fun countByLevel(level: CourseLevel): Long

    fun countByIsPublishedTrue(): Long

    @Query("SELECT c FROM Course c WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(c.description) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(c.code) LIKE LOWER(CONCAT('%', :search, '%'))")
    fun searchCourses(search: String, pageable: Pageable): Page<Course>

    @Query("""
        SELECT c FROM Course c
        WHERE c.isPublished = true
        AND (LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%'))
          OR LOWER(c.description) LIKE LOWER(CONCAT('%', :search, '%'))
          OR LOWER(c.code) LIKE LOWER(CONCAT('%', :search, '%')))
    """)
    fun searchPublishedCourses(@Param("search") search: String, pageable: Pageable): Page<Course>

    @Query("""
        SELECT c FROM Course c
        WHERE c.teacherId = :teacherUserId
        AND (LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%'))
          OR LOWER(c.description) LIKE LOWER(CONCAT('%', :search, '%'))
          OR LOWER(c.code) LIKE LOWER(CONCAT('%', :search, '%')))
    """)
    fun searchCoursesForTeacher(
        @Param("search") search: String,
        @Param("teacherUserId") teacherUserId: Long,
        pageable: Pageable
    ): Page<Course>

    @Query("""
        SELECT c FROM Course c 
        WHERE (:level IS NULL OR c.level = :level)
        AND (:status IS NULL OR c.status = :status)
        AND (:teacherId IS NULL OR c.teacherId = :teacherId)
        AND (:isPublished IS NULL OR c.isPublished = :isPublished)
        AND (:minPrice IS NULL OR c.price >= :minPrice)
        AND (:maxPrice IS NULL OR c.price <= :maxPrice)
    """)
    fun filterCourses(
        @Param("level") level: CourseLevel?,
        @Param("status") status: CourseStatus?,
        @Param("teacherId") teacherId: Long?,
        @Param("isPublished") isPublished: Boolean?,
        @Param("minPrice") minPrice: BigDecimal?,
        @Param("maxPrice") maxPrice: BigDecimal?,
        pageable: Pageable
    ): Page<Course>
}

