package com.sigep.security.application.service

import org.springframework.security.core.Authentication
import org.springframework.stereotype.Service

@Service("securityService")
class SecurityService {

    fun isOwnResource(authentication: Authentication, resourceId: Long): Boolean {
        val userId = authentication.details as? Map<*, *>
        val userIdFromToken = userId?.get("userId") as? Long

        return userIdFromToken == resourceId
    }

    fun canAccessStudent(authentication: Authentication, studentId: Long): Boolean {
        val authorities = authentication.authorities.map { it.authority }

        // Admin and teachers can access all students
        if (authorities.any { it == "ROLE_ADMIN" || it == "ROLE_TEACHER" }) {
            return true
        }

        // Guardians can only access their assigned students
        if (authorities.any { it == "ROLE_GUARDIAN" }) {
            // TODO: Implement logic to check if guardian is assigned to this student
            return true
        }

        return false
    }

    /**
     * Check if user has permission to modify enrollment
     */
    fun canModifyEnrollment(authentication: Authentication, enrollmentId: Long): Boolean {
        val authorities = authentication.authorities.map { it.authority }
        return authorities.any { it == "ROLE_ADMIN" || it == "ROLE_TEACHER" }
    }
}

