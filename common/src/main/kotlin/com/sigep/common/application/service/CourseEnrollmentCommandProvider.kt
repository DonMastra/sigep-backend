package com.sigep.common.application.service

import com.sigep.common.application.dto.EnrollmentSummaryDto

interface CourseEnrollmentCommandProvider {
    fun getCourseSeatAvailability(courseId: Long): CourseSeatAvailability
    fun createActiveEnrollment(studentId: Long, courseId: Long, notes: String?): CourseEnrollmentResult
    fun getLatestCompletedEnrollment(studentId: Long): EnrollmentSummaryDto?
}

data class CourseSeatAvailability(
    val courseId: Long,
    val courseName: String,
    val courseLevel: String,
    val maxStudents: Int,
    val activeEnrollments: Int,
    val availableSeats: Int,
    val enrollmentOpen: Boolean
)

data class CourseEnrollmentResult(
    val enrollmentId: Long,
    val studentId: Long,
    val courseId: Long,
    val courseName: String,
    val courseLevel: String
)
