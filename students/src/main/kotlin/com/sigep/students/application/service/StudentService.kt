package com.sigep.students.application.service

import com.sigep.common.application.dto.PageResponse
import com.sigep.common.domain.exception.ResourceNotFoundException
import com.sigep.common.domain.exception.DuplicateResourceException
import com.sigep.common.application.service.EnrollmentServiceProvider
import com.sigep.students.application.dto.*
import com.sigep.students.domain.model.Student
import com.sigep.students.domain.repository.StudentRepository
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

@Service
@Transactional
class StudentService(
    private val studentRepository: StudentRepository,
    private val enrollmentServiceProvider: EnrollmentServiceProvider  // Inyectamos la interfaz, no el repositorio
) {

    private val logger = LoggerFactory.getLogger(StudentService::class.java)

    @Cacheable(value = ["students"], key = "#id")
    fun getStudentById(id: Long): StudentDto {
        logger.info("Fetching student with id: {}", id)
        val student = studentRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Student not found with id: $id") }
        return student.toDto()
    }

    @Cacheable(value = ["students_detail"], key = "#id")
    fun getStudentDetailById(id: Long): StudentDetailDto {
        logger.info("Fetching student detail with id: {}", id)
        val student = studentRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Student not found with id: $id") }
        return student.toDetailDto()
    }

    fun getAllStudents(page: Int, size: Int, sortBy: String, sortDirection: String): PageResponse<StudentDto> {
        logger.info("Fetching all students - page: {}, size: {}", page, size)

        val direction = if (sortDirection.uppercase() == "DESC") Sort.Direction.DESC else Sort.Direction.ASC
        val pageable = PageRequest.of(page, size, Sort.by(direction, sortBy))

        val studentsPage = studentRepository.findAll(pageable)

        return PageResponse(
            content = studentsPage.content.map { it.toDto() },
            page = studentsPage.number,
            size = studentsPage.size,
            totalElements = studentsPage.totalElements,
            totalPages = studentsPage.totalPages
        )
    }

    fun searchStudents(search: String, page: Int, size: Int): PageResponse<StudentDto> {
        logger.info("Searching students with query: {}", search)

        val pageable = PageRequest.of(page, size)
        val studentsPage = studentRepository.searchStudents(search, pageable)

        return PageResponse(
            content = studentsPage.content.map { it.toDto() },
            page = studentsPage.number,
            size = studentsPage.size,
            totalElements = studentsPage.totalElements,
            totalPages = studentsPage.totalPages
        )
    }

    @CacheEvict(value = ["students", "students_detail"], allEntries = true)
    fun createStudent(request: CreateStudentRequest): StudentDto {
        logger.info("Creating new student with email: {}", request.email)

        if (studentRepository.existsByEmail(request.email)) {
            throw DuplicateResourceException("Student with email ${request.email} already exists")
        }

        if (studentRepository.existsByDocumentNumber(request.documentNumber)) {
            throw DuplicateResourceException("Student with document number ${request.documentNumber} already exists")
        }

        val student = Student(
            firstName = request.firstName,
            lastName = request.lastName,
            email = request.email,
            phone = "", // Deprecated, usar phoneNumber
            phoneNumber = request.phoneNumber,
            documentNumber = request.documentNumber,
            dateOfBirth = request.dateOfBirth,
            address = request.address,
            emergencyContact = request.emergencyContact,
            guardianId = request.guardianId ?: 0, // TODO: Manejar guardianId opcional
            enrollmentDate = LocalDate.now(),
            medicalNotes = request.medicalNotes,
            active = true,
            currentLevel = "BEGINNER", // Default
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        val savedStudent = studentRepository.save(student)
        logger.info("Student created successfully with id: {}", savedStudent.id)

        return savedStudent.toDto()
    }

    @CacheEvict(value = ["students", "students_detail"], key = "#id")
    fun updateStudent(id: Long, request: UpdateStudentRequest): StudentDto {
        logger.info("Updating student with id: {}", id)

        val student = studentRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Student not found with id: $id") }

        // Check email uniqueness if being updated
        if (request.email != null && request.email != student.email) {
            if (studentRepository.existsByEmail(request.email)) {
                throw DuplicateResourceException("Student with email ${request.email} already exists")
            }
        }

        // Check document number uniqueness if being updated
        if (request.documentNumber != null && request.documentNumber != student.documentNumber) {
            if (studentRepository.existsByDocumentNumber(request.documentNumber)) {
                throw DuplicateResourceException("Student with document number ${request.documentNumber} already exists")
            }
        }

        val updatedStudent = student.copy(
            firstName = request.firstName ?: student.firstName,
            lastName = request.lastName ?: student.lastName,
            email = request.email ?: student.email,
            documentNumber = request.documentNumber ?: student.documentNumber,
            dateOfBirth = request.dateOfBirth ?: student.dateOfBirth,
            phoneNumber = request.phoneNumber ?: student.phoneNumber,
            address = request.address ?: student.address,
            emergencyContact = request.emergencyContact ?: student.emergencyContact,
            guardianId = request.guardianId ?: student.guardianId,
            medicalNotes = request.medicalNotes ?: student.medicalNotes,
            active = request.active ?: student.active,
            updatedAt = LocalDateTime.now()
        )

        val savedStudent = studentRepository.save(updatedStudent)
        logger.info("Student updated successfully with id: {}", savedStudent.id)

        return savedStudent.toDto()
    }

    @CacheEvict(value = ["students", "students_detail"], key = "#id")
    fun deleteStudent(id: Long) {
        logger.info("Deleting student with id: {}", id)

        if (!studentRepository.existsById(id)) {
            throw ResourceNotFoundException("Student not found with id: $id")
        }

        studentRepository.deleteById(id)
        logger.info("Student deleted successfully with id: {}", id)
    }

    fun getStudentsByGuardian(guardianId: Long, page: Int, size: Int): PageResponse<StudentDto> {
        logger.info("Fetching students for guardian: {}", guardianId)

        val pageable = PageRequest.of(page, size)
        val studentsPage = studentRepository.findByGuardianId(guardianId, pageable)

        return PageResponse(
            content = studentsPage.content.map { it.toDto() },
            page = studentsPage.number,
            size = studentsPage.size,
            totalElements = studentsPage.totalElements,
            totalPages = studentsPage.totalPages
        )
    }

    /**
     * Convierte Student a StudentDto básico (con curso actual si existe)
     */
    private fun Student.toDto(): StudentDto {
        val currentEnrollment = enrollmentServiceProvider.getCurrentEnrollmentByStudent(this.id!!)

        return StudentDto(
            id = id!!,
            firstName = firstName,
            lastName = lastName,
            email = email,
            documentNumber = documentNumber,
            dateOfBirth = dateOfBirth,
            enrollmentDate = enrollmentDate,
            guardianId = if (guardianId > 0) guardianId else null,
            currentCourseId = currentEnrollment?.courseId,
            currentCourseName = currentEnrollment?.courseName,
            active = active,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    /**
     * Convierte Student a StudentDetailDto (con toda la información + historial)
     */
    private fun Student.toDetailDto(): StudentDetailDto {
        val currentEnrollment = enrollmentServiceProvider.getCurrentEnrollmentByStudent(this.id!!)
        val allEnrollments = enrollmentServiceProvider.getEnrollmentsByStudent(this.id!!)

        return StudentDetailDto(
            id = id!!,
            firstName = firstName,
            lastName = lastName,
            email = email,
            documentNumber = documentNumber,
            dateOfBirth = dateOfBirth,
            address = address,
            phoneNumber = phoneNumber,
            emergencyContact = emergencyContact,
            enrollmentDate = enrollmentDate,
            guardianId = if (guardianId > 0) guardianId else null,
            medicalNotes = medicalNotes,
            currentCourseId = currentEnrollment?.courseId,
            currentCourseName = currentEnrollment?.courseName,
            active = active,
            courseHistory = allEnrollments,  // Ya son EnrollmentSummaryDto
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}

