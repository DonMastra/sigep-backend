package com.sigep.students.domain.repository

import com.sigep.students.domain.model.Student
import com.sigep.students.domain.model.StudentDocumentType
import com.sigep.students.domain.model.StudentGuardianLinkEvent
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.Optional

@Repository
interface StudentRepository : JpaRepository<Student, Long> {
    fun findByDocumentTypeAndDocumentCountryAndNormalizedDocumentNumber(
        documentType: StudentDocumentType,
        documentCountry: String,
        normalizedDocumentNumber: String
    ): Optional<Student>

    fun existsByDocumentTypeAndDocumentCountryAndNormalizedDocumentNumber(
        documentType: StudentDocumentType,
        documentCountry: String,
        normalizedDocumentNumber: String
    ): Boolean

    @Query("""
        SELECT s FROM Student s
        WHERE s.dateOfBirth = :dateOfBirth
        AND LOWER(TRIM(s.firstName)) = LOWER(TRIM(:firstName))
        AND LOWER(TRIM(s.lastName)) = LOWER(TRIM(:lastName))
    """)
    fun findPotentialMatches(
        @Param("firstName") firstName: String,
        @Param("lastName") lastName: String,
        @Param("dateOfBirth") dateOfBirth: LocalDate
    ): List<Student>
    fun findByActive(active: Boolean, pageable: Pageable): Page<Student>
    fun findByGuardianId(guardianId: Long, pageable: Pageable): Page<Student>
    fun findByIdIn(ids: Collection<Long>, pageable: Pageable): Page<Student>

    @Query("""
        SELECT s FROM Student s WHERE 
        LOWER(s.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR 
        LOWER(s.lastName) LIKE LOWER(CONCAT('%', :search, '%')) OR 
        LOWER(s.email) LIKE LOWER(CONCAT('%', :search, '%')) OR
        LOWER(s.studentNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR
        LOWER(COALESCE(s.documentNumber, '')) LIKE LOWER(CONCAT('%', :search, '%'))
    """)
    fun searchStudents(@Param("search") search: String, pageable: Pageable): Page<Student>

    @Query("""
        SELECT s FROM Student s WHERE s.id IN :studentIds AND (
        LOWER(s.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR
        LOWER(s.lastName) LIKE LOWER(CONCAT('%', :search, '%')) OR
        LOWER(s.email) LIKE LOWER(CONCAT('%', :search, '%')) OR
        LOWER(s.studentNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR
        LOWER(COALESCE(s.documentNumber, '')) LIKE LOWER(CONCAT('%', :search, '%')))
    """)
    fun searchStudentsByIds(
        @Param("search") search: String,
        @Param("studentIds") studentIds: Collection<Long>,
        pageable: Pageable
    ): Page<Student>
}

@Repository
interface StudentGuardianLinkEventRepository : JpaRepository<StudentGuardianLinkEvent, Long>

