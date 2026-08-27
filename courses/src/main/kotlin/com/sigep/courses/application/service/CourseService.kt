package com.sigep.courses.application.service

import com.sigep.common.application.exception.ForbiddenException
import com.sigep.common.application.exception.ResourceNotFoundException
import com.sigep.common.application.exception.BusinessException
import com.sigep.common.application.dto.PageResponse
import com.sigep.common.application.service.ReservationAssignmentProvider
import com.sigep.common.application.service.ReservationInfoProvider
import com.sigep.common.application.service.StudentProfileProvider
import com.sigep.common.application.service.TeacherInfoProvider
import com.sigep.courses.application.dto.*
import com.sigep.courses.application.event.CourseEventPublisher
import com.sigep.courses.domain.event.CoursePublishedEvent
import com.sigep.courses.domain.model.*
import com.sigep.courses.domain.repository.CourseRepository
import com.sigep.courses.domain.repository.EnrollmentRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.cache.annotation.CacheEvict
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@Transactional
class CourseService(
    private val courseRepository: CourseRepository,
    private val enrollmentRepository: EnrollmentRepository,
    private val eventPublisher: CourseEventPublisher,
    private val teacherInfoProvider: TeacherInfoProvider,
    private val reservationInfoProvider: ReservationInfoProvider,
    private val reservationAssignmentProviderProvider: ObjectProvider<ReservationAssignmentProvider>,
    private val studentProfileProviderProvider: ObjectProvider<StudentProfileProvider>
) {

    private val logger = LoggerFactory.getLogger(CourseService::class.java)

    fun getCourseById(id: Long, actorUserId: Long?, actorRole: String?): CourseDto {
        logger.info("Fetching course with id: {}", id)
        val course = courseRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Course not found with id: $id") }
        validateCourseReadAccess(course, actorUserId, actorRole)
        return course.toDto()
    }

    fun getAllCourses(
        page: Int,
        size: Int,
        sortBy: String,
        sortDirection: String,
        actorUserId: Long?,
        actorRole: String?
    ): PageResponse<CourseDto> {
        logger.info("Fetching all courses - page: {}, size: {}", page, size)
        val direction = if (sortDirection.uppercase() == "DESC") Sort.Direction.DESC else Sort.Direction.ASC
        val pageable = PageRequest.of(page, size, Sort.by(direction, sortBy))
        val coursesPage = when (actorRole) {
            "TEACHER" -> courseRepository.findByTeacherId(requireActorUserId(actorUserId), pageable)
            "GUARDIAN" -> courseRepository.findByIsPublishedTrue(pageable)
            else -> courseRepository.findAll(pageable)
        }
        val teacherNameCache = mutableMapOf<Long, String?>()
        return PageResponse(
            content = coursesPage.content.map { it.toDto(teacherNameCache) },
            page = coursesPage.number,
            size = coursesPage.size,
            totalElements = coursesPage.totalElements,
            totalPages = coursesPage.totalPages
        )
    }

    fun searchCourses(search: String, page: Int, size: Int, actorUserId: Long?, actorRole: String?): PageResponse<CourseDto> {
        logger.info("Searching courses with query: {}", search)
        val pageable = PageRequest.of(page, size)
        val coursesPage = when (actorRole) {
            "TEACHER" -> courseRepository.searchCoursesForTeacher(search, requireActorUserId(actorUserId), pageable)
            "GUARDIAN" -> courseRepository.searchPublishedCourses(search, pageable)
            else -> courseRepository.searchCourses(search, pageable)
        }
        val teacherNameCache = mutableMapOf<Long, String?>()
        return PageResponse(
            content = coursesPage.content.map { it.toDto(teacherNameCache) },
            page = coursesPage.number,
            size = coursesPage.size,
            totalElements = coursesPage.totalElements,
            totalPages = coursesPage.totalPages
        )
    }

    fun getCoursesByTeacher(
        teacherId: Long,
        page: Int,
        size: Int,
        actorUserId: Long?,
        actorRole: String?
    ): PageResponse<CourseDto> {
        if (actorRole == "TEACHER" && actorUserId != teacherId) {
            throw ForbiddenException("Teachers can only access their assigned courses")
        }
        logger.info("Fetching courses for teacher: {}", teacherId)
        val pageable = PageRequest.of(page, size)
        val coursesPage = if (actorRole == "GUARDIAN") {
            courseRepository.findByTeacherIdAndIsPublishedTrue(teacherId, pageable)
        } else {
            courseRepository.findByTeacherId(teacherId, pageable)
        }
        val teacherNameCache = mutableMapOf<Long, String?>()
        return PageResponse(
            content = coursesPage.content.map { it.toDto(teacherNameCache) },
            page = coursesPage.number,
            size = coursesPage.size,
            totalElements = coursesPage.totalElements,
            totalPages = coursesPage.totalPages
        )
    }

    @CacheEvict(value = ["courses"], allEntries = true)
    fun createCourse(request: CreateCourseRequest): CourseDto {
        logger.info("Creating new course: {}", request.name)

        if (courseRepository.existsByCodeIgnoreCase(request.code)) {
            throw BusinessException("Course code '${request.code}' already exists")
        }
        if (request.minStudents > request.maxStudents) {
            throw BusinessException("Minimum students cannot be greater than maximum students")
        }
        if (request.startDate != null && request.endDate != null && request.endDate.isBefore(request.startDate)) {
            throw BusinessException("End date cannot be before start date")
        }
        validateAssignableTeacher(request.teacherId)

        val course = Course(
            code = request.code,
            name = request.name,
            description = request.description,
            level = request.level,
            duration = request.duration,
            maxStudents = request.maxStudents,
            minStudents = request.minStudents,
            teacherId = request.teacherId,
            price = request.price,
            startDate = request.startDate,
            endDate = request.endDate,
            status = request.status ?: CourseStatus.INACTIVE,
            isPublished = false, // Always false at creation; use PATCH /publish once a reservation is assigned
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        val savedCourse = courseRepository.save(course)

        val reservationIds = normalizeReservationIds(
            request.reservationIds + listOfNotNull(request.reservationId)
        )
        if (reservationIds.isNotEmpty()) {
            reservationAssignmentProvider().syncCourseReservations(savedCourse.id!!, reservationIds)
        }

        logger.info("Course created successfully with id: {}", savedCourse.id)
        return savedCourse.toDto()
    }

    @CacheEvict(value = ["courses"], key = "#id")
    fun updateCourse(id: Long, request: UpdateCourseRequest): CourseDto {
        logger.info("Updating course with id: {}", id)
        val course = courseRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Course not found with id: $id") }

        if (request.code != null && !request.code.equals(course.code, ignoreCase = true) && courseRepository.existsByCodeIgnoreCase(request.code)) {
            throw BusinessException("Course code '${request.code}' already exists")
        }

        val newMinStudents = request.minStudents ?: course.minStudents
        val newMaxStudents = request.maxStudents ?: course.maxStudents
        if (newMinStudents > newMaxStudents) {
            throw BusinessException("Minimum students cannot be greater than maximum students")
        }

        val newStartDate = request.startDate ?: course.startDate
        val newEndDate = request.endDate ?: course.endDate
        if (newStartDate != null && newEndDate != null && newEndDate.isBefore(newStartDate)) {
            throw BusinessException("End date cannot be before start date")
        }

        validateAssignableTeacher(request.teacherId)

        val updatedCourse = course.copy(
            code = request.code ?: course.code,
            name = request.name ?: course.name,
            description = request.description ?: course.description,
            level = request.level ?: course.level,
            duration = request.duration ?: course.duration,
            maxStudents = newMaxStudents,
            minStudents = newMinStudents,
            teacherId = request.teacherId,
            price = request.price ?: course.price,
            startDate = newStartDate,
            endDate = newEndDate,
            status = request.status ?: course.status,
            isPublished = request.isPublished ?: course.isPublished,
            updatedAt = LocalDateTime.now()
        )

        val savedCourse = courseRepository.save(updatedCourse)
        request.reservationIds?.let { requestedIds ->
            reservationAssignmentProvider().syncCourseReservations(
                savedCourse.id!!,
                normalizeReservationIds(requestedIds)
            )
        }
        logger.info("Course updated successfully with id: {}", savedCourse.id)
        return savedCourse.toDto()
    }

    @CacheEvict(value = ["courses"], key = "#id")
    fun deleteCourse(id: Long) {
        logger.info("Deleting course with id: {}", id)
        if (!courseRepository.existsById(id)) {
            throw ResourceNotFoundException("Course not found with id: $id")
        }
        courseRepository.deleteById(id)
        logger.info("Course deleted successfully with id: {}", id)
    }

    @CacheEvict(value = ["courses"], key = "#courseId")
    fun enrollStudent(courseId: Long, request: EnrollStudentRequest, actorUserId: Long?, actorRole: String?): EnrollmentDto {
        logger.info("Enrolling student {} in course {}", request.studentId, courseId)
        val course = courseRepository.findById(courseId)
            .orElseThrow { ResourceNotFoundException("Course not found with id: $courseId") }

        if (actorRole == "TEACHER" && actorUserId != course.teacherId) {
            throw ForbiddenException("Teachers can only enroll students in their assigned courses")
        }

        val existingEnrollment = enrollmentRepository.findByStudentIdAndCourseId(request.studentId, courseId)
        if (existingEnrollment.isPresent && existingEnrollment.get().status == EnrollmentStatus.ACTIVE) {
            throw BusinessException("Student is already enrolled in this course")
        }

        val activeEnrollments = enrollmentRepository.countActiveEnrollmentsByCourse(courseId)
        if (activeEnrollments >= course.maxStudents) {
            throw BusinessException("Course is full. Maximum students: ${course.maxStudents}")
        }

        val enrollment = Enrollment(
            studentId = request.studentId,
            course = course,
            status = EnrollmentStatus.ACTIVE,
            notes = request.notes,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        val savedEnrollment = enrollmentRepository.save(enrollment)
        logger.info("Student enrolled successfully")
        return savedEnrollment.toDto()
    }

    fun filterCourses(
        filter: CourseFilterRequest,
        page: Int,
        size: Int,
        actorUserId: Long?,
        actorRole: String?
    ): PageResponse<CourseDto> {
        logger.info("Filtering courses with filter: {}", filter)
        val pageable = PageRequest.of(page, size)
        val coursesPage = courseRepository.filterCourses(
            level = filter.level,
            status = filter.status,
            teacherId = if (actorRole == "TEACHER") requireActorUserId(actorUserId) else filter.teacherId,
            isPublished = if (actorRole == "GUARDIAN") true else filter.isPublished,
            minPrice = filter.minPrice,
            maxPrice = filter.maxPrice,
            pageable = pageable
        )

        var courses = coursesPage.content
        if (filter.hasAvailableSeats == true) {
            courses = courses.filter { course ->
                val enrolled = enrollmentRepository.countActiveEnrollmentsByCourse(course.id!!).toInt()
                course.maxStudents > enrolled
            }
        }

        val teacherNameCache = mutableMapOf<Long, String?>()
        return PageResponse(
            content = courses.map { it.toDto(teacherNameCache) },
            page = coursesPage.number,
            size = coursesPage.size,
            totalElements = coursesPage.totalElements,
            totalPages = coursesPage.totalPages
        )
    }

    fun getPublishedCourses(page: Int, size: Int): PageResponse<CourseSimpleDto> {
        logger.info("Fetching published courses")
        val pageable = PageRequest.of(page, size)
        val coursesPage = courseRepository.findByIsPublishedTrue(pageable)
        return PageResponse(
            content = coursesPage.content.map { it.toSimpleDto() },
            page = coursesPage.number,
            size = coursesPage.size,
            totalElements = coursesPage.totalElements,
            totalPages = coursesPage.totalPages
        )
    }

    fun getCourseStatistics(): CourseStatisticsDto {
        logger.info("Calculating course statistics")
        val totalCourses = courseRepository.count()
        val activeCourses = courseRepository.countByStatus(CourseStatus.ACTIVE)
        val publishedCourses = courseRepository.countByIsPublishedTrue()
        val totalEnrollments = enrollmentRepository.countByStatus(EnrollmentStatus.ACTIVE)

        val allCourses = courseRepository.findAll()
        val totalCapacity = allCourses.sumOf { it.maxStudents }
        val totalEnrolled = allCourses.sumOf { course ->
            enrollmentRepository.countActiveEnrollmentsByCourse(course.id!!).toInt()
        }
        val averageEnrollmentRate = if (totalCapacity > 0) {
            (totalEnrolled.toDouble() / totalCapacity.toDouble()) * 100
        } else 0.0

        val coursesByLevel = CourseLevel.values().associateWith { level ->
            courseRepository.countByLevel(level)
        }
        val coursesByStatus = CourseStatus.values().associateWith { status ->
            courseRepository.countByStatus(status)
        }

        return CourseStatisticsDto(
            totalCourses = totalCourses,
            activeCourses = activeCourses,
            publishedCourses = publishedCourses,
            totalEnrollments = totalEnrollments,
            averageEnrollmentRate = averageEnrollmentRate,
            coursesByLevel = coursesByLevel,
            coursesByStatus = coursesByStatus
        )
    }

    @CacheEvict(value = ["courses"], key = "#id")
    fun publishCourse(id: Long): CourseDto {
        logger.info("Publishing course with id: {}", id)
        val course = courseRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Course not found with id: $id") }

        if (!reservationInfoProvider.hasReservationAssigned(id)) {
            throw BusinessException(
                message = "No se puede publicar el curso sin al menos una reserva de horario asignada",
                code = "BUSINESS_RULE_VIOLATION",
                field = "reservation",
                details = "A confirmed schedule reservation (classroom + time slot) is required before publishing"
            )
        }

        if (course.teacherId == null || teacherInfoProvider.getTeacherNameById(course.teacherId) == null) {
            throw BusinessException(
                message = "No se puede publicar el curso sin un docente asignado",
                code = "BUSINESS_RULE_VIOLATION",
                field = "teacherId",
                details = "Assign an active TEACHER account before publishing"
            )
        }
        if (course.status == CourseStatus.CANCELLED || course.status == CourseStatus.COMPLETED) {
            throw BusinessException("Only active or inactive courses can be published")
        }

        val updatedCourse = course.copy(isPublished = true, updatedAt = LocalDateTime.now())
        val savedCourse = courseRepository.save(updatedCourse)
        logger.info("Course published successfully with id: {}", savedCourse.id)

        eventPublisher.publishCoursePublished(
            CoursePublishedEvent(
                courseId = savedCourse.id!!,
                courseCode = savedCourse.code,
                courseName = savedCourse.name,
                level = savedCourse.level.name,
                startDate = savedCourse.startDate
            )
        )
        return savedCourse.toDto()
    }

    @CacheEvict(value = ["courses"], key = "#id")
    fun unpublishCourse(id: Long): CourseDto {
        logger.info("Unpublishing course with id: {}", id)
        val course = courseRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Course not found with id: $id") }
        val updatedCourse = course.copy(isPublished = false, updatedAt = LocalDateTime.now())
        return courseRepository.save(updatedCourse).toDto()
    }

    @CacheEvict(value = ["courses"], key = "#id")
    fun activateCourse(id: Long): CourseDto {
        logger.info("Activating course with id: {}", id)
        val course = courseRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Course not found with id: $id") }

        if (!reservationInfoProvider.hasReservationAssigned(id)) {
            throw BusinessException(
                message = "No se puede habilitar inscripción sin una reserva de horario asignada",
                code = "COURSE_NOT_READY_FOR_ENROLLMENT",
                field = "reservation",
                details = "Course must have an assigned classroom reservation before enrollment can be opened"
            )
        }

        val updatedCourse = course.copy(status = CourseStatus.ACTIVE, updatedAt = LocalDateTime.now())
        return courseRepository.save(updatedCourse).toDto()
    }

    @CacheEvict(value = ["courses"], key = "#id")
    fun deactivateCourse(id: Long): CourseDto {
        logger.info("Deactivating course with id: {}", id)
        val course = courseRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Course not found with id: $id") }
        val updatedCourse = course.copy(status = CourseStatus.INACTIVE, updatedAt = LocalDateTime.now())
        return courseRepository.save(updatedCourse).toDto()
    }

    // ── Private mapping helpers ────────────────────────────────────────────────

    private fun Course.toDto(teacherNameCache: MutableMap<Long, String?> = mutableMapOf()): CourseDto {
        val enrolledCount = enrollmentRepository.countActiveEnrollmentsByCourse(id!!).toInt()
        val resolvedTeacherName = teacherId?.let { id -> teacherNameCache.getOrPut(id) {
            teacherInfoProvider.getTeacherNameById(id)
        } }
        val totalEnrollmentCount = enrollmentRepository.countByCourseId(id).toInt()
        val availableSeats = maxStudents - enrolledCount
        val reservationSummaries = reservationInfoProvider.getReservationsByCourse(id)
        val reservationSummary = reservationSummaries.firstOrNull()
        val isEnrollmentOpen = isPublished &&
                               status == CourseStatus.ACTIVE &&
                               availableSeats > 0 &&
                               reservationSummary != null

        return CourseDto(
            id = id,
            code = code,
            name = name,
            description = description,
            level = level,
            duration = duration,
            maxStudents = maxStudents,
            minStudents = minStudents,
            teacherId = teacherId,
            teacherName = resolvedTeacherName,
            price = price,
            startDate = startDate,
            endDate = endDate,
            status = status,
            isPublished = isPublished,
            hasReservation = reservationSummary != null,
            reservationSummary = reservationSummary,
            reservationSummaries = reservationSummaries,
            enrolledStudents = enrolledCount,
            totalEnrollments = totalEnrollmentCount,
            availableSeats = availableSeats,
            isEnrollmentOpen = isEnrollmentOpen,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun validateCourseReadAccess(course: Course, actorUserId: Long?, actorRole: String?) {
        if (actorRole == "TEACHER" && actorUserId != course.teacherId) {
            throw ForbiddenException("Teachers can only access their assigned courses")
        }
        if (actorRole == "GUARDIAN" && !course.isPublished) {
            throw ForbiddenException("Guardians can only access published courses")
        }
    }

    private fun requireActorUserId(actorUserId: Long?): Long =
        actorUserId ?: throw ForbiddenException("Authenticated user id is required")

    private fun validateAssignableTeacher(teacherId: Long?) {
        if (teacherId != null && teacherInfoProvider.getTeacherNameById(teacherId) == null) {
            throw BusinessException(
                message = "El docente seleccionado no está habilitado para recibir cursos",
                code = "INVALID_TEACHER_ASSIGNMENT",
                field = "teacherId",
                details = "Teacher must be active teaching staff linked to an eligible active account"
            )
        }
    }

    private fun reservationAssignmentProvider(): ReservationAssignmentProvider =
        reservationAssignmentProviderProvider.getIfAvailable()
            ?: throw BusinessException(
                message = "No reservation assignment provider available",
                code = "INTEGRATION_PROVIDER_NOT_AVAILABLE",
                field = "reservationIds",
                details = "Scheduling module provider is required to synchronize course reservations"
            )

    private fun normalizeReservationIds(reservationIds: Collection<Long>): Set<Long> {
        if (reservationIds.any { it <= 0 }) {
            throw BusinessException(
                message = "Reservation IDs must be positive",
                code = "VALIDATION_ERROR",
                field = "reservationIds"
            )
        }
        return reservationIds.toSet()
    }

    private fun Course.toSimpleDto(): CourseSimpleDto {
        val enrolledCount = enrollmentRepository.countActiveEnrollmentsByCourse(id!!).toInt()
        val availableSeats = maxStudents - enrolledCount
        val hasReservation = reservationInfoProvider.hasReservationAssigned(id)
        val isEnrollmentOpen = isPublished &&
                               status == CourseStatus.ACTIVE &&
                               availableSeats > 0 &&
                               hasReservation

        return CourseSimpleDto(
            id = id,
            code = code,
            name = name,
            level = level,
            price = price,
            availableSeats = availableSeats,
            isEnrollmentOpen = isEnrollmentOpen,
            hasReservation = hasReservation
        )
    }

    private fun Enrollment.toDto() = EnrollmentDto(
        id = id!!,
        studentId = studentId,
        studentName = studentProfileProviderProvider.getIfAvailable()
            ?.getStudentProfile(studentId)
            ?.let { "${it.firstName} ${it.lastName}".trim() },
        courseId = course.id!!,
        courseName = course.name,
        enrollmentDate = enrollmentDate,
        status = status,
        finalGrade = finalGrade,
        completionDate = completionDate,
        notes = notes,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

