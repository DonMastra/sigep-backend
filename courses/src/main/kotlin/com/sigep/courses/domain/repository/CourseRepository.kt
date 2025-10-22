package com.sigep.courses.domain.repository

import com.sigep.courses.domain.model.Course
import com.sigep.courses.domain.model.CourseStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface CourseRepository : JpaRepository<Course, Long> {
    fun findByStatus(status: CourseStatus, pageable: Pageable): Page<Course>
    fun findByTeacherId(teacherId: Long, pageable: Pageable): Page<Course>
    fun findByLevel(level: String, pageable: Pageable): Page<Course>

    @Query("SELECT c FROM Course c WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(c.description) LIKE LOWER(CONCAT('%', :search, '%'))")
    fun searchCourses(search: String, pageable: Pageable): Page<Course>
}

