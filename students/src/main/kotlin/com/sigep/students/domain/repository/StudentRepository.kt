package com.sigep.students.domain.repository

import com.sigep.students.domain.model.Student
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface StudentRepository : JpaRepository<Student, Long> {
    fun findByEmail(email: String): Optional<Student>
    fun existsByEmail(email: String): Boolean
    fun existsByDocumentNumber(documentNumber: String): Boolean
    fun findByActive(active: Boolean, pageable: Pageable): Page<Student>
    fun findByGuardianId(guardianId: Long, pageable: Pageable): Page<Student>

    @Query("""
        SELECT s FROM Student s WHERE 
        LOWER(s.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR 
        LOWER(s.lastName) LIKE LOWER(CONCAT('%', :search, '%')) OR 
        LOWER(s.email) LIKE LOWER(CONCAT('%', :search, '%')) OR
        LOWER(s.documentNumber) LIKE LOWER(CONCAT('%', :search, '%'))
    """)
    fun searchStudents(@Param("search") search: String, pageable: Pageable): Page<Student>
}

