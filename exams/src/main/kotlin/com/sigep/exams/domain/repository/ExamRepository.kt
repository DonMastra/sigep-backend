package com.sigep.exams.domain.repository

import com.sigep.exams.domain.model.Exam
import com.sigep.exams.domain.model.ExamStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.UUID

@Repository
interface ExamRepository : JpaRepository<Exam, UUID> {

    fun findByCourseId(courseId: UUID, pageable: Pageable): Page<Exam>

    fun findByCourseIdAndStatus(courseId: UUID, status: ExamStatus, pageable: Pageable): Page<Exam>

    @Query("""
        SELECT e FROM Exam e 
        WHERE e.courseId = :courseId 
        AND e.status = :status
        AND e.scheduledAt BETWEEN :start AND :end
    """)
    fun findByCourseAndStatusAndScheduledBetween(
        @Param("courseId") courseId: UUID,
        @Param("status") status: ExamStatus,
        @Param("start") start: LocalDateTime,
        @Param("end") end: LocalDateTime
    ): List<Exam>

    @Query("""
        SELECT DISTINCT e FROM Exam e
        WHERE e.courseId IN :courseIds
        AND e.status IN :statuses
        ORDER BY e.scheduledAt DESC
    """)
    fun findByCoursesAndStatuses(
        @Param("courseIds") courseIds: List<UUID>,
        @Param("statuses") statuses: List<ExamStatus>,
        pageable: Pageable
    ): Page<Exam>

    @Query("""
        SELECT e FROM Exam e 
        WHERE e.status = 'PUBLISHED'
        AND e.visibilityStart <= :now
        AND (e.visibilityEnd IS NULL OR e.visibilityEnd >= :now)
    """)
    fun findVisibleExams(@Param("now") now: LocalDateTime, pageable: Pageable): Page<Exam>

    fun existsByCourseIdAndTitleAndIdNot(courseId: UUID, title: String, id: UUID): Boolean

    /**
     * Encuentra exámenes asignados a un docente específico
     */
    @Query("""
        SELECT e FROM Exam e 
        WHERE e.assignedTeachers LIKE CONCAT('%', :teacherId, '%')
    """)
    fun findByTeacherId(@Param("teacherId") teacherId: UUID, pageable: Pageable): Page<Exam>

    /**
     * Encuentra exámenes asignados a un docente con filtro por estado
     */
    @Query("""
        SELECT e FROM Exam e 
        WHERE e.assignedTeachers LIKE CONCAT('%', :teacherId, '%')
        AND e.status IN :statuses
    """)
    fun findByTeacherIdAndStatusIn(
        @Param("teacherId") teacherId: UUID,
        @Param("statuses") statuses: List<ExamStatus>,
        pageable: Pageable
    ): Page<Exam>

    /**
     * Cuenta exámenes por docente
     */
    @Query("""
        SELECT COUNT(e) FROM Exam e 
        WHERE e.assignedTeachers LIKE CONCAT('%', :teacherId, '%')
    """)
    fun countByTeacherId(@Param("teacherId") teacherId: UUID): Long

    /**
     * Encuentra exámenes de un docente en un rango de fechas
     */
    @Query("""
        SELECT e FROM Exam e 
        WHERE e.assignedTeachers LIKE CONCAT('%', :teacherId, '%')
        AND e.scheduledAt BETWEEN :start AND :end
        ORDER BY e.scheduledAt DESC
    """)
    fun findByTeacherIdAndScheduledBetween(
        @Param("teacherId") teacherId: UUID,
        @Param("start") start: LocalDateTime,
        @Param("end") end: LocalDateTime
    ): List<Exam>
}

