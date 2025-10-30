package com.sigep.courses.domain.model

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "course_materials")
data class CourseMaterial(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    val course: Course,

    @Column(nullable = false)
    val title: String,

    @Column(length = 1000)
    val description: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val type: MaterialType,

    @Column(nullable = false)
    val fileUrl: String, // URL or path to the file

    @Column
    val fileName: String? = null, // Original filename

    @Column
    val fileSize: Long? = null, // Size in bytes

    @Column
    val mimeType: String? = null, // e.g., application/pdf, video/mp4

    @Column(nullable = false)
    val uploadedBy: Long, // ID of the user who uploaded

    @Column(nullable = false)
    val isVisible: Boolean = true, // If students can see it

    @Column
    val orderIndex: Int = 0, // For ordering materials

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now()
)

enum class MaterialType {
    PDF,           // PDF documents
    VIDEO,         // Video files
    AUDIO,         // Audio files
    IMAGE,         // Images
    DOCUMENT,      // Word, Excel, etc.
    PRESENTATION,  // PowerPoint, etc.
    LINK,          // External links
    ZIP,           // Compressed files
    OTHER          // Other types
}

