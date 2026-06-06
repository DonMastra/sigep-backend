package com.sigep.scheduling.domain.repository

import com.sigep.scheduling.domain.model.Classroom
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ClassroomRepository : JpaRepository<Classroom, Long> {
    fun findByActiveTrue(): List<Classroom>
    fun existsByNameIgnoreCase(name: String): Boolean
}
