package com.sigep.common.application.service

/**
 * Cross-module interface for Reservation data.
 * Declared in common, implemented by the scheduling module.
 * Allows courses module to query reservation info without a direct dependency.
 * Designed for future microservice extraction.
 */
interface ReservationInfoProvider {

    /**
     * Returns the ASSIGNED reservation summary for a given course, or null if none.
     */
    fun getReservationByCourse(courseId: Long): ReservationInfo?

    /**
     * Returns true if the course has an ASSIGNED reservation.
     */
    fun hasReservationAssigned(courseId: Long): Boolean
}

/**
 * Lightweight DTO carrying reservation + slot + classroom info across module boundaries.
 */
data class ReservationInfo(
    val reservationId: Long,
    val status: String,
    val slotId: Long,
    val dayOfWeek: String,
    val startTime: String,
    val endTime: String,
    val classroomId: Long,
    val classroomName: String,
    val building: String?,
    val floor: String?,
    val classroomCapacity: Int
)

