package com.sigep.courses.domain.event

import java.time.LocalDateTime

/**
 * Event emitted when a new certificate is issued
 */
data class CertificateIssuedEvent(
    val certificateId: Long,
    val certificateCode: String,
    val studentId: Long,
    val courseId: Long,
    val courseName: String,
    val finalGrade: java.math.BigDecimal,
    val honors: String?,
    val issueDate: java.time.LocalDate,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

/**
 * Event emitted when a new course material is uploaded
 */
data class CourseMaterialUploadedEvent(
    val materialId: Long,
    val courseId: Long,
    val courseName: String,
    val title: String,
    val type: String,
    val uploadedBy: Long,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

/**
 * Event emitted when attendance is recorded
 */
data class AttendanceRecordedEvent(
    val attendanceId: Long,
    val enrollmentId: Long,
    val studentId: Long,
    val courseId: Long,
    val courseName: String,
    val attendanceDate: java.time.LocalDate,
    val status: String,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

/**
 * Event emitted when a course is published
 */
data class CoursePublishedEvent(
    val courseId: Long,
    val courseCode: String,
    val courseName: String,
    val level: String,
    val startDate: java.time.LocalDate?,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

