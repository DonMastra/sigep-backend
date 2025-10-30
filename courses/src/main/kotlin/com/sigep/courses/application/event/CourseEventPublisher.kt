package com.sigep.courses.application.event

import com.sigep.courses.domain.event.*
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

@Service
class CourseEventPublisher(
    private val eventPublisher: ApplicationEventPublisher
) {

    private val logger = LoggerFactory.getLogger(CourseEventPublisher::class.java)

    fun publishCertificateIssued(event: CertificateIssuedEvent) {
        logger.info("Publishing CertificateIssuedEvent for certificate: {}", event.certificateCode)
        eventPublisher.publishEvent(event)
    }

    fun publishMaterialUploaded(event: CourseMaterialUploadedEvent) {
        logger.info("Publishing CourseMaterialUploadedEvent for material: {}", event.materialId)
        eventPublisher.publishEvent(event)
    }

    fun publishAttendanceRecorded(event: AttendanceRecordedEvent) {
        logger.debug("Publishing AttendanceRecordedEvent for enrollment: {}", event.enrollmentId)
        eventPublisher.publishEvent(event)
    }

    fun publishCoursePublished(event: CoursePublishedEvent) {
        logger.info("Publishing CoursePublishedEvent for course: {}", event.courseName)
        eventPublisher.publishEvent(event)
    }
}

