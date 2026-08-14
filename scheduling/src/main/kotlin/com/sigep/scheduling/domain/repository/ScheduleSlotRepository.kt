package com.sigep.scheduling.domain.repository

import com.sigep.scheduling.domain.model.ScheduleSlot
import com.sigep.scheduling.domain.model.SlotDayOfWeek
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ScheduleSlotRepository : JpaRepository<ScheduleSlot, Long> {
    fun findByActiveTrue(pageable: Pageable): Page<ScheduleSlot>
    fun findByClassroomIdAndActiveTrue(classroomId: Long, pageable: Pageable): Page<ScheduleSlot>
    fun findByClassroomIdAndDayOfWeekAndActiveTrue(classroomId: Long, dayOfWeek: SlotDayOfWeek): List<ScheduleSlot>
}
