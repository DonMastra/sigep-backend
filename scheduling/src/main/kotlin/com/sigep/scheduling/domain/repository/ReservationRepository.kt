package com.sigep.scheduling.domain.repository

import com.sigep.scheduling.domain.model.Reservation
import com.sigep.scheduling.domain.model.ReservationStatus
import com.sigep.scheduling.domain.model.ReservationTargetType
import com.sigep.scheduling.domain.model.SlotDayOfWeek
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface ReservationRepository : JpaRepository<Reservation, Long> {
    fun findByStatus(status: ReservationStatus, pageable: Pageable): Page<Reservation>
    fun findByTargetType(targetType: ReservationTargetType, pageable: Pageable): Page<Reservation>
    fun findByStatusAndTargetType(
        status: ReservationStatus,
        targetType: ReservationTargetType,
        pageable: Pageable
    ): Page<Reservation>
    fun findByTargetTypeAndTargetIdAndStatus(
        targetType: ReservationTargetType,
        targetId: Long,
        status: ReservationStatus
    ): Optional<Reservation>
    fun existsByTargetTypeAndTargetIdAndStatus(
        targetType: ReservationTargetType,
        targetId: Long,
        status: ReservationStatus
    ): Boolean

    fun findBySlotIdAndStatusNot(slotId: Long, status: ReservationStatus): List<Reservation>

    fun existsBySlotIdAndStatus(slotId: Long, status: ReservationStatus): Boolean

    @Query(
        """
        SELECT r FROM Reservation r
        JOIN r.slot s
        JOIN s.classroom c
        WHERE (:status IS NULL OR r.status = :status)
          AND (:targetType IS NULL OR r.targetType = :targetType)
          AND (:classroomId IS NULL OR c.id = :classroomId)
          AND (:dayOfWeek IS NULL OR s.dayOfWeek = :dayOfWeek)
          AND (:startTimeFrom IS NULL OR s.startTime >= :startTimeFrom)
          AND (:endTimeTo IS NULL OR s.endTime <= :endTimeTo)
    """
    )
    fun findByFilters(
        @Param("status") status: ReservationStatus?,
        @Param("targetType") targetType: ReservationTargetType?,
        @Param("classroomId") classroomId: Long?,
        @Param("dayOfWeek") dayOfWeek: SlotDayOfWeek?,
        @Param("startTimeFrom") startTimeFrom: String?,
        @Param("endTimeTo") endTimeTo: String?,
        pageable: Pageable
    ): Page<Reservation>

    @Query(
        """
        SELECT r FROM Reservation r
        JOIN r.slot s
        JOIN s.classroom c
        WHERE (:status IS NULL OR r.status = :status)
          AND (:targetType IS NULL OR r.targetType = :targetType)
          AND (:classroomId IS NULL OR c.id = :classroomId)
          AND (:dayOfWeek IS NULL OR s.dayOfWeek = :dayOfWeek)
          AND (:startTimeFrom IS NULL OR s.startTime >= :startTimeFrom)
          AND (:endTimeTo IS NULL OR s.endTime <= :endTimeTo)
          AND ((r.targetType = :courseTargetType AND r.targetId IN :courseIds)
            OR (r.targetType = :sessionTargetType AND r.targetId IN :sessionIds))
    """
    )
    fun findByFiltersForTeacher(
        @Param("status") status: ReservationStatus?,
        @Param("targetType") targetType: ReservationTargetType?,
        @Param("classroomId") classroomId: Long?,
        @Param("dayOfWeek") dayOfWeek: SlotDayOfWeek?,
        @Param("startTimeFrom") startTimeFrom: String?,
        @Param("endTimeTo") endTimeTo: String?,
        @Param("courseTargetType") courseTargetType: ReservationTargetType,
        @Param("sessionTargetType") sessionTargetType: ReservationTargetType,
        @Param("courseIds") courseIds: Collection<Long>,
        @Param("sessionIds") sessionIds: Collection<Long>,
        pageable: Pageable
    ): Page<Reservation>
}
