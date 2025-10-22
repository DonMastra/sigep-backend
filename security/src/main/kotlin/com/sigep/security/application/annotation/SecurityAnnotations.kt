package com.sigep.security.application.annotation

import org.springframework.security.access.prepost.PreAuthorize

/**
 * Annotation for endpoints that require ADMIN role
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@PreAuthorize("hasRole('ADMIN')")
annotation class RequireAdmin

/**
 * Annotation for endpoints that require TEACHER role
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@PreAuthorize("hasRole('TEACHER')")
annotation class RequireTeacher

/**
 * Annotation for endpoints that require GUARDIAN role
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@PreAuthorize("hasRole('GUARDIAN')")
annotation class RequireGuardian

/**
 * Annotation for endpoints accessible by ADMIN or TEACHER
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
annotation class RequireAdminOrTeacher

/**
 * Annotation for endpoints accessible by ADMIN, TEACHER or GUARDIAN
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'GUARDIAN')")
annotation class RequireStaffOrGuardian

/**
 * Annotation for endpoints that allow access to own resources
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'GUARDIAN') or @securityService.isOwnResource(authentication, #id)")
annotation class RequireOwnershipOrStaff

