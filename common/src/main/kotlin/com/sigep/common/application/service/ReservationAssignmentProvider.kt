package com.sigep.common.application.service

/**
 * Cross-module command interface to assign reservations from courses without
 * a direct dependency on scheduling.
 */
interface ReservationAssignmentProvider {
    fun assignReservationToCourse(reservationId: Long, courseId: Long)

    /**
     * Reconciles the complete reservation selection for a course atomically.
     */
    fun syncCourseReservations(courseId: Long, reservationIds: Set<Long>)
}

