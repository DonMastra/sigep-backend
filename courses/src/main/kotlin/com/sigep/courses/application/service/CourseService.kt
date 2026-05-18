package com.sigep.courses.application.service

import com.sigep.common.application.exception.ForbiddenException
import com.sigep.common.application.dto.PageResponse
import com.sigep.common.domain.exception.ResourceNotFoundException
import com.sigep.common.domain.exception.BusinessException
import com.sigep.courses.application.dto.*
import com.sigep.courses.application.event.CourseEventPublisher
import com.sigep.courses.domain.event.CoursePublishedEvent
import com.sigep.courses.domain.model.*
import com.sigep.courses.domain.repository.CourseRepository
import com.sigep.courses.domain.repository.EnrollmentRepository
import com.sigep.security.domain.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
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
    private val userRepository: UserRepository
) {

    private val logger = LoggerFactory.getLogger(CourseService::class.java)

    @Cacheable(value = ["courses"], key = "#id")
    fun getCourseById(id: Long): CourseDto {
        logger.info("Fetching course with id: {}", id)
        val course = courseRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Course not found with id: $id") }
        return course.toDto()
    }

    fun getAllCourses(page: Int, size: Int, sortBy: String, sortDirection: String): PageResponse<CourseDto> {
        logger.info("Fetching all courses - page: {}, size: {}", page, size)

        val direction = if (sortDirection.uppercase() == "DESC") Sort.Direction.DESC else Sort.Direction.ASC
        val pageable = PageRequest.of(page, size, Sort.by(direction, sortBy))

        val coursesPage = courseRepository.findAll(pageable)

        val teacherNameCache = mutableMapOf<Long, String?>()

        return PageResponse(
            content = coursesPage.content.map { it.toDto(teacherNameCache) },
            page = coursesPage.number,
            size = coursesPage.size,
            totalElements = coursesPage.totalElements,
            totalPages = coursesPage.totalPages
        )
    }

    fun searchCourses(search: String, page: Int, size: Int): PageResponse<CourseDto> {
        logger.info("Searching courses with query: {}", search)

        val pageable = PageRequest.of(page, size)
        val coursesPage = courseRepository.searchCourses(search, pageable)

        val teacherNameCache = mutableMapOf<Long, String?>()

        return PageResponse(
            content = coursesPage.content.map { it.toDto(teacherNameCache) },
            page = coursesPage.number,
            size = coursesPage.size,
            totalElements = coursesPage.totalElements,
            totalPages = coursesPage.totalPages
        )
    }

    fun getCoursesByTeacher(teacherId: Long, page: Int, size: Int): PageResponse<CourseDto> {
        logger.info("Fetching courses for teacher: {}", teacherId)

        val pageable = PageRequest.of(page, size)
        val coursesPage = courseRepository.findByTeacherId(teacherId, pageable)

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

        // Validate that code is unique
        if (courseRepository.existsByCode(request.code)) {
            throw BusinessException("Course code '${request.code}' already exists")
        }

        // Validate minStudents <= maxStudents
        if (request.minStudents > request.maxStudents) {
            throw BusinessException("Minimum students cannot be greater than maximum students")
        }

        // Validate dates if provided
        if (request.startDate != null && request.endDate != null && request.endDate.isBefore(request.startDate)) {
            throw BusinessException("End date cannot be before start date")
        }

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
            status = CourseStatus.ACTIVE,
            isPublished = request.isPublished,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        val savedCourse = courseRepository.save(course)

        // Add schedules
        request.schedules.forEach { scheduleReq ->
            val schedule = CourseSchedule(
                course = savedCourse,
                dayOfWeek = scheduleReq.dayOfWeek,
                startTime = scheduleReq.startTime,
                endTime = scheduleReq.endTime
            )
            savedCourse.schedules.add(schedule)
        }

        logger.info("Course created successfully with id: {}", savedCourse.id)

        return savedCourse.toDto()
    }

    @CacheEvict(value = ["courses"], key = "#id")
    fun updateCourse(id: Long, request: UpdateCourseRequest): CourseDto {
        logger.info("Updating course with id: {}", id)

        val course = courseRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Course not found with id: $id") }

        // Validate code uniqueness if it's being changed
        if (request.code != null && request.code != course.code && courseRepository.existsByCode(request.code)) {
            throw BusinessException("Course code '${request.code}' already exists")
        }

        // Validate minStudents and maxStudents
        val newMinStudents = request.minStudents ?: course.minStudents
        val newMaxStudents = request.maxStudents ?: course.maxStudents
        if (newMinStudents > newMaxStudents) {
            throw BusinessException("Minimum students cannot be greater than maximum students")
        }

        // Validate dates if being updated
        val newStartDate = request.startDate ?: course.startDate
        val newEndDate = request.endDate ?: course.endDate
        if (newStartDate != null && newEndDate != null && newEndDate.isBefore(newStartDate)) {
            throw BusinessException("End date cannot be before start date")
        }

        val updatedCourse = course.copy(
            code = request.code ?: course.code,
            name = request.name ?: course.name,
            description = request.description ?: course.description,
            level = request.level ?: course.level,
            duration = request.duration ?: course.duration,
            maxStudents = newMaxStudents,
            minStudents = newMinStudents,
            teacherId = request.teacherId ?: course.teacherId,
            price = request.price ?: course.price,
            startDate = newStartDate,
            endDate = newEndDate,
            status = request.status ?: course.status,
            isPublished = request.isPublished ?: course.isPublished,
            updatedAt = LocalDateTime.now()
        )

        val savedCourse = courseRepository.save(updatedCourse)
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

        // Verificar si el estudiante ya está inscrito
        val existingEnrollment = enrollmentRepository.findByStudentIdAndCourseId(request.studentId, courseId)
        if (existingEnrollment.isPresent && existingEnrollment.get().status == EnrollmentStatus.ACTIVE) {
            throw BusinessException("Student is already enrolled in this course")
        }

        // Verificar capacidad del curso
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

    fun filterCourses(filter: CourseFilterRequest, page: Int, size: Int): PageResponse<CourseDto> {
        logger.info("Filtering courses with filter: {}", filter)

        val pageable = PageRequest.of(page, size)
        val coursesPage = courseRepository.filterCourses(
            level = filter.level,
            status = filter.status,
            teacherId = filter.teacherId,
            isPublished = filter.isPublished,
            minPrice = filter.minPrice,
            maxPrice = filter.maxPrice,
            pageable = pageable
        )

        var courses = coursesPage.content

        // Apply hasAvailableSeats filter in memory (since it requires enrollment count)
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

        if (course.schedules.isEmpty()) {
            throw BusinessException("Cannot publish a course without schedules")
        }

        val activeEnrollments = enrollmentRepository.countActiveEnrollmentsByCourse(id).toInt()
        if (activeEnrollments < course.minStudents) {
            throw BusinessException("Cannot publish course with fewer than minimum required students")
        }

        val updatedCourse = course.copy(
            isPublished = true,
            updatedAt = LocalDateTime.now()
        )

        val savedCourse = courseRepository.save(updatedCourse)
        logger.info("Course published successfully with id: {}", savedCourse.id)

        // Publish event for notifications
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

        val updatedCourse = course.copy(
            isPublished = false,
            updatedAt = LocalDateTime.now()
        )

        val savedCourse = courseRepository.save(updatedCourse)
        logger.info("Course unpublished successfully with id: {}", savedCourse.id)

        return savedCourse.toDto()
    }

    @CacheEvict(value = ["courses"], key = "#id")
    fun activateCourse(id: Long): CourseDto {
        logger.info("Activating course with id: {}", id)

        val course = courseRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Course not found with id: $id") }

        val updatedCourse = course.copy(
            status = CourseStatus.ACTIVE,
            updatedAt = LocalDateTime.now()
        )

        val savedCourse = courseRepository.save(updatedCourse)
        logger.info("Course activated successfully with id: {}", savedCourse.id)

        return savedCourse.toDto()
    }

    @CacheEvict(value = ["courses"], key = "#id")
    fun deactivateCourse(id: Long): CourseDto {
        logger.info("Deactivating course with id: {}", id)

        val course = courseRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Course not found with id: $id") }

        val updatedCourse = course.copy(
            status = CourseStatus.INACTIVE,
            updatedAt = LocalDateTime.now()
        )

        val savedCourse = courseRepository.save(updatedCourse)
        logger.info("Course deactivated successfully with id: {}", savedCourse.id)

        return savedCourse.toDto()
    }

    private fun Course.toDto(teacherNameCache: MutableMap<Long, String?> = mutableMapOf()): CourseDto {
        val enrolledCount = enrollmentRepository.countActiveEnrollmentsByCourse(id!!).toInt()
        val resolvedTeacherName = teacherNameCache.getOrPut(teacherId) {
            userRepository.findById(teacherId)
                .map { user -> "${user.firstName} ${user.lastName}".trim() }
                .orElse(null)
        }
        val availableSeats = maxStudents - enrolledCount
        val isEnrollmentOpen = isPublished &&
                                status == CourseStatus.ACTIVE &&
                                availableSeats > 0 &&
                                (startDate == null || !startDate.isBefore(java.time.LocalDate.now()))

        return CourseDto(
            id = id,
            code = code,
            name = name,
            description = description,
            level = level,
            duration = duration!!,
            maxStudents = maxStudents!!,
            minStudents = minStudents,
            teacherId = teacherId,
            teacherName = resolvedTeacherName,
            price = price,
            startDate = startDate,
            endDate = endDate,
            status = status,
            isPublished = isPublished,
            schedules = schedules.map { it.toDto() },
            enrolledStudents = enrolledCount,
            availableSeats = availableSeats,
            isEnrollmentOpen = isEnrollmentOpen,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun Course.toSimpleDto(): CourseSimpleDto {
        val enrolledCount = enrollmentRepository.countActiveEnrollmentsByCourse(id!!).toInt()
        val availableSeats = maxStudents - enrolledCount
        val isEnrollmentOpen = isPublished &&
                                status == CourseStatus.ACTIVE &&
                                availableSeats > 0 &&
                                (startDate == null || !startDate.isBefore(java.time.LocalDate.now()))

        return CourseSimpleDto(
            id = id,
            code = code,
            name = name,
            level = level,
            price = price,
            availableSeats = availableSeats,
            isEnrollmentOpen = isEnrollmentOpen
        )
    }

    private fun CourseSchedule.toDto() = CourseScheduleDto(
        id = id,
        dayOfWeek = dayOfWeek,
        startTime = startTime,
        endTime = endTime
    )

    private fun Enrollment.toDto() = EnrollmentDto(
        id = id!!,
        studentId = studentId,
        studentName = null,
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

