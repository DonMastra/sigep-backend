package com.sigep.students.domain.model

import com.sigep.common.domain.AggregateRoot
import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(
    name = "students",
    indexes = [
        Index(name = "idx_students_guardian", columnList = "guardian_id"),
        Index(name = "idx_students_document_identity", columnList = "document_country,document_type,normalized_document_number"),
        Index(name = "uq_students_student_number", columnList = "student_number", unique = true)
    ]
)
data class Student(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "student_number", nullable = false, unique = true, updatable = false, length = 32)
    val studentNumber: String = StudentNumberGenerator.next(),

    @Column(nullable = false)
    val firstName: String,

    @Column(nullable = false)
    val lastName: String,

    @Column(nullable = false)
    val email: String,

    @Column(nullable = false)
    val dateOfBirth: LocalDate,

    @Column(nullable = false)
    val address: String,

    @Column(nullable = false)
    val phoneNumber: String,

    @Column(nullable = false)
    val emergencyContact: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 30)
    val documentType: StudentDocumentType = StudentDocumentType.DNI,

    @Column(name = "document_country", nullable = false, length = 2)
    val documentCountry: String = "AR",

    @Column(name = "document_number", length = 50)
    val documentNumber: String? = null,

    @Column(name = "normalized_document_number", length = 50)
    val normalizedDocumentNumber: String? = null,

    /**
     * Tutor principal opcional para compatibilidad con integraciones legadas.
     * La fuente de verdad para acceso académico es student_guardian_relationships.
     */
    @Column(nullable = true)
    val guardianId: Long? = null,

    @Column(nullable = false)
    val enrollmentDate: LocalDate,

    @Column(length = 1000)
    val medicalNotes: String? = null,

    @Column(length = 500)
    val photoUrl: String? = null,

    @Column(nullable = false)
    val active: Boolean = true,

    @Column(nullable = false)
    val currentLevel: String, // e.g., "Beginner", "Intermediate", "Advanced"

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now()
) : AggregateRoot

private object StudentNumberGenerator {
    fun next(): String = "SIGEP-${UUID.randomUUID().toString().replace("-", "").take(12).uppercase()}"
}

enum class StudentDocumentType {
    DNI,
    PASSPORT,
    NATIONAL_ID,
    NO_DOCUMENT,
    IN_PROCESS
}


