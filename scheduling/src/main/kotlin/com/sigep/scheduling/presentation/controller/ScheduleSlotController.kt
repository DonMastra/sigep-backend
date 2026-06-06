package com.sigep.scheduling.presentation.controller

import com.sigep.common.application.dto.ApiResponse
import com.sigep.common.application.dto.PageResponse
import com.sigep.scheduling.application.dto.*
import com.sigep.scheduling.application.service.ScheduleSlotService
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/scheduling/slots")
@Tag(name = "Schedule Slots", description = "Time slot management per classroom")
class ScheduleSlotController(private val slotService: ScheduleSlotService) {

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    fun getSlots(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) classroomId: Long?
    ): ResponseEntity<ApiResponse<PageResponse<ScheduleSlotDto>>> {
        val pageSize = limit ?: size ?: 20
        return ResponseEntity.ok(ApiResponse.success(slotService.getAllSlots(page, pageSize, classroomId)))
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    fun getSlotById(@PathVariable id: Long): ResponseEntity<ApiResponse<ScheduleSlotDto>> {
        return ResponseEntity.ok(ApiResponse.success(slotService.getSlotById(id)))
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    fun createSlot(@Valid @RequestBody request: CreateScheduleSlotRequest): ResponseEntity<ApiResponse<ScheduleSlotDto>> {
        val created = slotService.createSlot(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created, "Schedule slot created"))
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    fun updateSlot(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateScheduleSlotRequest
    ): ResponseEntity<ApiResponse<ScheduleSlotDto>> {
        return ResponseEntity.ok(ApiResponse.success(slotService.updateSlot(id, request), "Slot updated"))
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    fun deleteSlot(@PathVariable id: Long): ResponseEntity<ApiResponse<ScheduleSlotDto>> {
        return ResponseEntity.ok(ApiResponse.success(slotService.softDeleteSlot(id), "Slot deactivated"))
    }
}
