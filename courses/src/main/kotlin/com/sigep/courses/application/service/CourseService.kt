package com.sigep.courses.application.service

import com.sigep.common.application.dto.PageResponse
import com.sigep.common.domain.exception.ResourceNotFoundException
import com.sigep.common.domain.exception.BusinessException
import com.sigep.courses.application.dto.*
import com.sigep.courses.domain.model.*
import com.sigep.courses.domain.repository.CourseRepository
import com.sigep.courses.domain.repository.EnrollmentRepository
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
    private val enrollmentRepository: EnrollmentRepository
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

        return PageResponse(
            content = coursesPage.content.map { it.toDto() },
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

        return PageResponse(
            content = coursesPage.content.map { it.toDto() },
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

        return PageResponse(
            content = coursesPage.content.map { it.toDto() },
            page = coursesPage.number,
            size = coursesPage.size,
            totalElements = coursesPage.totalElements,
            totalPages = coursesPage.totalPages
        )
    }

    @CacheEvict(value = ["courses"], allEntries = true)
    fun createCourse(request: CreateCourseRequest): CourseDto {
        logger.info("Creating new course: {}", request.name)

        val schedules = request.schedules.map { scheduleReq ->
            CourseSchedule(
                course = Course(name = request.name, description = request.description, level = request.level,
                               duration = request.duration, maxStudents = request.maxStudents, teacherId = request.teacherId),
                dayOfWeek = scheduleReq.dayOfWeek,
                startTime = scheduleReq.startTime,
                endTime = scheduleReq.endTime
            )
        }.toMutableList()

        val course = Course(
            name = request.name,
            description = request.description,
            level = request.level,
            duration = request.duration,
            maxStudents = request.maxStudents,
            teacherId = request.teacherId,
            status = CourseStatus.ACTIVE,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        val savedCourse = courseRepository.save(course)

        // Actualizar las referencias de los schedules al curso guardado
        schedules.forEach { it.course }
        savedCourse.schedules.addAll(schedules)

        logger.info("Course created successfully with id: {}", savedCourse.id)

        return savedCourse.toDto()
    }

    @CacheEvict(value = ["courses"], key = "#id")
    fun updateCourse(id: Long, request: UpdateCourseRequest): CourseDto {
        logger.info("Updating course with id: {}", id)

        val course = courseRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Course not found with id: $id") }

        val updatedCourse = course.copy(
            name = request.name ?: course.name,
            description = request.description ?: course.description,
            level = request.level ?: course.level,
            duration = request.duration ?: course.duration,
            maxStudents = request.maxStudents ?: course.maxStudents,
            teacherId = request.teacherId ?: course.teacherId,
            status = request.status ?: course.status,
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
    fun enrollStudent(courseId: Long, request: EnrollStudentRequest): EnrollmentDto {
        logger.info("Enrolling student {} in course {}", request.studentId, courseId)

        val course = courseRepository.findById(courseId)
            .orElseThrow { ResourceNotFoundException("Course not found with id: $courseId") }

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

    private fun Course.toDto() = CourseDto(
        id = id!!,
        name = name,
        description = description,
        level = level,
        duration = duration,
        maxStudents = maxStudents,
        teacherId = teacherId,
        status = status,
        schedules = schedules.map { it.toDto() },
        enrolledStudents = enrollmentRepository.countActiveEnrollmentsByCourse(id!!).toInt(),
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun CourseSchedule.toDto() = CourseScheduleDto(
        id = id,
        dayOfWeek = dayOfWeek,
        startTime = startTime,
        endTime = endTime
    )

    private fun Enrollment.toDto() = EnrollmentDto(
        id = id!!,
        studentId = studentId,
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

