package com.sigep.students.application.service

import com.sigep.common.application.dto.PageResponse
import com.sigep.common.application.exception.ForbiddenException
import com.sigep.common.application.exception.ValidationException
import com.sigep.common.domain.exception.ResourceNotFoundException
import com.sigep.common.domain.exception.DuplicateResourceException
import com.sigep.common.domain.exception.BusinessException
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
import org.springframework.web.multipart.MultipartFile
import java.math.BigDecimal
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

@Service
@Transactional
class StudentService(
    private val studentRepository: StudentRepository,
    private val enrollmentServiceProvider: EnrollmentServiceProvider,  // Inyectamos la interfaz, no el repositorio
    private val userRepository: UserRepository
) {

    private val logger = LoggerFactory.getLogger(StudentService::class.java)
    private val maxPhotoSizeBytes = 5L * 1024L * 1024L
    private val allowedPhotoExtensions = setOf("jpg", "jpeg", "png", "webp")
    private val photoBaseDirectory = File(System.getProperty("java.io.tmpdir"), "sigep/student-photos")

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

    fun getStudentsForTeacher(
        teacherUserId: Long,
        page: Int,
        size: Int,
        sortBy: String,
        sortDirection: String
    ): PageResponse<StudentDto> {
        val studentIds = enrollmentServiceProvider.getActiveStudentIdsByTeacher(teacherUserId)
        if (studentIds.isEmpty()) {
            return PageResponse(emptyList(), page, size, 0, 0)
        }

        val direction = if (sortDirection.uppercase() == "DESC") Sort.Direction.DESC else Sort.Direction.ASC
        val pageable = PageRequest.of(page, size, Sort.by(direction, sortBy))
        val studentsPage = studentRepository.findByIdIn(studentIds, pageable)
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

    fun searchStudentsForTeacher(teacherUserId: Long, search: String, page: Int, size: Int): PageResponse<StudentDto> {
        val studentIds = enrollmentServiceProvider.getActiveStudentIdsByTeacher(teacherUserId)
        if (studentIds.isEmpty()) {
            return PageResponse(emptyList(), page, size, 0, 0)
        }

        val pageable = PageRequest.of(page, size)
        val studentsPage = studentRepository.searchStudentsByIds(search, studentIds, pageable)
        return PageResponse(
            content = studentsPage.content.map { it.toDto() },
            page = studentsPage.number,
            size = studentsPage.size,
            totalElements = studentsPage.totalElements,
            totalPages = studentsPage.totalPages
        )
    }

    fun assertCanAccessStudent(studentId: Long, actorUserId: Long?, actorRole: String?) {
        when (actorRole) {
            UserRole.ADMIN.name -> return
            UserRole.TEACHER.name -> {
                if (actorUserId == null || !enrollmentServiceProvider.teacherCanAccessStudent(actorUserId, studentId)) {
                    throw ForbiddenException("Teachers can only access students enrolled in their assigned courses")
                }
            }
            UserRole.GUARDIAN.name -> {
                val student = studentRepository.findById(studentId)
                    .orElseThrow { ResourceNotFoundException("Student not found with id: $studentId") }
                if (actorUserId == null || student.guardianId != actorUserId) {
                    throw ForbiddenException("Guardians can only access their own students")
                }
            }
            else -> throw ForbiddenException("User role cannot access students")
        }
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
            currentLevel = request.currentLevel,
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
            photoUrl = student.photoUrl,
            active = request.active ?: student.active,
            currentLevel = request.currentLevel ?: student.currentLevel,
            updatedAt = LocalDateTime.now()
        )

        val savedStudent = studentRepository.save(updatedStudent)
        logger.info("Student updated successfully with id: {}", savedStudent.id)

        return savedStudent.toDto()
    }

    @CacheEvict(value = ["students", "students_detail"], key = "#id")
    fun uploadStudentPhoto(id: Long, photo: MultipartFile): StudentDto {
        logger.info("Uploading profile photo for student: {}", id)

        if (photo.isEmpty) {
            throw ValidationException("Photo file is empty")
        }

        if (photo.size > maxPhotoSizeBytes) {
            throw ValidationException("Photo size exceeds 5MB limit")
        }

        val extension = extractAllowedExtension(photo.originalFilename)
        val student = studentRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Student not found with id: $id") }

        if (!photoBaseDirectory.exists() && !photoBaseDirectory.mkdirs()) {
            throw BusinessException("Unable to create directory for student photos")
        }

        val photoFile = File(photoBaseDirectory, "student-$id.$extension")
        photo.transferTo(photoFile)

        val photoUrl = "/api/v1/students/$id/photo"
        val savedStudent = studentRepository.save(
            student.copy(
                photoUrl = photoUrl,
                updatedAt = LocalDateTime.now()
            )
        )

        logger.info("Profile photo uploaded successfully for student: {}", id)
        return savedStudent.toDto()
    }

    fun getStudentPhotoFile(id: Long): File {
        val student = studentRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Student not found with id: $id") }

        if (student.photoUrl.isNullOrBlank()) {
            throw ResourceNotFoundException("Student photo not found for student id: $id")
        }

        val photoFile = allowedPhotoExtensions
            .map { File(photoBaseDirectory, "student-$id.$it") }
            .firstOrNull { it.exists() }
            ?: throw ResourceNotFoundException("Student photo file not found for student id: $id")

        return photoFile
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
            currentLevel = currentLevel,
            active = active,
            photoUrl = photoUrl,
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
            currentLevel = currentLevel,
            active = active,
            photoUrl = photoUrl,
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

    private fun extractAllowedExtension(originalFilename: String?): String {
        val extension = originalFilename
            ?.substringAfterLast('.', "")
            ?.lowercase(Locale.getDefault())
            ?.trim()

        if (extension.isNullOrBlank() || extension !in allowedPhotoExtensions) {
            throw ValidationException("Unsupported photo format. Allowed: ${allowedPhotoExtensions.joinToString(", ")}")
        }

        return extension
    }
}

