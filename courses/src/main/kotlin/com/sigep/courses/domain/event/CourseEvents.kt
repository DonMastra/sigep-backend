package com.sigep.courses.domain.event

import java.time.LocalDateTime

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

data class CourseMaterialUploadedEvent(
    val materialId: Long,
    val courseId: Long,
    val courseName: String,
    val title: String,
    val type: String,
    val uploadedBy: Long,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

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

data class CoursePublishedEvent(
    val courseId: Long,
    val courseCode: String,
    val courseName: String,
    val level: String,
    val startDate: java.time.LocalDate?,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

