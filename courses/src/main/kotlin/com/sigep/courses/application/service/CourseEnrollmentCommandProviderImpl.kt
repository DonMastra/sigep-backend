package com.sigep.courses.application.service

import com.sigep.common.application.dto.EnrollmentSummaryDto
import com.sigep.common.application.exception.BusinessException
import com.sigep.common.application.exception.ResourceNotFoundException
import com.sigep.common.application.service.CourseEnrollmentCommandProvider
import com.sigep.common.application.service.CourseEnrollmentResult
import com.sigep.common.application.service.CourseSeatAvailability
import com.sigep.common.application.service.ReservationInfoProvider
import com.sigep.courses.domain.model.CourseStatus
import com.sigep.courses.domain.model.Enrollment
import com.sigep.courses.domain.model.EnrollmentStatus
import com.sigep.courses.domain.repository.CourseRepository
import com.sigep.courses.domain.repository.EnrollmentRepository
import org.springframework.beans.factory.ObjectProvider
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class CourseEnrollmentCommandProviderImpl(
    private val courseRepository: CourseRepository,
    private val enrollmentRepository: EnrollmentRepository,
    private val reservationInfoProviderProvider: ObjectProvider<ReservationInfoProvider>
) : CourseEnrollmentCommandProvider {

    override fun getCourseSeatAvailability(courseId: Long): CourseSeatAvailability {
        val course = courseRepository.findById(courseId)
            .orElseThrow { ResourceNotFoundException("Course not found with id: $courseId") }

        val activeEnrollments = enrollmentRepository.countActiveEnrollmentsByCourse(courseId).toInt()
        val hasReservation = reservationInfoProviderProvider.getIfAvailable()?.hasReservationAssigned(courseId) ?: true
        val availableSeats = (course.maxStudents - activeEnrollments).coerceAtLeast(0)
        val enrollmentOpen = course.isPublished &&
            course.status == CourseStatus.ACTIVE &&
            availableSeats > 0 &&
            hasReservation &&
            (course.startDate == null || !course.startDate.isBefore(LocalDate.now()))

        return CourseSeatAvailability(
            courseId = course.id!!,
            courseName = course.name,
            courseLevel = course.level.name,
            maxStudents = course.maxStudents,
            activeEnrollments = activeEnrollments,
            availableSeats = availableSeats,
            enrollmentOpen = enrollmentOpen
        )
    }

    override fun createActiveEnrollment(
        studentId: Long,
        courseId: Long,
        notes: String?
    ): CourseEnrollmentResult {
        val course = courseRepository.findById(courseId)
            .orElseThrow { ResourceNotFoundException("Course not found with id: $courseId") }

        val existingEnrollment = enrollmentRepository.findByStudentIdAndCourseId(studentId, courseId)
        if (existingEnrollment.isPresent && existingEnrollment.get().status == EnrollmentStatus.ACTIVE) {
            throw BusinessException("Student is already enrolled in this course")
        }

        val activeEnrollments = enrollmentRepository.countActiveEnrollmentsByCourse(courseId)
        if (activeEnrollments >= course.maxStudents) {
            throw BusinessException("Course is full. Maximum students: ${course.maxStudents}")
        }

        val now = LocalDateTime.now()
        val enrollment = Enrollment(
            studentId = studentId,
            course = course,
            enrollmentDate = LocalDate.now(),
            status = EnrollmentStatus.ACTIVE,
            notes = notes,
            createdAt = now,
            updatedAt = now
        )
        val savedEnrollment = enrollmentRepository.save(enrollment)

        return CourseEnrollmentResult(
            enrollmentId = savedEnrollment.id!!,
            studentId = studentId,
            courseId = course.id!!,
            courseName = course.name,
            courseLevel = course.level.name
        )
    }

    override fun getLatestCompletedEnrollment(studentId: Long): EnrollmentSummaryDto? {
        val pageable = PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "completionDate", "enrollmentDate"))
        return enrollmentRepository.findByStudentIdAndStatus(studentId, EnrollmentStatus.COMPLETED, pageable)
            .content
            .maxWithOrNull(compareBy<Enrollment> { it.completionDate ?: it.enrollmentDate }.thenBy { it.id ?: 0L })
            ?.let { enrollment ->
                EnrollmentSummaryDto(
                    id = enrollment.id!!,
                    studentId = enrollment.studentId,
                    courseId = enrollment.course.id!!,
                    courseName = enrollment.course.name,
                    courseLevel = enrollment.course.level.name,
                    enrollmentDate = enrollment.enrollmentDate,
                    status = enrollment.status.name,
                    finalGrade = enrollment.finalGrade,
                    completionDate = enrollment.completionDate
                )
            }
    }
}
