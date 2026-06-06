package com.sigep.common.application.service

/**
 * Cross-module validator for scheduling assignment targets.
 * Declared in common, implemented by courses.
 */
interface SchedulingTargetValidationProvider {
    fun courseExists(courseId: Long): Boolean
    fun sessionExists(sessionId: Long): Boolean

    /**
     * A course is operational when it is published or active for enrollment.
     */
    fun isCourseOperational(courseId: Long): Boolean
}

