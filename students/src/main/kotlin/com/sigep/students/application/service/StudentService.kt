package com.sigep.students.application.service

import com.sigep.common.application.dto.PageResponse
import com.sigep.common.application.dto.PaginationInfo
import com.sigep.common.application.exception.DuplicateResourceException
import com.sigep.common.application.exception.ResourceNotFoundException
import com.sigep.students.application.dto.CreateStudentRequest
import com.sigep.students.application.dto.StudentDto
import com.sigep.students.application.dto.UpdateStudentRequest
import com.sigep.students.domain.model.Student
import com.sigep.students.domain.model.StudentStatus
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
    private val studentRepository: StudentRepository
) {

    private val logger = LoggerFactory.getLogger(StudentService::class.java)

    @Cacheable(value = ["students"], key = "#id")
    fun getStudentById(id: Long): StudentDto {
        logger.info("Fetching student with id: {}", id)
        val student = studentRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Student not found with id: $id") }
        return student.toDto()
    }

    fun getAllStudents(page: Int, size: Int, sortBy: String, sortDirection: String): PageResponse<StudentDto> {
        logger.info("Fetching all students - page: {}, size: {}", page, size)

        val direction = if (sortDirection.uppercase() == "DESC") Sort.Direction.DESC else Sort.Direction.ASC
        val pageable = PageRequest.of(page, size, Sort.by(direction, sortBy))

        val studentsPage = studentRepository.findAll(pageable)

        return PageResponse(
            items = studentsPage.content.map { it.toDto() },
            pagination = PaginationInfo(
                page = page,
                limit = size,
                total = studentsPage.totalElements,
                totalPages = studentsPage.totalPages
            )
        )
    }

    fun searchStudents(search: String, page: Int, size: Int): PageResponse<StudentDto> {
        logger.info("Searching students with query: {}", search)

        val pageable = PageRequest.of(page, size)
        val studentsPage = studentRepository.searchStudents(search, pageable)

        return PageResponse(
            items = studentsPage.content.map { it.toDto() },
            pagination = PaginationInfo(
                page = page,
                limit = size,
                total = studentsPage.totalElements,
                totalPages = studentsPage.totalPages
            )
        )
    }

    @CacheEvict(value = ["students"], allEntries = true)
    fun createStudent(request: CreateStudentRequest): StudentDto {
        logger.info("Creating new student with email: {}", request.email)

        if (studentRepository.existsByEmail(request.email)) {
            throw DuplicateResourceException("Student with email ${request.email} already exists")
        }

        val student = Student(
            firstName = request.firstName,
            lastName = request.lastName,
            email = request.email,
            phone = request.phone,
            dateOfBirth = request.dateOfBirth,
            address = request.address,
            guardianId = request.guardianId,
            enrollmentDate = LocalDate.now(),
            status = StudentStatus.ACTIVE,
            currentLevel = request.currentLevel,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        val savedStudent = studentRepository.save(student)
        logger.info("Student created successfully with id: {}", savedStudent.id)

        return savedStudent.toDto()
    }

    @CacheEvict(value = ["students"], key = "#id")
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

        val updatedStudent = student.copy(
            firstName = request.firstName ?: student.firstName,
            lastName = request.lastName ?: student.lastName,
            email = request.email ?: student.email,
            phone = request.phone ?: student.phone,
            address = request.address ?: student.address,
            currentLevel = request.currentLevel ?: student.currentLevel,
            status = request.status ?: student.status,
            updatedAt = LocalDateTime.now()
        )

        val savedStudent = studentRepository.save(updatedStudent)
        logger.info("Student updated successfully with id: {}", savedStudent.id)

        return savedStudent.toDto()
    }

    @CacheEvict(value = ["students"], key = "#id")
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
            items = studentsPage.content.map { it.toDto() },
            pagination = PaginationInfo(
                page = page,
                limit = size,
                total = studentsPage.totalElements,
                totalPages = studentsPage.totalPages
            )
        )
    }

    private fun Student.toDto() = StudentDto(
        id = id!!,
        firstName = firstName,
        lastName = lastName,
        email = email,
        phone = phone,
        dateOfBirth = dateOfBirth,
        address = address,
        guardianId = guardianId,
        enrollmentDate = enrollmentDate,
        status = status,
        currentLevel = currentLevel,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

