package com.sigep.common.infrastructure.config

import com.sigep.common.application.dto.ErrorResponse
import com.sigep.common.application.exception.BusinessException as AppBusinessException
import com.sigep.common.application.exception.DuplicateResourceException as AppDuplicateResourceException
import com.sigep.common.application.exception.ForbiddenException as AppForbiddenException
import com.sigep.common.application.exception.ResourceConflictException as AppResourceConflictException
import com.sigep.common.application.exception.ReservationAlreadyAssignedException as AppReservationAlreadyAssignedException
import com.sigep.common.application.exception.ResourceNotFoundException as AppResourceNotFoundException
import com.sigep.common.application.exception.UnauthorizedException as AppUnauthorizedException
import com.sigep.common.application.exception.ValidationException as AppValidationException
import com.sigep.common.domain.exception.BusinessException as DomainBusinessException
import com.sigep.common.domain.exception.DuplicateResourceException as DomainDuplicateResourceException
import com.sigep.common.domain.exception.ResourceNotFoundException as DomainResourceNotFoundException
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.authorization.AuthorizationDeniedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(AppResourceNotFoundException::class)
    fun handleResourceNotFound(ex: AppResourceNotFoundException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        logger.warn("Resource not found: {}", ex.message)
        return buildErrorResponse(
            status = HttpStatus.NOT_FOUND,
            code = ex.code,
            message = ex.message,
            request = request
        )
    }

    @ExceptionHandler(DomainResourceNotFoundException::class)
    fun handleDomainResourceNotFound(ex: DomainResourceNotFoundException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        logger.warn("Resource not found: {}", ex.message)
        return buildErrorResponse(
            status = HttpStatus.NOT_FOUND,
            code = "RESOURCE_NOT_FOUND",
            message = ex.message ?: "Resource not found",
            request = request
        )
    }

    @ExceptionHandler(AppValidationException::class)
    fun handleValidation(ex: AppValidationException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        logger.warn("Validation error: {}", ex.message)
        val details = ex.details ?: ex.validationDetails.takeIf { it.isNotEmpty() }?.joinToString(", ")
        return buildErrorResponse(
            status = HttpStatus.BAD_REQUEST,
            code = ex.code,
            message = ex.message,
            request = request,
            field = ex.field,
            details = details
        )
    }

    @ExceptionHandler(AppUnauthorizedException::class)
    fun handleUnauthorized(ex: AppUnauthorizedException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        logger.warn("Unauthorized access: {}", ex.message)
        return buildErrorResponse(
            status = HttpStatus.UNAUTHORIZED,
            code = ex.code,
            message = ex.message,
            request = request
        )
    }

    @ExceptionHandler(AppForbiddenException::class)
    fun handleForbidden(ex: AppForbiddenException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        logger.warn("Forbidden access: {}", ex.message)
        return buildErrorResponse(
            status = HttpStatus.FORBIDDEN,
            code = ex.code,
            message = ex.message,
            request = request
        )
    }

    @ExceptionHandler(AuthorizationDeniedException::class)
    fun handleAuthorizationDenied(ex: AuthorizationDeniedException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        logger.warn("Authorization denied: {}", ex.message)
        return buildErrorResponse(
            status = HttpStatus.FORBIDDEN,
            code = "FORBIDDEN",
            message = "You do not have permission to perform this action",
            request = request
        )
    }

    @ExceptionHandler(AppDuplicateResourceException::class)
    fun handleDuplicateResource(ex: AppDuplicateResourceException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        logger.warn("Duplicate resource: {}", ex.message)
        return buildErrorResponse(
            status = HttpStatus.CONFLICT,
            code = ex.code,
            message = ex.message,
            request = request
        )
    }

    @ExceptionHandler(AppResourceConflictException::class)
    fun handleResourceConflict(ex: AppResourceConflictException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        logger.warn("Resource conflict: {}", ex.message)
        return buildErrorResponse(
            status = HttpStatus.CONFLICT,
            code = ex.code,
            message = ex.message,
            request = request,
            field = ex.field,
            details = ex.details
        )
    }

    @ExceptionHandler(AppReservationAlreadyAssignedException::class)
    fun handleReservationAlreadyAssigned(ex: AppReservationAlreadyAssignedException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        logger.warn("Reservation already assigned: {}", ex.message)
        return buildErrorResponse(
            status = HttpStatus.CONFLICT,
            code = ex.code,
            message = ex.message,
            request = request,
            field = ex.field,
            details = ex.details
        )
    }

    @ExceptionHandler(DomainDuplicateResourceException::class)
    fun handleDomainDuplicateResource(ex: DomainDuplicateResourceException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        logger.warn("Duplicate resource: {}", ex.message)
        return buildErrorResponse(
            status = HttpStatus.CONFLICT,
            code = "DUPLICATE_RESOURCE",
            message = ex.message ?: "Duplicate resource",
            request = request
        )
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValid(ex: MethodArgumentNotValidException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        val firstFieldError = ex.bindingResult.fieldErrors.firstOrNull()
        val details = firstFieldError?.defaultMessage ?: "Validation failed"
        logger.warn("Validation failed: {}", ex.bindingResult.fieldErrors)
        return buildErrorResponse(
            status = HttpStatus.BAD_REQUEST,
            code = "VALIDATION_ERROR",
            message = "Validation failed",
            request = request,
            field = firstFieldError?.field,
            details = details
        )
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleHttpMessageNotReadable(ex: HttpMessageNotReadableException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        logger.warn("Malformed request body: {}", ex.mostSpecificCause.message)
        return buildErrorResponse(
            status = HttpStatus.BAD_REQUEST,
            code = "VALIDATION_ERROR",
            message = "Validation failed",
            request = request,
            details = "Malformed request body or unsupported field in JSON"
        )
    }

    @ExceptionHandler(AppBusinessException::class)
    fun handleBusinessException(ex: AppBusinessException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        logger.warn("Business exception: {}", ex.message)
        return buildErrorResponse(
            status = HttpStatus.BAD_REQUEST,
            code = ex.code,
            message = ex.message,
            request = request,
            field = ex.field,
            details = ex.details
        )
    }

    @ExceptionHandler(DomainBusinessException::class)
    fun handleDomainBusinessException(ex: DomainBusinessException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        logger.warn("Business exception: {}", ex.message)
        return buildErrorResponse(
            status = HttpStatus.BAD_REQUEST,
            code = "BUSINESS_RULE_VIOLATION",
            message = ex.message ?: "Business rule violation",
            request = request
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        logger.error("Unexpected error: {}", ex.message, ex)
        return buildErrorResponse(
            status = HttpStatus.INTERNAL_SERVER_ERROR,
            code = "INTERNAL_SERVER_ERROR",
            message = "An unexpected error occurred",
            request = request
        )
    }

    private fun buildErrorResponse(
        status: HttpStatus,
        code: String,
        message: String,
        request: HttpServletRequest,
        field: String? = null,
        details: String? = null
    ): ResponseEntity<ErrorResponse> {
        val response = ErrorResponse(
            status = status.value(),
            code = code,
            message = message,
            field = field,
            details = details,
            path = request.requestURI
        )
        return ResponseEntity.status(status).body(response)
    }
}
