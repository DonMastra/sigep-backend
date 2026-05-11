package com.sigep.students.application.service

import com.sigep.common.application.dto.PageResponse
import com.sigep.common.application.exception.ForbiddenException
import com.sigep.common.application.exception.ValidationException
import com.sigep.common.domain.exception.ResourceNotFoundException
import com.sigep.common.domain.exception.DuplicateResourceException
import com.sigep.common.application.service.EnrollmentServiceProvider
import com.sigep.security.domain.model.UserRole
import com.sigep.security.domain.repository.UserRepository
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
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@Service
@Transactional
class StudentService(
    private val studentRepository: StudentRepository,
    private val enrollmentServiceProvider: EnrollmentServiceProvider,  // Inyectamos la interfaz, no el repositorio
    private val userRepository: UserRepository
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
            phoneNumber = request.phoneNumber,
            documentNumber = request.documentNumber,
            dateOfBirth = request.dateOfBirth,
            address = request.address,
            emergencyContact = request.emergencyContact,
            guardianId = request.guardianId,
            enrollmentDate = request.enrollmentDate ?: LocalDate.now(),
            medicalNotes = request.medicalNotes,
            active = request.active,
            currentLevel = "BEGINNER", // Default
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        val savedStudent = studentRepository.save(student)
        logger.info("Student created successfully with id: {}", savedStudent.id)

        return savedStudent.toDto()
    }

    @CacheEvict(value = ["students", "students_detail"], allEntries = true)
    fun createStudentForGuardian(guardianUserId: Long, request: GuardianStudentRegistrationRequest): StudentDto {
        logger.info("Guardian {} creating student via self-registration", guardianUserId)

        val guardianUser = userRepository.findById(guardianUserId)
            .orElseThrow { ResourceNotFoundException("Guardian user not found with id: $guardianUserId") }

        if (guardianUser.role != UserRole.GUARDIAN) {
            throw ForbiddenException("Only GUARDIAN users can self-register students")
        }

        val useProfileData = request.useGuardianProfileData

        val firstName = resolveRequiredField("firstName", request.firstName, guardianUser.firstName, useProfileData)
        val lastName = resolveRequiredField("lastName", request.lastName, guardianUser.lastName, useProfileData)
        val email = resolveRequiredField("email", request.email, guardianUser.email, useProfileData)
        val documentNumber = resolveRequiredField("documentNumber", request.documentNumber, guardianUser.documentNumber, useProfileData)
        val address = resolveRequiredField("address", request.address, guardianUser.address, useProfileData)
        val phoneNumber = resolveRequiredField("phoneNumber", request.phoneNumber, guardianUser.phoneNumber, useProfileData)
        val emergencyContact = resolveRequiredField("emergencyContact", request.emergencyContact, guardianUser.emergencyContact, useProfileData)
        val dateOfBirth: LocalDate = (request.dateOfBirth ?: if (useProfileData) guardianUser.dateOfBirth else null)
            ?: throw ValidationException("Field dateOfBirth is required for guardian self-registration")

        if (studentRepository.existsByEmail(email)) {
            throw DuplicateResourceException("Student with email $email already exists")
        }

        if (studentRepository.existsByDocumentNumber(documentNumber)) {
            throw DuplicateResourceException("Student with document number $documentNumber already exists")
        }

        val student = Student(
            firstName = firstName,
            lastName = lastName,
            email = email,
            phoneNumber = phoneNumber,
            documentNumber = documentNumber,
            dateOfBirth = dateOfBirth,
            address = address,
            emergencyContact = emergencyContact,
            guardianId = guardianUserId,
            enrollmentDate = LocalDate.now(),
            medicalNotes = request.medicalNotes,
            active = true,
            currentLevel = "BEGINNER",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        val savedStudent = studentRepository.save(student)
        logger.info("Student self-registered successfully with id: {} for guardian: {}", savedStudent.id, guardianUserId)

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
            enrollmentDate = request.enrollmentDate ?: student.enrollmentDate,
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
     * Obtiene el estado de pagos de un estudiante
     * TODO: Esta es una implementación temporal.
     * Cuando el módulo payments esté implementado, debe integrarse con ese servicio.
     */
    fun getStudentPaymentStatus(studentId: Long): StudentPaymentStatusDto {
        logger.info("Fetching payment status for student: {}", studentId)

        if (!studentRepository.existsById(studentId)) {
            throw ResourceNotFoundException("Student not found with id: $studentId")
        }

        // TODO: Integrar con módulo de payments cuando esté disponible
        // Por ahora retornamos un estado por defecto
        return StudentPaymentStatusDto(
            studentId = studentId,
            status = PaymentStatus.UP_TO_DATE,  // Mock: todos al día
            balance = BigDecimal.ZERO,
            lastPaymentDate = LocalDate.now().minusMonths(1),
            nextDueDate = LocalDate.now().plusMonths(1)
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
            guardianId = guardianId,
            currentCourseId = currentEnrollment?.courseId,
            currentCourseName = currentEnrollment?.courseName,
            active = active,
            phoneNumber = phoneNumber,
            address = address,
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
            guardianId = guardianId,
            medicalNotes = medicalNotes,
            currentCourseId = currentEnrollment?.courseId,
            currentCourseName = currentEnrollment?.courseName,
            active = active,
            courseHistory = allEnrollments,  // Ya son EnrollmentSummaryDto
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun resolveRequiredField(
        fieldName: String,
        requestValue: String?,
        profileValue: String?,
        useProfileData: Boolean
    ): String {
        val resolvedValue = requestValue ?: if (useProfileData) profileValue else null
        if (resolvedValue.isNullOrBlank()) {
            throw ValidationException("Field $fieldName is required for guardian self-registration")
        }
        return resolvedValue
    }
}

