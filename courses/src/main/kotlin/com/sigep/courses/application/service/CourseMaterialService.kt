package com.sigep.courses.application.service

import com.sigep.common.application.dto.PageResponse
import com.sigep.common.domain.exception.BusinessException
import com.sigep.common.domain.exception.ResourceNotFoundException
import com.sigep.courses.application.dto.*
import com.sigep.courses.application.event.CourseEventPublisher
import com.sigep.courses.domain.event.CourseMaterialUploadedEvent
import com.sigep.courses.domain.model.CourseMaterial
import com.sigep.courses.domain.model.MaterialType
import com.sigep.courses.domain.repository.CourseMaterialRepository
import com.sigep.courses.domain.repository.CourseRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@Transactional
class CourseMaterialService(
    private val courseMaterialRepository: CourseMaterialRepository,
    private val courseRepository: CourseRepository,
    private val eventPublisher: CourseEventPublisher
) {

    private val logger = LoggerFactory.getLogger(CourseMaterialService::class.java)

    fun getMaterialById(id: Long): CourseMaterialDto {
        logger.info("Fetching course material with id: {}", id)
        val material = courseMaterialRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Course material not found with id: $id") }
        return material.toDto()
    }

    fun getMaterialsByCourse(courseId: Long, page: Int, size: Int, visibleOnly: Boolean = false): PageResponse<CourseMaterialDto> {
        logger.info("Fetching materials for course: {}, visibleOnly: {}", courseId, visibleOnly)

        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "orderIndex"))
        val materialsPage = if (visibleOnly) {
            courseMaterialRepository.findByCourseIdAndIsVisibleTrue(courseId, pageable)
        } else {
            courseMaterialRepository.findByCourseId(courseId, pageable)
        }

        return PageResponse(
            content = materialsPage.content.map { it.toDto() },
            page = materialsPage.number,
            size = materialsPage.size,
            totalElements = materialsPage.totalElements,
            totalPages = materialsPage.totalPages
        )
    }

    fun getMaterialsByCourseAndType(courseId: Long, type: MaterialType, page: Int, size: Int): PageResponse<CourseMaterialDto> {
        logger.info("Fetching materials of type {} for course: {}", type, courseId)

        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "orderIndex"))
        val materialsPage = courseMaterialRepository.findByCourseIdAndType(courseId, type, pageable)

        return PageResponse(
            content = materialsPage.content.map { it.toDto() },
            page = materialsPage.number,
            size = materialsPage.size,
            totalElements = materialsPage.totalElements,
            totalPages = materialsPage.totalPages
        )
    }

    fun createMaterial(request: CreateCourseMaterialRequest, uploadedBy: Long): CourseMaterialDto {
        logger.info("Creating course material for course: {}", request.courseId)

        val course = courseRepository.findById(request.courseId)
            .orElseThrow { ResourceNotFoundException("Course not found with id: ${request.courseId}") }

        val material = CourseMaterial(
            course = course,
            title = request.title,
            description = request.description,
            type = request.type,
            fileUrl = request.fileUrl,
            fileName = request.fileName,
            fileSize = request.fileSize,
            mimeType = request.mimeType,
            uploadedBy = uploadedBy,
            isVisible = request.isVisible,
            orderIndex = request.orderIndex,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        val savedMaterial = courseMaterialRepository.save(material)
        logger.info("Course material created successfully with id: {}", savedMaterial.id)

        // Publish event for notifications
        eventPublisher.publishMaterialUploaded(
            CourseMaterialUploadedEvent(
                materialId = savedMaterial.id!!,
                courseId = course.id!!,
                courseName = course.name,
                title = request.title,
                type = request.type.name,
                uploadedBy = uploadedBy
            )
        )

        return savedMaterial.toDto()
    }

    fun updateMaterial(id: Long, request: UpdateCourseMaterialRequest): CourseMaterialDto {
        logger.info("Updating course material with id: {}", id)

        val material = courseMaterialRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Course material not found with id: $id") }

        val updatedMaterial = material.copy(
            title = request.title ?: material.title,
            description = request.description ?: material.description,
            type = request.type ?: material.type,
            fileUrl = request.fileUrl ?: material.fileUrl,
            fileName = request.fileName ?: material.fileName,
            fileSize = request.fileSize ?: material.fileSize,
            mimeType = request.mimeType ?: material.mimeType,
            isVisible = request.isVisible ?: material.isVisible,
            orderIndex = request.orderIndex ?: material.orderIndex,
            updatedAt = LocalDateTime.now()
        )

        val savedMaterial = courseMaterialRepository.save(updatedMaterial)
        logger.info("Course material updated successfully with id: {}", savedMaterial.id)

        return savedMaterial.toDto()
    }

    fun deleteMaterial(id: Long) {
        logger.info("Deleting course material with id: {}", id)

        if (!courseMaterialRepository.existsById(id)) {
            throw ResourceNotFoundException("Course material not found with id: $id")
        }

        courseMaterialRepository.deleteById(id)
        logger.info("Course material deleted successfully with id: {}", id)
    }

    fun reorderMaterials(courseId: Long, request: ReorderMaterialsRequest): List<CourseMaterialDto> {
        logger.info("Reordering materials for course: {}", courseId)

        request.materialOrders.forEach { order ->
            val material = courseMaterialRepository.findById(order.materialId)
                .orElseThrow { ResourceNotFoundException("Material not found with id: ${order.materialId}") }

            if (material.course.id != courseId) {
                throw BusinessException("Material ${order.materialId} does not belong to course $courseId")
            }

            val updatedMaterial = material.copy(
                orderIndex = order.orderIndex,
                updatedAt = LocalDateTime.now()
            )
            courseMaterialRepository.save(updatedMaterial)
        }

        logger.info("Materials reordered successfully")
        return courseMaterialRepository.findByCourseIdOrderByOrderIndexAsc(courseId).map { it.toDto() }
    }

    fun toggleVisibility(id: Long): CourseMaterialDto {
        logger.info("Toggling visibility for material: {}", id)

        val material = courseMaterialRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Course material not found with id: $id") }

        val updatedMaterial = material.copy(
            isVisible = !material.isVisible,
            updatedAt = LocalDateTime.now()
        )

        val savedMaterial = courseMaterialRepository.save(updatedMaterial)
        logger.info("Material visibility toggled. New state: {}", savedMaterial.isVisible)

        return savedMaterial.toDto()
    }

    fun getMaterialsStatistics(courseId: Long): CourseMaterialsStatisticsDto {
        logger.info("Calculating materials statistics for course: {}", courseId)

        val course = courseRepository.findById(courseId)
            .orElseThrow { ResourceNotFoundException("Course not found with id: $courseId") }

        val allMaterials = courseMaterialRepository.findByCourseIdOrderByOrderIndexAsc(courseId)
        val totalMaterials = allMaterials.size.toLong()
        val visibleMaterials = allMaterials.count { it.isVisible }.toLong()

        val materialsByType = MaterialType.entries.associateWith { type ->
            courseMaterialRepository.countByCourseIdAndType(courseId, type)
        }

        val totalFileSize = allMaterials.mapNotNull { it.fileSize }.sum()

        return CourseMaterialsStatisticsDto(
            courseId = courseId,
            courseName = course.name,
            totalMaterials = totalMaterials,
            visibleMaterials = visibleMaterials,
            materialsByType = materialsByType,
            totalFileSize = totalFileSize
        )
    }

    private fun CourseMaterial.toDto() = CourseMaterialDto(
        id = id!!,
        courseId = course.id!!,
        courseName = course.name,
        title = title,
        description = description,
        type = type,
        fileUrl = fileUrl,
        fileName = fileName,
        fileSize = fileSize,
        mimeType = mimeType,
        uploadedBy = uploadedBy,
        uploadedByName = null, // Can be populated if needed
        isVisible = isVisible,
        orderIndex = orderIndex,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

