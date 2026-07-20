package com.sigep.staff.application.service

import com.sigep.common.application.dto.PageResponse
import com.sigep.common.application.exception.ResourceNotFoundException
import com.sigep.common.application.exception.BusinessException
import com.sigep.common.application.exception.ValidationException
import com.sigep.courses.domain.model.Course
import com.sigep.courses.domain.repository.CourseRepository
import com.sigep.courses.domain.repository.EnrollmentRepository
import com.sigep.security.domain.model.AccountStatus
import com.sigep.security.domain.model.User
import com.sigep.security.domain.model.UserRole
import com.sigep.security.domain.repository.UserRepository
import com.sigep.staff.application.dto.*
import com.sigep.staff.domain.model.AttendanceStatus
import com.sigep.staff.domain.model.TeachingStaff
import com.sigep.staff.infrastructure.repository.StaffAttendanceRepository
import com.sigep.staff.infrastructure.repository.TeachingStaffRepository
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDate
import java.time.YearMonth

@Service
@Transactional
class TeachingStaffService(
    private val teachingStaffRepository: TeachingStaffRepository,
    private val attendanceRepository: StaffAttendanceRepository,
    private val courseRepository: CourseRepository,
    private val enrollmentRepository: EnrollmentRepository,
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder
) {

    companion object {
        private val log = LoggerFactory.getLogger(TeachingStaffService::class.java)
        private const val MAX_PHOTO_SIZE = 5L * 1024L * 1024L
        private val ALLOWED_PHOTO_TYPES = setOf("image/jpeg", "image/png", "image/webp")
    }

    @Cacheable(value = ["teachingStaff"], key = "#page + '-' + #limit + '-' + #sort + '-' + #order")
    @Transactional(readOnly = true)
    fun getAllTeachingStaff(page: Int, limit: Int, sort: String, order: String): PageResponse<TeachingStaffDto> {
        log.debug("Fetching all teaching staff - page: {}, limit: {}", page, limit)

        val direction = if (order.uppercase() == "DESC") Sort.Direction.DESC else Sort.Direction.ASC
        val pageable = PageRequest.of(page, limit, Sort.by(direction, sort))
        val staffPage = teachingStaffRepository.findByIsActiveTrue(pageable)
        val staffDtos = staffPage.content.map { toDto(it) }

        return PageResponse(
            content = staffDtos,
            page = staffPage.number,
            size = staffPage.size,
            totalElements = staffPage.totalElements,
            totalPages = staffPage.totalPages
        )
    }

    @Cacheable(value = ["teachingStaff"], key = "#id")
    @Transactional(readOnly = true)
    fun getTeachingStaffById(id: Long): TeachingStaffDto {
        log.debug("Fetching teaching staff by id: {}", id)

        val staff = teachingStaffRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Teaching staff not found with id: $id") }

        if (!staff.isActive) {
            throw ResourceNotFoundException("Teaching staff is inactive with id: $id")
        }

        return toDtoWithDetails(staff)
    }

    @Transactional(readOnly = true)
    fun searchTeachingStaff(query: String, page: Int, limit: Int): PageResponse<TeachingStaffDto> {
        log.debug("Searching teaching staff with query: {}", query)

        val pageable = PageRequest.of(page, limit, Sort.by(Sort.Direction.ASC, "lastName"))
        val staffPage = teachingStaffRepository.searchByQuery(query, pageable)
        val staffDtos = staffPage.content.map { toDto(it) }

        return PageResponse(
            content = staffDtos,
            page = staffPage.number,
            size = staffPage.size,
            totalElements = staffPage.totalElements,
            totalPages = staffPage.totalPages
        )
    }

    @Transactional(readOnly = true)
    fun resolveTeacherNames(ids: Collection<Long>): List<TeacherResolutionDto> {
        if (ids.isEmpty()) {
            return emptyList()
        }

        return teachingStaffRepository.findAllByLinkedUserIdInAndIsActiveTrue(ids)
            .map { TeacherResolutionDto(id = it.linkedUserId!!, fullName = it.fullName) }
            .sortedBy { it.id }
    }

    @CacheEvict(value = ["teachingStaff"], allEntries = true)
    fun createTeachingStaff(request: CreateTeachingStaffRequest): TeachingStaffDto {
        log.info("Creating new teaching staff: {} {}", request.firstName, request.lastName)

        teachingStaffRepository.findByEmail(request.email)?.let {
            throw IllegalArgumentException("Email already exists: ${request.email}")
        }

        teachingStaffRepository.findByDocumentNumber(request.documentNumber)?.let {
            throw IllegalArgumentException("Document number already exists: ${request.documentNumber}")
        }

        if (userRepository.existsByUsername(request.username)) {
            throw BusinessException("Username already exists: ${request.username}")
        }
        if (userRepository.existsByEmail(request.email)) {
            throw BusinessException("A user account already exists with email ${request.email}")
        }

        val now = java.time.LocalDateTime.now()
        val user = userRepository.save(
            User(
                username = request.username.trim(),
                email = request.email.trim(),
                password = passwordEncoder.encode(request.initialPassword),
                firstName = request.firstName.trim(),
                lastName = request.lastName.trim(),
                phoneNumber = request.phoneNumber,
                address = request.address,
                dateOfBirth = request.birthDate,
                documentNumber = request.documentNumber,
                emergencyContact = "${request.resolvedEmergencyContactName} / ${request.resolvedEmergencyContactPhone}",
                role = UserRole.TEACHER,
                status = AccountStatus.ACTIVE,
                active = true,
                createdAt = now,
                updatedAt = now
            )
        )

        val staff = TeachingStaff(
            linkedUserId = user.id,
            firstName = request.firstName,
            lastName = request.lastName,
            email = request.email,
            phoneNumber = request.phoneNumber,
            documentNumber = request.documentNumber,
            birthDate = request.birthDate,
            address = request.address,
            hireDate = request.hireDate,
            monthlySalary = request.monthlySalary,
            paymentStatus = request.paymentStatus,
            specialization = request.specialization,
            qualifications = request.qualifications,
            observations = request.observations,
            notes = request.notes,
            emergencyContactName = request.resolvedEmergencyContactName,
            emergencyContactPhone = request.resolvedEmergencyContactPhone
        )

        val savedStaff = teachingStaffRepository.save(staff)
        applyExactCourseAssignments(savedStaff, request.assignedCourseIds, request.confirmCourseReassignments)
        log.info("Teaching staff created successfully with id: {}", savedStaff.id)

        return toDto(savedStaff)
    }

    @CacheEvict(value = ["teachingStaff"], allEntries = true)
    fun updateTeachingStaff(id: Long, request: UpdateTeachingStaffRequest): TeachingStaffDto {
        log.info("Updating teaching staff with id: {}", id)

        val staff = teachingStaffRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Teaching staff not found with id: $id") }

        request.email?.let { newEmail ->
            if (newEmail != staff.email) {
                teachingStaffRepository.findByEmail(newEmail)?.let {
                    throw IllegalArgumentException("Email already exists: $newEmail")
                }
            }
        }

        request.documentNumber?.let { newDocument ->
            if (newDocument != staff.documentNumber && teachingStaffRepository.findByDocumentNumber(newDocument) != null) {
                throw BusinessException("Document number already exists: $newDocument")
            }
        }

        val linkedUserId = request.linkedUserId ?: staff.linkedUserId
        val linkedUser = linkedUserId?.let { validateTeacherAccount(it, staff.id) }

        val updatedStaff = staff.copy(
            firstName = request.firstName ?: staff.firstName,
            lastName = request.lastName ?: staff.lastName,
            email = request.email ?: staff.email,
            phoneNumber = request.phoneNumber ?: staff.phoneNumber,
            documentNumber = request.documentNumber ?: staff.documentNumber,
            birthDate = request.birthDate ?: staff.birthDate,
            hireDate = request.hireDate ?: staff.hireDate,
            address = request.address ?: staff.address,
            linkedUserId = linkedUserId,
            monthlySalary = request.monthlySalary ?: staff.monthlySalary,
            paymentStatus = request.paymentStatus ?: staff.paymentStatus,
            specialization = request.specialization ?: staff.specialization,
            qualifications = request.qualifications ?: staff.qualifications,
            observations = request.observations ?: staff.observations,
            notes = request.notes ?: staff.notes,
            emergencyContactName = request.resolvedEmergencyContactName ?: staff.emergencyContactName,
            emergencyContactPhone = request.resolvedEmergencyContactPhone ?: staff.emergencyContactPhone
        )

        val savedStaff = teachingStaffRepository.save(updatedStaff)
        request.isActive?.let { savedStaff.isActive = it }
        linkedUser?.let { user ->
            userRepository.save(
                user.copy(
                    firstName = savedStaff.firstName,
                    lastName = savedStaff.lastName,
                    phoneNumber = savedStaff.phoneNumber,
                    address = savedStaff.address,
                    dateOfBirth = savedStaff.birthDate,
                    documentNumber = savedStaff.documentNumber,
                    updatedAt = java.time.LocalDateTime.now()
                )
            )
        }
        request.assignedCourseIds?.let {
            applyExactCourseAssignments(savedStaff, it, request.confirmCourseReassignments)
        }
        log.info("Teaching staff updated successfully with id: {}", savedStaff.id)

        return toDto(savedStaff)
    }

    @CacheEvict(value = ["teachingStaff"], allEntries = true)
    fun uploadPhoto(id: Long, photo: MultipartFile): TeachingStaffDto {
        if (photo.isEmpty) throw ValidationException("Photo file is empty")
        if (photo.size > MAX_PHOTO_SIZE) throw ValidationException("Photo size exceeds 5MB limit")
        val contentType = photo.contentType?.lowercase()
        if (contentType !in ALLOWED_PHOTO_TYPES) {
            throw ValidationException("Unsupported photo type. Allowed: JPEG, PNG and WebP")
        }
        val staff = teachingStaffRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Teaching staff not found with id: $id") }
        return toDto(
            teachingStaffRepository.save(
                staff.copy(
                    photoData = photo.bytes,
                    photoContentType = contentType,
                    photoFilename = photo.originalFilename
                )
            )
        )
    }

    @Transactional(readOnly = true)
    fun getPhoto(id: Long): TeachingStaffPhoto {
        val staff = teachingStaffRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Teaching staff not found with id: $id") }
        return TeachingStaffPhoto(
            data = staff.photoData ?: throw ResourceNotFoundException("Teaching staff photo not found for id: $id"),
            contentType = staff.photoContentType ?: "application/octet-stream",
            filename = staff.photoFilename
        )
    }

    @CacheEvict(value = ["teachingStaff"], allEntries = true)
    fun deleteTeachingStaff(id: Long) {
        log.info("Deleting teaching staff with id: {}", id)

        val staff = teachingStaffRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Teaching staff not found with id: $id") }

        staff.isActive = false
        teachingStaffRepository.save(staff)

        log.info("Teaching staff soft-deleted successfully with id: {}", id)
    }

    private fun toDto(staff: TeachingStaff): TeachingStaffDto {
        val linkedUser = staff.linkedUserId?.let { userRepository.findById(it).orElse(null) }
        val courses = staff.linkedUserId?.let { courseRepository.findAllByTeacherId(it) }.orEmpty()
        val activeStudents = courses.sumOf { course ->
            enrollmentRepository.countActiveEnrollmentsByCourse(course.id!!).toInt()
        }
        return TeachingStaffDto(
            id = staff.id!!,
            linkedUserId = staff.linkedUserId,
            username = linkedUser?.username,
            firstName = staff.firstName,
            lastName = staff.lastName,
            fullName = staff.fullName,
            email = staff.email,
            phoneNumber = staff.phoneNumber,
            documentNumber = staff.documentNumber,
            birthDate = staff.birthDate,
            address = staff.address,
            hireDate = staff.hireDate,
            monthlySalary = staff.monthlySalary,
            paymentStatus = staff.paymentStatus,
            status = if (staff.isActive) "ACTIVE" else "INACTIVE",
            assignedStudentsCount = activeStudents,
            assignedCourses = courses.map { it.toAssignmentDto() },
            specialization = staff.specialization,
            qualifications = staff.qualifications,
            observations = staff.observations,
            notes = staff.notes,
            emergencyContactName = staff.emergencyContactName,
            emergencyContactPhone = staff.emergencyContactPhone,
            attendanceStats = AttendanceStatsDto.EMPTY,
            totalWorkingDaysInMonth = 0,
            photoUrl = staff.photoData?.let { "/api/v1/staff/teaching/${staff.id}/photo" },
            createdAt = staff.createdAt,
            updatedAt = staff.updatedAt
        )
    }

    private fun toDtoWithDetails(staff: TeachingStaff): TeachingStaffDto {
        val dto = toDto(staff)

        val now = LocalDate.now()
        val startOfMonth = now.withDayOfMonth(1)
        val endOfMonth = YearMonth.now().atEndOfMonth()

        // Calcular días laborales reales del mes (lunes a viernes)
        val totalWorkingDays = generateSequence(startOfMonth) { it.plusDays(1) }
            .takeWhile { !it.isAfter(endOfMonth) }
            .count { it.dayOfWeek.value in 1..5 }

        val presentDays = attendanceRepository.countTeachingStaffAttendanceByStatus(
            staff.id!!, AttendanceStatus.PRESENT, startOfMonth, endOfMonth
        ).toInt()

        val absentDays = attendanceRepository.countTeachingStaffAttendanceByStatus(
            staff.id, AttendanceStatus.ABSENT, startOfMonth, endOfMonth
        ).toInt()

        val lateDays = attendanceRepository.countTeachingStaffAttendanceByStatus(
            staff.id, AttendanceStatus.LATE, startOfMonth, endOfMonth
        ).toInt()

        val totalDays = presentDays + absentDays + lateDays
        val attendanceRate = if (totalWorkingDays > 0) (presentDays.toDouble() / totalWorkingDays.toDouble()) * 100 else 0.0

        return dto.copy(
            totalWorkingDaysInMonth = totalWorkingDays,
            attendanceStats = AttendanceStatsDto(
                totalDays = totalDays,
                presentDays = presentDays,
                absentDays = absentDays,
                lateDays = lateDays,
                attendanceRate = attendanceRate
            )
        )
    }

    private fun validateTeacherAccount(userId: Long, currentStaffId: Long?): User {
        val user = userRepository.findById(userId)
            .orElseThrow { ResourceNotFoundException("Teacher account not found with id: $userId") }
        if (user.role != UserRole.TEACHER || user.status != AccountStatus.ACTIVE || !user.active) {
            throw BusinessException("Linked account must be an active TEACHER")
        }
        teachingStaffRepository.findByLinkedUserId(userId)?.let { linked ->
            if (linked.id != currentStaffId) throw BusinessException("Teacher account is already linked to another staff record")
        }
        return user
    }

    private fun applyExactCourseAssignments(
        staff: TeachingStaff,
        requestedCourseIds: Collection<Long>,
        confirmReassignments: Boolean
    ) {
        val teacherUserId = staff.linkedUserId
            ?: throw BusinessException("Teaching staff must be linked to an active TEACHER account before assigning courses")
        validateTeacherAccount(teacherUserId, staff.id)

        val targetIds = requestedCourseIds.distinct().toSet()
        val targets = courseRepository.findAllById(targetIds)
        val missing = targetIds - targets.mapNotNull { it.id }.toSet()
        if (missing.isNotEmpty()) throw ResourceNotFoundException("Courses not found: ${missing.joinToString()}")

        val reassignments = targets.filter { it.teacherId != null && it.teacherId != teacherUserId }
        if (reassignments.isNotEmpty() && !confirmReassignments) {
            throw BusinessException(
                "Course reassignment requires confirmation: ${reassignments.joinToString { it.name }}"
            )
        }

        courseRepository.findAllByTeacherId(teacherUserId)
            .filter { it.id !in targetIds }
            .forEach { courseRepository.save(it.copy(teacherId = null, updatedAt = java.time.LocalDateTime.now())) }
        targets.filter { it.teacherId != teacherUserId }
            .forEach { courseRepository.save(it.copy(teacherId = teacherUserId, updatedAt = java.time.LocalDateTime.now())) }
    }

    private fun Course.toAssignmentDto() = CourseAssignmentDto(
        courseId = id!!,
        courseName = name,
        level = level.name,
        enrolledStudents = enrollmentRepository.countActiveEnrollmentsByCourse(id!!).toInt()
    )
}

data class TeachingStaffPhoto(
    val data: ByteArray,
    val contentType: String,
    val filename: String?
)

