package com.sigep.common.application.exception

open class BusinessException(
    override val message: String,
    val code: String = "BUSINESS_ERROR"
) : RuntimeException(message)

class ResourceNotFoundException(
    message: String,
    code: String = "RESOURCE_NOT_FOUND"
) : BusinessException(message, code)

class ValidationException(
    message: String,
    val details: List<String> = emptyList(),
    code: String = "VALIDATION_ERROR"
) : BusinessException(message, code)

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

