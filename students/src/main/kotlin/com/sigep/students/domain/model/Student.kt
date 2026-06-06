package com.sigep.students.domain.model

import com.sigep.common.domain.AggregateRoot
import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(name = "students")
data class Student(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false)
    val firstName: String,

    @Column(nullable = false)
    val lastName: String,

    @Column(unique = true, nullable = false)
    val email: String,

    @Column(nullable = false)
    val dateOfBirth: LocalDate,

    @Column(nullable = false)
    val address: String,

    @Column(nullable = false)
    val phoneNumber: String,

    @Column(nullable = false)
    val emergencyContact: String,

    @Column(nullable = false)
    val documentNumber: String,

    /** Referencia al User con rol GUARDIAN. Nullable: un estudiante puede no tener tutor registrado. */
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


