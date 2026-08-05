package com.sigep.courses.application.service

import com.sigep.common.application.service.CourseAccessInfo
import com.sigep.common.application.service.CourseAccessProvider
import com.sigep.courses.domain.repository.CourseRepository
import com.sigep.courses.domain.repository.EnrollmentRepository
import org.springframework.stereotype.Service

@Service
class CourseAccessProviderImpl(
    private val courseRepository: CourseRepository,
    private val enrollmentRepository: EnrollmentRepository
) : CourseAccessProvider {

    override fun getCourseInfo(courseId: Long): CourseAccessInfo? =
        courseRepository.findById(courseId).map { course ->
            CourseAccessInfo(
                id = course.id!!,
                code = course.code,
                name = course.name,
                teacherUserId = course.teacherId
            )
        }.orElse(null)

    override fun getCourseInfo(courseIds: Collection<Long>): Map<Long, CourseAccessInfo> {
        if (courseIds.isEmpty()) return emptyMap()

        return courseRepository.findAllById(courseIds.distinct())
            .associate { course ->
                course.id!! to CourseAccessInfo(
                    id = course.id,
                    code = course.code,
                    name = course.name,
                    teacherUserId = course.teacherId
                )
            }
    }

    override fun getCourseIdsAssignedToTeacher(teacherUserId: Long): Set<Long> =
        courseRepository.findIdsByTeacherId(teacherUserId)

    override fun getActiveStudentIds(courseId: Long): Set<Long> =
        enrollmentRepository.findActiveStudentIdsByCourse(courseId)
}
