package com.sigep.exams.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID

class ExamSubmissionSkillGradesTest {

    @Test
    fun `calcula el promedio simple y redondea a entero`() {
        val submission = newSubmission()

        submission.updateSkillGrades(
            readingScore = 60,
            writingScore = 61,
            listeningScore = 61,
            updatedBy = 20
        )

        assertEquals(BigDecimal("61"), submission.score)
        assertEquals(SubmissionStatus.GRADED, submission.status)
        assertEquals(20, submission.gradedBy)
    }

    @Test
    fun `mantiene pendiente una carga parcial sin nota final`() {
        val submission = newSubmission()

        submission.updateSkillGrades(
            readingScore = 80,
            writingScore = null,
            listeningScore = 75,
            updatedBy = 20
        )

        assertNull(submission.score)
        assertEquals(SubmissionStatus.PENDING, submission.status)
        assertNull(submission.gradedBy)
    }

    @Test
    fun `no reemplaza una nota final existente con una carga parcial`() {
        val submission = newSubmission().apply {
            grade(BigDecimal("70"), gradedBy = 20)
        }

        assertThrows(IllegalArgumentException::class.java) {
            submission.updateSkillGrades(
                readingScore = 80,
                writingScore = null,
                listeningScore = 75,
                updatedBy = 20
            )
        }
    }

    @Test
    fun `rechaza valores fuera de cero a cien`() {
        val submission = newSubmission()

        assertThrows(IllegalArgumentException::class.java) {
            submission.updateSkillGrades(
                readingScore = 101,
                writingScore = 50,
                listeningScore = 50,
                updatedBy = 20
            )
        }
    }

    private fun newSubmission() = ExamSubmission(
        examId = UUID.randomUUID(),
        studentId = 1,
        createdBy = 10
    )
}
