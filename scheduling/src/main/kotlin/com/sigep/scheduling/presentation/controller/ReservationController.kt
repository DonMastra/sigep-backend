package com.sigep.scheduling.presentation.controller

import com.sigep.common.application.dto.ApiResponse
import com.sigep.common.application.dto.PageResponse
import com.sigep.scheduling.application.dto.*
import com.sigep.scheduling.application.service.ReservationService
import com.sigep.scheduling.domain.model.ReservationStatus
import com.sigep.scheduling.domain.model.ReservationTargetType
import com.sigep.scheduling.domain.model.SlotDayOfWeek
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/scheduling/reservations")
@Tag(name = "Reservations", description = "Reservation management - assign slots to courses or sessions")
class ReservationController(private val reservationService: ReservationService) {

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    fun getReservations(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) status: ReservationStatus?,
        @RequestParam(required = false) targetType: ReservationTargetType?,
        @RequestParam(required = false) classroomId: Long?,
        @RequestParam(required = false) dayOfWeek: SlotDayOfWeek?,
        @RequestParam(required = false) startTimeFrom: String?,
        @RequestParam(required = false) endTimeTo: String?
    ): ResponseEntity<ApiResponse<PageResponse<ReservationDto>>> {
        val pageSize = limit ?: size ?: 20
        return ResponseEntity.ok(
            ApiResponse.success(
                reservationService.getReservations(
                    page = page,
                    size = pageSize,
                    status = status,
                    targetType = targetType,
                    classroomId = classroomId,
                    dayOfWeek = dayOfWeek,
                    startTimeFrom = startTimeFrom,
                    endTimeTo = endTimeTo
                )
            )
        )
    }

    @GetMapping("/available")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    fun getAvailableReservations(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) classroomId: Long?,
        @RequestParam(required = false) dayOfWeek: SlotDayOfWeek?,
        @RequestParam(required = false) startTimeFrom: String?,
        @RequestParam(required = false) endTimeTo: String?
    ): ResponseEntity<ApiResponse<PageResponse<ReservationDto>>> {
        val pageSize = limit ?: size ?: 20
        return ResponseEntity.ok(
            ApiResponse.success(
                reservationService.getAvailableReservations(
                    page = page,
                    size = pageSize,
                    classroomId = classroomId,
                    dayOfWeek = dayOfWeek,
                    startTimeFrom = startTimeFrom,
                    endTimeTo = endTimeTo
                )
            )
        )
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    fun getReservationById(@PathVariable id: Long): ResponseEntity<ApiResponse<ReservationDto>> {
        return ResponseEntity.ok(ApiResponse.success(reservationService.getReservationById(id)))
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    fun createReservation(@Valid @RequestBody request: CreateReservationRequest): ResponseEntity<ApiResponse<ReservationDto>> {
        val created = reservationService.createReservation(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created, "Reservation created"))
    }

    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasRole('ADMIN')")
    fun assignReservation(
        @PathVariable id: Long,
        @Valid @RequestBody request: AssignReservationRequest
    ): ResponseEntity<ApiResponse<ReservationDto>> {
        return ResponseEntity.ok(ApiResponse.success(reservationService.assignReservation(id, request), "Reservation assigned"))
    }

    @PatchMapping("/{id}/unassign")
    @PreAuthorize("hasRole('ADMIN')")
    fun unassignReservation(@PathVariable id: Long): ResponseEntity<ApiResponse<ReservationDto>> {
        return ResponseEntity.ok(ApiResponse.success(reservationService.unassignReservation(id), "Reservation unassigned"))
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    fun inactivateReservation(@PathVariable id: Long): ResponseEntity<ApiResponse<ReservationDto>> {
        return ResponseEntity.ok(ApiResponse.success(reservationService.inactivateReservation(id), "Reservation inactivated"))
    }
}
