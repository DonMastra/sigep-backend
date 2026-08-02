package com.sigep.courses.application.service

import com.sigep.common.application.service.SchedulingTargetValidationProvider
import com.sigep.courses.domain.model.CourseStatus
import com.sigep.courses.domain.repository.CourseRepository
import com.sigep.courses.domain.repository.CourseSessionRepository
import org.springframework.stereotype.Service

@Service
class CourseSchedulingValidationProviderImpl(
    private val courseRepository: CourseRepository,
    private val sessionRepository: CourseSessionRepository
) : SchedulingTargetValidationProvider {

    override fun courseExists(courseId: Long): Boolean =
        courseRepository.existsById(courseId)

    override fun sessionExists(sessionId: Long): Boolean =
        sessionRepository.existsById(sessionId)

    override fun isCourseOperational(courseId: Long): Boolean {
        val course = courseRepository.findById(courseId).orElse(null) ?: return false
        return course.isPublished || course.status == CourseStatus.ACTIVE
    }

    override fun getCourseIdsAssignedToTeacher(teacherUserId: Long): Set<Long> =
        courseRepository.findIdsByTeacherId(teacherUserId)

    override fun getSessionIdsAssignedToTeacher(teacherUserId: Long): Set<Long> =
        sessionRepository.findIdsByTeacherId(teacherUserId)
}

