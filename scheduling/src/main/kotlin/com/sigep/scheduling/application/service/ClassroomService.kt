package com.sigep.scheduling.application.service

import com.sigep.common.application.dto.PageResponse
import com.sigep.common.application.exception.DuplicateResourceException
import com.sigep.common.application.exception.ResourceNotFoundException
import com.sigep.scheduling.application.dto.*
import com.sigep.scheduling.domain.model.Classroom
import com.sigep.scheduling.domain.repository.ClassroomRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@Transactional
class ClassroomService(private val classroomRepository: ClassroomRepository) {

    private val logger = LoggerFactory.getLogger(ClassroomService::class.java)

    fun getAllClassrooms(page: Int, size: Int): PageResponse<ClassroomDto> {
        val pageable = PageRequest.of(page, size)
        val result = classroomRepository.findAll(pageable)
        return PageResponse(
            content = result.content.map { it.toDto() },
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages
        )
    }

    fun getActiveClassrooms(): List<ClassroomDto> =
        classroomRepository.findByActiveTrue().map { it.toDto() }

    fun getClassroomById(id: Long): ClassroomDto =
        classroomRepository.findById(id)
            .map { it.toDto() }
            .orElseThrow { ResourceNotFoundException("Classroom not found with id: $id") }

    fun createClassroom(request: CreateClassroomRequest): ClassroomDto {
        if (classroomRepository.existsByNameIgnoreCase(request.name)) {
            throw DuplicateResourceException("A classroom with name '${request.name}' already exists")
        }
        val classroom = Classroom(
            name = request.name,
            building = request.building,
            floor = request.floor,
            capacity = request.capacity
        )
        val saved = classroomRepository.save(classroom)
        logger.info("Classroom created: {} (id={})", saved.name, saved.id)
        return saved.toDto()
    }

    fun updateClassroom(id: Long, request: UpdateClassroomRequest): ClassroomDto {
        val classroom = classroomRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Classroom not found with id: $id") }

        if (request.name != null && !request.name.equals(classroom.name, ignoreCase = true)
            && classroomRepository.existsByNameIgnoreCase(request.name)) {
            throw DuplicateResourceException("A classroom with name '${request.name}' already exists")
        }

        val updated = classroom.copy(
            name = request.name ?: classroom.name,
            building = request.building ?: classroom.building,
            floor = request.floor ?: classroom.floor,
            capacity = request.capacity ?: classroom.capacity,
            active = request.active ?: classroom.active,
            updatedAt = LocalDateTime.now()
        )
        return classroomRepository.save(updated).toDto()
    }

    fun softDeleteClassroom(id: Long): ClassroomDto {
        val classroom = classroomRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Classroom not found with id: $id") }
        val deactivated = classroom.copy(active = false, updatedAt = LocalDateTime.now())
        return classroomRepository.save(deactivated).toDto()
    }

    private fun Classroom.toDto() = ClassroomDto(
        id = id!!,
        name = name,
        building = building,
        floor = floor,
        capacity = capacity,
        active = active,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
