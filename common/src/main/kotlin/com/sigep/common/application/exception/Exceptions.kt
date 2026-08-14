package com.sigep.common.application.exception

open class BusinessException(
    override val message: String,
    val code: String = "BUSINESS_ERROR",
    val field: String? = null,
    val details: String? = null
) : RuntimeException(message)

class ResourceNotFoundException(
    message: String,
    code: String = "RESOURCE_NOT_FOUND"
) : BusinessException(message, code)

class ValidationException(
    message: String,
    val validationDetails: List<String> = emptyList(),
    code: String = "VALIDATION_ERROR",
    field: String? = null,
    details: String? = null
) : BusinessException(message, code, field, details)

class UnauthorizedException(
    message: String = "Unauthorized access",
    code: String = "UNAUTHORIZED"
) : BusinessException(message, code)

class ForbiddenException(
    message: String = "Forbidden access",
    code: String = "FORBIDDEN"
) : BusinessException(message, code)

class DuplicateResourceException(
    message: String,
    code: String = "DUPLICATE_RESOURCE"
) : BusinessException(message, code)

class ResourceConflictException(
    message: String,
    code: String = "RESOURCE_CONFLICT",
    field: String? = null,
    details: String? = null
) : BusinessException(message, code, field, details)

class UnprocessableEntityException(
    message: String,
    code: String = "UNPROCESSABLE_ENTITY",
    field: String? = null,
    details: String? = null
) : BusinessException(message, code, field, details)

class ReservationAlreadyAssignedException(
    message: String,
    field: String? = null,
    details: String? = null
) : BusinessException(message, "RESERVATION_ALREADY_ASSIGNED", field, details)

class CourseReservationLimitExceededException(
    message: String,
    field: String? = null,
    details: String? = null
) : BusinessException(message, "COURSE_RESERVATION_LIMIT_EXCEEDED", field, details)

class ReservationNotAvailableException(
    message: String,
    field: String? = null,
    details: String? = null
) : BusinessException(message, "RESERVATION_NOT_AVAILABLE", field, details)

