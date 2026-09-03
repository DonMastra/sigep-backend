package com.sigep.students.application.service

import com.sigep.common.application.dto.PageResponse
import com.sigep.common.application.exception.ForbiddenException
import com.sigep.common.application.exception.ResourceConflictException
import com.sigep.common.application.exception.UnprocessableEntityException
import com.sigep.common.application.exception.ValidationException
import com.sigep.common.domain.exception.ResourceNotFoundException
import com.sigep.common.domain.exception.BusinessException
import com.sigep.common.application.service.EnrollmentServiceProvider
import com.sigep.common.application.service.UserRoleMembershipProvider
import com.sigep.common.application.service.StudentTuitionBenefitInfo
import com.sigep.common.application.service.StudentTuitionBenefitProvider
import com.sigep.security.domain.model.UserRole
import com.sigep.security.domain.model.AccountStatus
import com.sigep.security.domain.repository.UserRepository
import com.sigep.students.application.dto.*
import com.sigep.students.domain.model.Student
import com.sigep.students.domain.model.StudentGuardianLinkAction
import com.sigep.students.domain.model.StudentGuardianLinkEvent
import com.sigep.students.domain.model.StudentGuardianLinkOrigin
import com.sigep.students.domain.model.StudentGuardianRelationship
import com.sigep.students.domain.repository.StudentGuardianLinkEventRepository
import com.sigep.students.domain.repository.StudentGuardianRelationshipRepository
import com.sigep.students.domain.repository.StudentRepository
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
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
    private val userRepository: UserRepository,
    private val guardianLinkEventRepository: StudentGuardianLinkEventRepository,
    private val guardianRelationshipRepository: StudentGuardianRelationshipRepository,
    private val identityNormalizer: StudentIdentityNormalizer,
    private val roleMembershipProviders: List<UserRoleMembershipProvider> = emptyList(),
    private val tuitionBenefitProviders: List<StudentTuitionBenefitProvider> = emptyList()
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

    fun getAllStudents(
        page: Int,
        size: Int,
        sortBy: String,
        sortDirection: String,
        hasAssignedCourse: Boolean? = null
    ): PageResponse<StudentDto> {
        logger.info("Fetching all students - page: {}, size: {}", page, size)

        val pageable = studentPageRequest(page, size, sortBy, sortDirection)

        val studentsPage = findStudentsByCourseAssignment(pageable, hasAssignedCourse)

        return PageResponse(
            content = mapToDtos(studentsPage.content),
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
        sortDirection: String,
        hasAssignedCourse: Boolean? = null
    ): PageResponse<StudentDto> {
        if (hasAssignedCourse == false) {
            return PageResponse(emptyList(), page, size, 0, 0)
        }
        val studentIds = enrollmentServiceProvider.getActiveStudentIdsByTeacher(teacherUserId)
        if (studentIds.isEmpty()) {
            return PageResponse(emptyList(), page, size, 0, 0)
        }

        val pageable = studentPageRequest(page, size, sortBy, sortDirection)
        val studentsPage = studentRepository.findByIdIn(studentIds, pageable)
        return PageResponse(
            content = mapToDtos(studentsPage.content),
            page = studentsPage.number,
            size = studentsPage.size,
            totalElements = studentsPage.totalElements,
            totalPages = studentsPage.totalPages
        )
    }

    fun searchStudents(
        search: String,
        page: Int,
        size: Int,
        sortBy: String,
        sortDirection: String,
        hasAssignedCourse: Boolean? = null
    ): PageResponse<StudentDto> {
        logger.info("Searching students with a supplied query")

        val pageable = studentPageRequest(page, size, sortBy, sortDirection)
        val studentsPage = searchStudentsByCourseAssignment(search, pageable, hasAssignedCourse)

        return PageResponse(
            content = mapToDtos(studentsPage.content),
            page = studentsPage.number,
            size = studentsPage.size,
            totalElements = studentsPage.totalElements,
            totalPages = studentsPage.totalPages
        )
    }

    fun searchStudentsForTeacher(
        teacherUserId: Long,
        search: String,
        page: Int,
        size: Int,
        sortBy: String,
        sortDirection: String,
        hasAssignedCourse: Boolean? = null
    ): PageResponse<StudentDto> {
        if (hasAssignedCourse == false) {
            return PageResponse(emptyList(), page, size, 0, 0)
        }
        val studentIds = enrollmentServiceProvider.getActiveStudentIdsByTeacher(teacherUserId)
        if (studentIds.isEmpty()) {
            return PageResponse(emptyList(), page, size, 0, 0)
        }

        val pageable = studentPageRequest(page, size, sortBy, sortDirection)
        val studentsPage = studentRepository.searchStudentsByIds(search, studentIds, pageable)
        return PageResponse(
            content = mapToDtos(studentsPage.content),
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
                val canAccess = actorUserId != null && (
                    student.guardianId == actorUserId ||
                        guardianRelationshipRepository
                            .existsByStudentIdAndGuardianUserIdAndActiveTrueAndCanViewAcademicTrue(studentId, actorUserId)
                    )
                if (!canAccess) {
                    throw ForbiddenException("Guardians can only access their own students")
                }
            }
            else -> throw ForbiddenException("User role cannot access students")
        }
    }

    private fun studentPageRequest(page: Int, size: Int, sortBy: String, sortDirection: String): PageRequest {
        val sortField = sortBy.takeIf { it in ALLOWED_SORT_FIELDS } ?: "id"
        val direction = if (sortDirection.uppercase(Locale.ROOT) == "DESC") {
            Sort.Direction.DESC
        } else {
            Sort.Direction.ASC
        }
        val orders = buildList {
            val primaryOrder = Sort.Order(direction, sortField)
            add(if (sortField == "id") primaryOrder else primaryOrder.ignoreCase())
            when (sortField) {
                "lastName" -> add(Sort.Order(direction, "firstName").ignoreCase())
                "firstName" -> add(Sort.Order(direction, "lastName").ignoreCase())
            }
            if (sortField != "id") add(Sort.Order.asc("id"))
        }
        return PageRequest.of(page, size, Sort.by(orders))
    }

    private fun findStudentsByCourseAssignment(
        pageable: Pageable,
        hasAssignedCourse: Boolean?
    ): Page<Student> {
        if (hasAssignedCourse == null) return studentRepository.findAll(pageable)

        val activeStudentIds = enrollmentServiceProvider.getActiveStudentIds()
        return when {
            hasAssignedCourse && activeStudentIds.isEmpty() -> Page.empty(pageable)
            hasAssignedCourse -> studentRepository.findByIdIn(activeStudentIds, pageable)
            activeStudentIds.isEmpty() -> studentRepository.findAll(pageable)
            else -> studentRepository.findByIdNotIn(activeStudentIds, pageable)
        }
    }

    private fun searchStudentsByCourseAssignment(
        search: String,
        pageable: Pageable,
        hasAssignedCourse: Boolean?
    ): Page<Student> {
        if (hasAssignedCourse == null) return studentRepository.searchStudents(search, pageable)

        val activeStudentIds = enrollmentServiceProvider.getActiveStudentIds()
        return when {
            hasAssignedCourse && activeStudentIds.isEmpty() -> Page.empty(pageable)
            hasAssignedCourse -> studentRepository.searchStudentsByIds(search, activeStudentIds, pageable)
            activeStudentIds.isEmpty() -> studentRepository.searchStudents(search, pageable)
            else -> studentRepository.searchStudentsExcludingIds(search, activeStudentIds, pageable)
        }
    }

    private companion object {
        val ALLOWED_SORT_FIELDS = setOf("id", "lastName", "firstName", "studentNumber", "email")
    }

    @CacheEvict(value = ["students", "students_detail"], allEntries = true)
    fun createStudent(request: CreateStudentRequest, actorUserId: Long): StudentDto {
        logger.info("Creating new student record")
        val identity = identityNormalizer.normalize(request.documentType, request.documentCountry, request.documentNumber)
        ensureDocumentAvailable(identity)
        val guardianSelection = resolveGuardianSelection(
            request.guardianId,
            request.guardianIds,
            request.primaryGuardianId
        )
        guardianSelection.guardianIds.forEach(::validateAssignableGuardian)

        val student = Student(
            firstName = request.firstName.trim(),
            lastName = request.lastName.trim(),
            email = request.email.trim().lowercase(),
            phoneNumber = request.phoneNumber,
            documentType = identity.type,
            documentCountry = identity.country,
            documentNumber = identity.displayNumber,
            normalizedDocumentNumber = identity.normalizedNumber,
            dateOfBirth = request.dateOfBirth,
            address = request.address,
            emergencyContact = request.emergencyContact,
            guardianId = guardianSelection.primaryGuardianId,
            enrollmentDate = request.enrollmentDate ?: LocalDate.now(),
            medicalNotes = request.medicalNotes,
            active = request.active,
            currentLevel = request.currentLevel,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        val savedStudent = studentRepository.save(student)
        syncGuardianRelationships(
            savedStudent,
            guardianSelection,
            actorUserId,
            StudentGuardianLinkOrigin.ADMIN,
            "Guardians assigned during student creation"
        )
        logger.info("Student created successfully with id: {}", savedStudent.id)

        return savedStudent.toDto()
    }

    @CacheEvict(value = ["students", "students_detail"], allEntries = true)
    fun createStudentForGuardian(guardianUserId: Long, request: GuardianStudentRegistrationRequest): StudentDto {
        logger.info("Guardian {} creating student via self-registration", guardianUserId)

        val guardianUser = userRepository.findById(guardianUserId)
            .orElseThrow { ResourceNotFoundException("Guardian user not found with id: $guardianUserId") }

        if (!hasActiveRole(guardianUser.id!!, guardianUser.role, UserRole.GUARDIAN)) {
            throw ForbiddenException("Only GUARDIAN users can self-register students")
        }

        val useProfileData = request.useGuardianProfileData

        val firstName = resolveRequiredField("firstName", request.firstName, guardianUser.firstName, useProfileData)
        val lastName = resolveRequiredField("lastName", request.lastName, guardianUser.lastName, useProfileData)
        val email = resolveRequiredField("email", request.email, guardianUser.email, useProfileData)
        val documentNumber = request.documentNumber ?: if (useProfileData) guardianUser.documentNumber else null
        val address = resolveRequiredField("address", request.address, guardianUser.address, useProfileData)
        val phoneNumber = resolveRequiredField("phoneNumber", request.phoneNumber, guardianUser.phoneNumber, useProfileData)
        val emergencyContact = resolveRequiredField("emergencyContact", request.emergencyContact, guardianUser.emergencyContact, useProfileData)
        val dateOfBirth: LocalDate = (request.dateOfBirth ?: if (useProfileData) guardianUser.dateOfBirth else null)
            ?: throw ValidationException("Field dateOfBirth is required for guardian self-registration")

        val identity = identityNormalizer.normalize(request.documentType, request.documentCountry, documentNumber)
        val existing = findByIdentity(identity)
        if (existing != null) {
            if (guardianCanViewStudent(existing, guardianUserId)) {
                return existing.toDto()
            }
            throw UnprocessableEntityException(
                message = "The student identity requires administrative verification",
                code = "STUDENT_MATCH_REQUIRES_VERIFICATION"
            )
        }

        val student = Student(
            firstName = firstName,
            lastName = lastName,
            email = email.trim().lowercase(),
            phoneNumber = phoneNumber,
            documentType = identity.type,
            documentCountry = identity.country,
            documentNumber = identity.displayNumber,
            normalizedDocumentNumber = identity.normalizedNumber,
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
        syncGuardianRelationships(
            savedStudent,
            GuardianSelection(setOf(guardianUserId), guardianUserId),
            guardianUserId,
            StudentGuardianLinkOrigin.GUARDIAN,
            "Guardian self-registration"
        )
        logger.info("Student self-registered successfully with id: {} for guardian: {}", savedStudent.id, guardianUserId)

        return savedStudent.toDto()
    }

    @CacheEvict(value = ["students", "students_detail"], key = "#id")
    fun updateStudent(id: Long, request: UpdateStudentRequest, actorUserId: Long): StudentDto {
        logger.info("Updating student with id: {}", id)

        val student = studentRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Student not found with id: $id") }

        val identityChanged = request.documentType != null || request.documentCountry != null || request.documentNumber != null
        val identity = if (identityChanged) {
            identityNormalizer.normalize(
                request.documentType ?: student.documentType,
                request.documentCountry ?: student.documentCountry,
                request.documentNumber ?: student.documentNumber
            ).also { ensureDocumentAvailable(it, excludingStudentId = id) }
        } else null
        val requestedGuardianSelection = when {
            request.guardianIds != null -> resolveGuardianSelection(
                request.guardianId,
                request.guardianIds,
                request.primaryGuardianId
            )
            request.guardianId != null && request.guardianId != student.guardianId ->
                GuardianSelection(setOf(request.guardianId), request.guardianId)
            else -> null
        }
        requestedGuardianSelection?.guardianIds?.forEach(::validateAssignableGuardian)
        val nextGuardianId = if (requestedGuardianSelection != null) {
            requestedGuardianSelection.primaryGuardianId
        } else {
            student.guardianId
        }

        val updatedStudent = student.copy(
            firstName = request.firstName ?: student.firstName,
            lastName = request.lastName ?: student.lastName,
            email = request.email?.trim()?.lowercase() ?: student.email,
            documentType = identity?.type ?: student.documentType,
            documentCountry = identity?.country ?: student.documentCountry,
            documentNumber = if (identityChanged) identity?.displayNumber else student.documentNumber,
            normalizedDocumentNumber = if (identityChanged) identity?.normalizedNumber else student.normalizedDocumentNumber,
            dateOfBirth = request.dateOfBirth ?: student.dateOfBirth,
            enrollmentDate = request.enrollmentDate ?: student.enrollmentDate,
            phoneNumber = request.phoneNumber ?: student.phoneNumber,
            address = request.address ?: student.address,
            emergencyContact = request.emergencyContact ?: student.emergencyContact,
            guardianId = nextGuardianId,
            medicalNotes = request.medicalNotes ?: student.medicalNotes,
            photoUrl = student.photoUrl,
            active = request.active ?: student.active,
            currentLevel = request.currentLevel ?: student.currentLevel,
            updatedAt = LocalDateTime.now()
        )

        var savedStudent = studentRepository.save(updatedStudent)
        if (requestedGuardianSelection != null) {
            savedStudent = syncGuardianRelationships(
                savedStudent,
                requestedGuardianSelection,
                actorUserId,
                StudentGuardianLinkOrigin.ADMIN,
                "Guardians changed from student update"
            )
        }
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
            content = mapToDtos(studentsPage.content),
            page = studentsPage.number,
            size = studentsPage.size,
            totalElements = studentsPage.totalElements,
            totalPages = studentsPage.totalPages
        )
    }

    @Transactional(readOnly = true)
    fun matchIdentity(actorUserId: Long, actorRole: String, request: StudentIdentityMatchRequest): StudentIdentityMatchDto {
        val identity = identityNormalizer.normalize(request.documentType, request.documentCountry, request.documentNumber)
        val matches = findIdentityCandidates(identity, request.firstName, request.lastName, request.dateOfBirth)
        if (matches.isEmpty()) {
            return StudentIdentityMatchDto(StudentIdentityMatchOutcome.NONE)
        }
        if (matches.size > 1) {
            return StudentIdentityMatchDto(StudentIdentityMatchOutcome.VERIFICATION_REQUIRED)
        }

        val student = matches.single()
        if (actorRole == UserRole.ADMIN.name) {
            return StudentIdentityMatchDto(
                outcome = if (activeGuardianRelationships(student.id!!).isEmpty()) {
                    StudentIdentityMatchOutcome.UNASSIGNED
                } else {
                    StudentIdentityMatchOutcome.OWNED
                },
                studentId = student.id,
                displayName = "${student.firstName} ${student.lastName}"
            )
        }
        if (actorRole == UserRole.GUARDIAN.name && guardianCanViewStudent(student, actorUserId)) {
            return StudentIdentityMatchDto(
                outcome = StudentIdentityMatchOutcome.OWNED,
                studentId = student.id,
                displayName = "${student.firstName} ${student.lastName}"
            )
        }
        return StudentIdentityMatchDto(StudentIdentityMatchOutcome.VERIFICATION_REQUIRED)
    }

    @CacheEvict(value = ["students", "students_detail"], key = "#studentId")
    fun linkGuardian(studentId: Long, guardianId: Long, actorUserId: Long, reason: String): StudentDto {
        validateAssignableGuardian(guardianId)
        val student = studentRepository.findById(studentId)
            .orElseThrow { ResourceNotFoundException("Student not found with id: $studentId") }
        val currentIds = activeGuardianRelationships(studentId).mapTo(linkedSetOf()) { it.guardianUserId }
        if (student.guardianId == guardianId && currentIds == setOf(guardianId)) {
            return student.toDto()
        }
        val saved = syncGuardianRelationships(
            student,
            GuardianSelection(setOf(guardianId), guardianId),
            actorUserId,
            StudentGuardianLinkOrigin.ADMIN,
            reason.trim()
        )
        return saved.toDto()
    }

    @CacheEvict(value = ["students", "students_detail"], key = "#studentId")
    fun replaceGuardians(
        studentId: Long,
        guardianIds: Set<Long>,
        primaryGuardianId: Long?,
        actorUserId: Long,
        reason: String
    ): StudentDto {
        val student = studentRepository.findById(studentId)
            .orElseThrow { ResourceNotFoundException("Student not found with id: $studentId") }
        val selection = resolveGuardianSelection(null, guardianIds, primaryGuardianId)
        selection.guardianIds.forEach(::validateAssignableGuardian)
        return syncGuardianRelationships(
            student,
            selection,
            actorUserId,
            StudentGuardianLinkOrigin.ADMIN,
            reason.trim()
        ).toDto()
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
    private fun mapToDtos(students: List<Student>): List<StudentDto> {
        val guardiansByStudentId = loadGuardianDtosByStudentIds(students.mapNotNull { it.id })
        val benefitsByStudentId = loadTuitionBenefitDtosByStudentIds(students.mapNotNull { it.id })
        return students.map { student ->
            student.toDto(
                guardiansByStudentId[student.id].orEmpty(),
                benefitsByStudentId[student.id].orEmpty()
            )
        }
    }

    private fun Student.toDto(
        guardianDtos: List<StudentGuardianDto> = loadGuardianDtos(this.id!!),
        tuitionBenefits: List<StudentTuitionBenefitDto> = loadTuitionBenefitDtos(this.id!!)
    ): StudentDto {
        val studentId = this.id!!
        val currentEnrollments = enrollmentServiceProvider.getEnrollmentsByStudentAndStatus(studentId, "ACTIVE")
        val currentEnrollment = currentEnrollments.firstOrNull()

        return StudentDto(
            id = studentId,
            studentNumber = studentNumber,
            firstName = firstName,
            lastName = lastName,
            email = email,
            documentType = documentType,
            documentCountry = documentCountry,
            documentNumber = documentNumber,
            dateOfBirth = dateOfBirth,
            enrollmentDate = enrollmentDate,
            guardianId = guardianId,
            guardianIds = guardianDtos.map { it.guardianId },
            guardians = guardianDtos,
            currentCourseId = currentEnrollment?.courseId,
            currentCourseName = currentEnrollment?.courseName,
            currentCourses = currentEnrollments,
            currentLevel = currentLevel,
            active = active,
            photoUrl = photoUrl,
            phoneNumber = phoneNumber,
            address = address,
            createdAt = createdAt,
            updatedAt = updatedAt,
            tuitionBenefits = tuitionBenefits
        )
    }

    /**
     * Convierte Student a StudentDetailDto (con toda la información + historial)
     */
    private fun Student.toDetailDto(): StudentDetailDto {
        val studentId = this.id!!
        val currentEnrollments = enrollmentServiceProvider.getEnrollmentsByStudentAndStatus(studentId, "ACTIVE")
        val currentEnrollment = currentEnrollments.firstOrNull()
        val allEnrollments = enrollmentServiceProvider.getEnrollmentsByStudent(studentId)
        val guardianDtos = loadGuardianDtos(studentId)
        val tuitionBenefits = loadTuitionBenefitDtos(studentId)

        return StudentDetailDto(
            id = studentId,
            studentNumber = studentNumber,
            firstName = firstName,
            lastName = lastName,
            email = email,
            documentType = documentType,
            documentCountry = documentCountry,
            documentNumber = documentNumber,
            dateOfBirth = dateOfBirth,
            address = address,
            phoneNumber = phoneNumber,
            emergencyContact = emergencyContact,
            enrollmentDate = enrollmentDate,
            guardianId = guardianId,
            guardianIds = guardianDtos.map { it.guardianId },
            guardians = guardianDtos,
            medicalNotes = medicalNotes,
            currentCourseId = currentEnrollment?.courseId,
            currentCourseName = currentEnrollment?.courseName,
            currentCourses = currentEnrollments,
            currentLevel = currentLevel,
            active = active,
            photoUrl = photoUrl,
            courseHistory = allEnrollments,  // Ya son EnrollmentSummaryDto
            createdAt = createdAt,
            updatedAt = updatedAt,
            tuitionBenefits = tuitionBenefits
        )
    }

    private fun loadTuitionBenefitDtos(studentId: Long): List<StudentTuitionBenefitDto> =
        loadTuitionBenefitDtosByStudentIds(listOf(studentId))[studentId].orEmpty()

    private fun loadTuitionBenefitDtosByStudentIds(
        studentIds: Collection<Long>
    ): Map<Long, List<StudentTuitionBenefitDto>> {
        if (studentIds.isEmpty()) return emptyMap()
        val provider = tuitionBenefitProviders.firstOrNull() ?: return emptyMap()
        return provider.getBenefitsByStudentIds(studentIds).mapValues { (_, benefits) ->
            benefits.map { it.toDto() }
        }
    }

    private fun StudentTuitionBenefitInfo.toDto() = StudentTuitionBenefitDto(
        id = id,
        type = type,
        percentage = percentage,
        amount = amount,
        validFrom = validFrom,
        validTo = validTo,
        reason = reason,
        active = active
    )

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

    private fun validateAssignableGuardian(guardianId: Long) {
        val guardian = userRepository.findById(guardianId)
            .orElseThrow { ResourceNotFoundException("Guardian user not found with id: $guardianId") }
        if (
            !hasActiveRole(guardian.id!!, guardian.role, UserRole.GUARDIAN) ||
            guardian.status == AccountStatus.REJECTED
        ) {
            throw ValidationException(
                message = "Guardian must be an assignable GUARDIAN account",
                code = "GUARDIAN_NOT_ASSIGNABLE",
                field = "guardianId"
            )
        }
    }

    private fun resolveGuardianSelection(
        legacyGuardianId: Long?,
        guardianIds: Set<Long>?,
        requestedPrimaryGuardianId: Long?
    ): GuardianSelection {
        val normalizedGuardianIds = (guardianIds ?: setOfNotNull(legacyGuardianId))
            .filter { it > 0 }
            .toCollection(linkedSetOf())
        val primaryGuardianId = requestedPrimaryGuardianId
            ?: legacyGuardianId?.takeIf(normalizedGuardianIds::contains)
            ?: normalizedGuardianIds.singleOrNull()

        if (primaryGuardianId != null && primaryGuardianId !in normalizedGuardianIds) {
            throw ValidationException(
                message = "Primary guardian must be included in guardianIds",
                code = "PRIMARY_GUARDIAN_NOT_SELECTED",
                field = "primaryGuardianId"
            )
        }
        return GuardianSelection(normalizedGuardianIds, primaryGuardianId)
    }

    private fun syncGuardianRelationships(
        student: Student,
        selection: GuardianSelection,
        actorUserId: Long,
        origin: StudentGuardianLinkOrigin,
        reason: String
    ): Student {
        val studentId = student.id!!
        val now = LocalDateTime.now()
        val before = guardianRelationshipRepository.findByStudentId(studentId)
        val beforeActiveIds = before.filter { it.active }.mapTo(linkedSetOf()) { it.guardianUserId }
        val beforePrimaryId = before.firstOrNull { it.active && it.primary }?.guardianUserId ?: student.guardianId

        if (before.isNotEmpty()) {
            guardianRelationshipRepository.saveAll(
                before.map { relationship ->
                    relationship.copy(
                        primary = false,
                        active = relationship.guardianUserId in selection.guardianIds,
                        updatedAt = now
                    )
                }
            )
            guardianRelationshipRepository.flush()
        }

        val currentByGuardianId = guardianRelationshipRepository.findByStudentId(studentId)
            .associateBy { it.guardianUserId }
        val activeRelationships = selection.guardianIds.map { guardianId ->
            currentByGuardianId[guardianId]?.copy(
                primary = guardianId == selection.primaryGuardianId,
                canViewAcademic = true,
                active = true,
                verifiedBy = actorUserId,
                verifiedAt = now,
                updatedAt = now
            ) ?: StudentGuardianRelationship(
                studentId = studentId,
                guardianUserId = guardianId,
                primary = guardianId == selection.primaryGuardianId,
                canViewAcademic = true,
                active = true,
                sourceSystem = "APPLICATION",
                sourceReference = reason.take(255),
                verifiedBy = actorUserId,
                verifiedAt = now,
                createdAt = now,
                updatedAt = now
            )
        }
        if (activeRelationships.isNotEmpty()) {
            guardianRelationshipRepository.saveAll(activeRelationships)
            guardianRelationshipRepository.flush()
        }

        val linkedStudent = if (student.guardianId != selection.primaryGuardianId) {
            studentRepository.save(student.copy(guardianId = selection.primaryGuardianId, updatedAt = now))
        } else {
            student
        }

        val removedIds = beforeActiveIds - selection.guardianIds
        val addedIds = selection.guardianIds - beforeActiveIds
        removedIds.forEach { guardianId ->
            recordGuardianChange(studentId, guardianId, null, actorUserId, origin, reason)
        }
        addedIds.forEach { guardianId ->
            recordGuardianChange(studentId, null, guardianId, actorUserId, origin, reason)
        }
        if (removedIds.isEmpty() && addedIds.isEmpty() && beforePrimaryId != selection.primaryGuardianId) {
            recordGuardianChange(
                studentId,
                beforePrimaryId,
                selection.primaryGuardianId,
                actorUserId,
                origin,
                reason
            )
        }
        return linkedStudent
    }

    private fun activeGuardianRelationships(studentId: Long): List<StudentGuardianRelationship> =
        guardianRelationshipRepository.findByStudentIdAndActiveTrueOrderByPrimaryDescIdAsc(studentId)

    private fun guardianCanViewStudent(student: Student, guardianUserId: Long): Boolean =
        student.guardianId == guardianUserId ||
            guardianRelationshipRepository.existsByStudentIdAndGuardianUserIdAndActiveTrueAndCanViewAcademicTrue(
                student.id!!,
                guardianUserId
            )

    private fun loadGuardianDtos(studentId: Long): List<StudentGuardianDto> {
        return loadGuardianDtosByStudentIds(listOf(studentId))[studentId].orEmpty()
    }

    private fun loadGuardianDtosByStudentIds(studentIds: Collection<Long>): Map<Long, List<StudentGuardianDto>> {
        if (studentIds.isEmpty()) return emptyMap()
        val relationships = guardianRelationshipRepository.findByStudentIdInAndActiveTrue(studentIds)
            .sortedWith(
                compareByDescending<StudentGuardianRelationship> { it.primary }
                    .thenBy { it.id ?: Long.MAX_VALUE }
            )
        if (relationships.isEmpty()) return emptyMap()
        val usersById = userRepository.findAllById(relationships.map { it.guardianUserId }.distinct())
            .associateBy { it.id!! }
        return relationships.mapNotNull { relationship ->
            usersById[relationship.guardianUserId]?.let { guardian ->
                relationship.studentId to StudentGuardianDto(
                    guardianId = relationship.guardianUserId,
                    firstName = guardian.firstName,
                    lastName = guardian.lastName,
                    email = guardian.email,
                    relationshipType = relationship.relationshipType,
                    primary = relationship.primary,
                    canViewAcademic = relationship.canViewAcademic,
                    billingContact = relationship.billingContact,
                    active = relationship.active,
                    accountStatus = guardian.status
                )
            }
        }.groupBy({ it.first }, { it.second })
    }

    private data class GuardianSelection(
        val guardianIds: Set<Long>,
        val primaryGuardianId: Long?
    )

    private fun ensureDocumentAvailable(identity: NormalizedStudentDocument, excludingStudentId: Long? = null) {
        val normalized = identity.normalizedNumber ?: return
        val existing = studentRepository.findByDocumentTypeAndDocumentCountryAndNormalizedDocumentNumber(
            identity.type,
            identity.country,
            normalized
        ).orElse(null)
        if (existing != null && existing.id != excludingStudentId) {
            throw ResourceConflictException(
                message = "A student with the same normalized identity already exists",
                code = "STUDENT_IDENTITY_CONFLICT",
                field = "documentNumber"
            )
        }
    }

    private fun hasActiveRole(userId: Long, legacyRole: UserRole, requiredRole: UserRole): Boolean =
        roleMembershipProviders.firstOrNull()?.hasActiveRole(userId, requiredRole.name)
            ?: (legacyRole == requiredRole)

    private fun findByIdentity(identity: NormalizedStudentDocument): Student? {
        val normalized = identity.normalizedNumber ?: return null
        return studentRepository.findByDocumentTypeAndDocumentCountryAndNormalizedDocumentNumber(
            identity.type,
            identity.country,
            normalized
        ).orElse(null)
    }

    private fun findIdentityCandidates(
        identity: NormalizedStudentDocument,
        firstName: String?,
        lastName: String?,
        dateOfBirth: LocalDate?
    ): List<Student> {
        findByIdentity(identity)?.let { return listOf(it) }
        if (firstName.isNullOrBlank() || lastName.isNullOrBlank() || dateOfBirth == null) {
            return emptyList()
        }
        return studentRepository.findPotentialMatches(firstName.trim(), lastName.trim(), dateOfBirth)
    }

    private fun recordGuardianChange(
        studentId: Long,
        previousGuardianId: Long?,
        guardianId: Long?,
        actorUserId: Long,
        origin: StudentGuardianLinkOrigin,
        reason: String
    ) {
        val action = when {
            previousGuardianId == null && guardianId != null -> StudentGuardianLinkAction.LINKED
            previousGuardianId != null && guardianId == null -> StudentGuardianLinkAction.UNLINKED
            else -> StudentGuardianLinkAction.REASSIGNED
        }
        guardianLinkEventRepository.save(
            StudentGuardianLinkEvent(
                studentId = studentId,
                previousGuardianUserId = previousGuardianId,
                guardianUserId = guardianId,
                action = action,
                origin = origin,
                actorUserId = actorUserId,
                reason = reason
            )
        )
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

