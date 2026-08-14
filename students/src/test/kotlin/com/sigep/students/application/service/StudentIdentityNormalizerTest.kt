package com.sigep.students.application.service

import com.sigep.common.application.exception.ValidationException
import com.sigep.students.domain.model.StudentDocumentType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class StudentIdentityNormalizerTest {
    private val normalizer = StudentIdentityNormalizer()

    @Test
    fun `argentine dni ignores separators and pads seven digits`() {
        val formatted = normalizer.normalize(StudentDocumentType.DNI, "ar", "Dni 12.345.678")
        val short = normalizer.normalize(StudentDocumentType.DNI, "AR", "1 234 567")

        assertEquals("12345678", formatted.normalizedNumber)
        assertEquals("01234567", short.normalizedNumber)
        assertEquals("AR", short.country)
    }

    @Test
    fun `foreign passport is scoped by country and normalized as alphanumeric uppercase`() {
        val result = normalizer.normalize(StudentDocumentType.PASSPORT, "uy", "ab-123 45")

        assertEquals("UY", result.country)
        assertEquals("AB12345", result.normalizedNumber)
    }

    @Test
    fun `no document has no unique number`() {
        val result = normalizer.normalize(StudentDocumentType.NO_DOCUMENT, "AR", null)

        assertNull(result.normalizedNumber)
        assertNull(result.displayNumber)
    }

    @Test
    fun `dni is only valid for argentina`() {
        assertFailsWith<ValidationException> {
            normalizer.normalize(StudentDocumentType.DNI, "UY", "12345678")
        }
    }
}
