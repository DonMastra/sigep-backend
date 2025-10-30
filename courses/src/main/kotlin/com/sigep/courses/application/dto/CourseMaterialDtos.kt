package com.sigep.courses.application.dto

import com.sigep.courses.domain.model.MaterialType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

data class CourseMaterialDto(
    val id: Long,
    val courseId: Long,
    val courseName: String,
    val title: String,
    val description: String?,
    val type: MaterialType,
    val fileUrl: String,
    val fileName: String?,
    val fileSize: Long?,
    val mimeType: String?,
    val uploadedBy: Long,
    val uploadedByName: String? = null,
    val isVisible: Boolean,
    val orderIndex: Int,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

data class CreateCourseMaterialRequest(
    @field:NotNull(message = "Course ID is required")
    val courseId: Long,

    @field:NotBlank(message = "Title is required")
    @field:Size(min = 3, max = 200)
    val title: String,

    @field:Size(max = 1000)
    val description: String? = null,

    @field:NotNull(message = "Material type is required")
    val type: MaterialType,

    @field:NotBlank(message = "File URL is required")
    val fileUrl: String,

    val fileName: String? = null,

    val fileSize: Long? = null,

    val mimeType: String? = null,

    val isVisible: Boolean = true,

    val orderIndex: Int = 0
)

data class UpdateCourseMaterialRequest(
    @field:Size(min = 3, max = 200)
    val title: String?,

    @field:Size(max = 1000)
    val description: String?,

    val type: MaterialType?,

    val fileUrl: String?,

    val fileName: String?,

    val fileSize: Long?,

    val mimeType: String?,

    val isVisible: Boolean?,

    val orderIndex: Int?
)

data class CourseMaterialsStatisticsDto(
    val courseId: Long,
    val courseName: String,
    val totalMaterials: Long,
    val visibleMaterials: Long,
    val materialsByType: Map<MaterialType, Long>,
    val totalFileSize: Long // Total size in bytes
)

data class ReorderMaterialsRequest(
    @field:NotNull(message = "Material order is required")
    val materialOrders: List<MaterialOrder>
)

data class MaterialOrder(
    @field:NotNull(message = "Material ID is required")
    val materialId: Long,

    @field:NotNull(message = "Order index is required")
    val orderIndex: Int
)

