package com.sigep.students.application.service

import com.sigep.common.application.exception.ValidationException
import com.sigep.students.domain.model.StudentDocumentType
import org.springframework.stereotype.Component
import java.text.Normalizer

@Component
class StudentIdentityNormalizer {

    fun normalize(
        type: StudentDocumentType,
        country: String,
        documentNumber: String?
    ): NormalizedStudentDocument {
        val normalizedCountry = country.trim().uppercase()
        if (!normalizedCountry.matches(Regex("^[A-Z]{2}$"))) {
            throw ValidationException(
                message = "Document country must be an ISO alpha-2 code",
                code = "INVALID_STUDENT_DOCUMENT",
                field = "documentCountry"
            )
        }

        if (type in setOf(StudentDocumentType.NO_DOCUMENT, StudentDocumentType.IN_PROCESS)) {
            if (!documentNumber.isNullOrBlank()) {
                throw ValidationException(
                    message = "Document number must be empty for $type",
                    code = "INVALID_STUDENT_DOCUMENT",
                    field = "documentNumber"
                )
            }
            return NormalizedStudentDocument(type, normalizedCountry, null, null)
        }

        val raw = documentNumber?.trim().orEmpty()
        if (raw.isBlank()) {
            throw ValidationException(
                message = "Document number is required",
                code = "INVALID_STUDENT_DOCUMENT",
                field = "documentNumber"
            )
        }

        return when (type) {
            StudentDocumentType.DNI -> normalizeArgentineDni(normalizedCountry, raw)
            StudentDocumentType.PASSPORT,
            StudentDocumentType.NATIONAL_ID -> normalizeForeignDocument(type, normalizedCountry, raw)
            StudentDocumentType.NO_DOCUMENT,
            StudentDocumentType.IN_PROCESS -> error("Handled above")
        }
    }

    fun normalizePersonName(value: String): String =
        Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("\\s+"), " ")
            .uppercase()

    private fun normalizeArgentineDni(country: String, raw: String): NormalizedStudentDocument {
        if (country != "AR") {
            throw ValidationException(
                message = "DNI document type requires country AR",
                code = "INVALID_STUDENT_DOCUMENT",
                field = "documentCountry"
            )
        }
        val digits = raw
            .uppercase()
            .replace(Regex("^DNI[\\s.:-]*"), "")
            .replace(Regex("[.\\s-]"), "")
        if (!digits.matches(Regex("^\\d{7,8}$"))) {
            throw ValidationException(
                message = "Argentine DNI must contain seven or eight digits",
                code = "INVALID_STUDENT_DOCUMENT",
                field = "documentNumber"
            )
        }
        val normalized = digits.padStart(8, '0')
        return NormalizedStudentDocument(StudentDocumentType.DNI, country, normalized, normalized)
    }

    private fun normalizeForeignDocument(
        type: StudentDocumentType,
        country: String,
        raw: String
    ): NormalizedStudentDocument {
        val normalized = raw.uppercase().replace(Regex("[^A-Z0-9]"), "")
        if (!normalized.matches(Regex("^[A-Z0-9]{5,20}$"))) {
            throw ValidationException(
                message = "Passport or national identity number must contain 5 to 20 letters or digits",
                code = "INVALID_STUDENT_DOCUMENT",
                field = "documentNumber"
            )
        }
        return NormalizedStudentDocument(type, country, raw.trim().uppercase(), normalized)
    }
}

data class NormalizedStudentDocument(
    val type: StudentDocumentType,
    val country: String,
    val displayNumber: String?,
    val normalizedNumber: String?
)
