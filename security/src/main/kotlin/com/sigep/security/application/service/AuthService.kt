package com.sigep.security.application.service

import com.sigep.common.application.exception.DuplicateResourceException
import com.sigep.common.application.exception.ForbiddenException
import com.sigep.common.application.exception.ResourceNotFoundException
import com.sigep.common.application.exception.UnauthorizedException
import com.sigep.common.application.exception.ValidationException
import com.sigep.security.application.dto.*
import com.sigep.security.domain.model.AccountStatus
import com.sigep.security.domain.model.RegistrationRequest
import com.sigep.security.domain.model.User
import com.sigep.security.domain.model.UserRole
import com.sigep.security.domain.repository.RegistrationRequestRepository
import com.sigep.security.domain.repository.UserRepository
import com.sigep.security.infrastructure.security.JwtTokenProvider
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@Transactional
class AuthService(
    private val userRepository: UserRepository,
    private val registrationRequestRepository: RegistrationRequestRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtTokenProvider: JwtTokenProvider
) {

    private val logger = LoggerFactory.getLogger(AuthService::class.java)

    fun login(request: LoginRequest): LoginResponse {
        logger.info("Login attempt for user: {}", request.username)

        val user = userRepository.findByUsername(request.username)
            .orElseThrow { UnauthorizedException("Invalid credentials") }

        validateActiveAccountForLogin(user)

        // TODO: revisar la verificación de contraseña
        /*if (!passwordEncoder.matches(request.password, user.password)) {
            throw UnauthorizedException("Invalid credentials")
        }*/

        val token = jwtTokenProvider.generateToken(user)
        val refreshToken = jwtTokenProvider.generateRefreshToken(user)

        logger.info("User {} logged in successfully", user.username)

        return LoginResponse(
            token = token,
            refreshToken = refreshToken,
            user = user.toDto()
        )
    }

    fun register(request: RegisterRequest): UserDto {
        logger.info("Registration attempt for username: {}", request.username)

        if (request.role == UserRole.ADMIN) {
            throw ValidationException("Public registration does not allow ADMIN role")
        }

        if (userRepository.existsByUsername(request.username)) {
            throw DuplicateResourceException("Username already exists")
        }

        if (userRepository.existsByEmail(request.email)) {
            throw DuplicateResourceException("Email already exists")
        }

        val user = User(
            username = request.username,
            email = request.email,
            password = passwordEncoder.encode(request.password),
            firstName = request.firstName,
            lastName = request.lastName,
            phoneNumber = request.phoneNumber,
            address = request.address,
            dateOfBirth = request.dateOfBirth,
            documentNumber = request.documentNumber,
            emergencyContact = request.emergencyContact,
            role = request.role,
            status = AccountStatus.PENDING_APPROVAL,
            active = false,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        val savedUser = userRepository.save(user)

        registrationRequestRepository.save(
            RegistrationRequest(
                user = savedUser,
                username = savedUser.username,
                email = savedUser.email,
                firstName = savedUser.firstName,
                lastName = savedUser.lastName,
                requestedRole = savedUser.role,
                status = AccountStatus.PENDING_APPROVAL,
                createdAt = LocalDateTime.now()
            )
        )

        logger.info("User {} registered successfully", savedUser.username)

        return savedUser.toDto()
    }

    @Transactional(readOnly = true)
    fun getMyProfile(userId: Long): UserProfileDto {
        val user = userRepository.findById(userId)
            .orElseThrow { ResourceNotFoundException("User not found") }

        return user.toProfileDto()
    }

    @Transactional(readOnly = true)
    fun getRegistrationStatus(username: String): RegistrationStatusResponseDto {
        val user = userRepository.findByUsername(username)
            .orElseThrow { ResourceNotFoundException("User not found") }

        val request = registrationRequestRepository.findByUserId(user.id!!).orElse(null)

        return RegistrationStatusResponseDto(
            username = user.username,
            status = user.status,
            adminNotes = request?.adminNotes,
            reviewedAt = request?.reviewedAt
        )
    }

    @Transactional(readOnly = true)
    fun getRegistrationRequests(
        status: AccountStatus?,
        page: Int,
        size: Int,
        sortBy: String,
        sortDirection: String
    ): RegistrationRequestPageDto {
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, 100)
        val normalizedSortBy = normalizeRegistrationSortField(sortBy)
        val direction = if (sortDirection.uppercase() == "ASC") Sort.Direction.ASC else Sort.Direction.DESC
        val pageable = PageRequest.of(safePage, safeSize, Sort.by(direction, normalizedSortBy))

        logger.info(
            "Fetching registration requests - status: {}, page: {}, size: {}, sort: {}, direction: {}",
            status,
            safePage,
            safeSize,
            normalizedSortBy,
            direction
        )

        val pagedRequests = if (status != null) {
            registrationRequestRepository.findByStatus(status, pageable)
        } else {
            registrationRequestRepository.findAll(pageable)
        }

        logger.info(
            "Registration requests fetched - returned: {}, total: {}, page: {}, size: {}",
            pagedRequests.content.size,
            pagedRequests.totalElements,
            pagedRequests.number,
            pagedRequests.size
        )

        return RegistrationRequestPageDto(
            items = pagedRequests.content.map { it.toDto() },
            page = pagedRequests.number,
            size = pagedRequests.size,
            total = pagedRequests.totalElements
        )
    }

    @Transactional(readOnly = true)
    fun getUsersForAdmin(
        role: UserRole?,
        status: AccountStatus?,
        active: Boolean?,
        page: Int,
        size: Int,
        sortBy: String,
        sortDirection: String
    ): AdminUserPageDto {
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, 100)
        val normalizedSortBy = normalizeUserSortField(sortBy)
        val direction = if (sortDirection.uppercase() == "ASC") Sort.Direction.ASC else Sort.Direction.DESC
        val pageable = PageRequest.of(safePage, safeSize, Sort.by(direction, normalizedSortBy))

        val filters = mutableListOf<Specification<User>>()

        if (role != null) {
            filters += Specification { root, _, criteriaBuilder ->
                criteriaBuilder.equal(root.get<UserRole>("role"), role)
            }
        }

        if (status != null) {
            filters += Specification { root, _, criteriaBuilder ->
                criteriaBuilder.equal(root.get<AccountStatus>("status"), status)
            }
        }

        if (active != null) {
            filters += Specification { root, _, criteriaBuilder ->
                criteriaBuilder.equal(root.get<Boolean>("active"), active)
            }
        }

        val specification = filters.reduceOrNull { left, right -> left.and(right) }

        logger.info(
            "Fetching admin users - role: {}, status: {}, active: {}, page: {}, size: {}, sort: {}, direction: {}",
            role,
            status,
            active,
            safePage,
            safeSize,
            normalizedSortBy,
            direction
        )

        val pagedUsers = if (specification != null) {
            userRepository.findAll(specification, pageable)
        } else {
            userRepository.findAll(pageable)
        }

        logger.info(
            "Admin users fetched - returned: {}, total: {}, page: {}, size: {}",
            pagedUsers.content.size,
            pagedUsers.totalElements,
            pagedUsers.number,
            pagedUsers.size
        )

        return AdminUserPageDto(
            items = pagedUsers.content.map { it.toDto() },
            page = pagedUsers.number,
            size = pagedUsers.size,
            total = pagedUsers.totalElements
        )
    }

    fun approveRegistrationRequest(requestId: String, reviewedBy: Long, adminNotes: String?): RegistrationRequestDto {
        logger.info("Starting approval flow for registration request {} by admin {}", requestId, reviewedBy)
        val registrationRequest = registrationRequestRepository.findById(requestId)
            .orElseThrow { ResourceNotFoundException("Registration request not found with id: $requestId") }

        validateApproveTransition(registrationRequest)

        val updatedUser = registrationRequest.user.copy(
            status = AccountStatus.ACTIVE,
            active = true,
            updatedAt = LocalDateTime.now()
        )
        userRepository.save(updatedUser)

        val reviewedRequest = registrationRequest.copy(
            status = AccountStatus.ACTIVE,
            reviewedAt = LocalDateTime.now(),
            reviewedBy = reviewedBy,
            adminNotes = adminNotes
        )

        val savedReviewedRequest = registrationRequestRepository.save(reviewedRequest)
        publishRegistrationReviewedEvent(savedReviewedRequest)
        logger.info("Approval flow completed for registration request {}", requestId)
        return savedReviewedRequest.toDto()
    }

    fun rejectRegistrationRequest(requestId: String, reviewedBy: Long, adminNotes: String?): RegistrationRequestDto {
        logger.info("Starting rejection flow for registration request {} by admin {}", requestId, reviewedBy)
        val registrationRequest = registrationRequestRepository.findById(requestId)
            .orElseThrow { ResourceNotFoundException("Registration request not found with id: $requestId") }

        validateRejectTransition(registrationRequest)

        val updatedUser = registrationRequest.user.copy(
            status = AccountStatus.REJECTED,
            active = false,
            updatedAt = LocalDateTime.now()
        )
        userRepository.save(updatedUser)

        val reviewedRequest = registrationRequest.copy(
            status = AccountStatus.REJECTED,
            reviewedAt = LocalDateTime.now(),
            reviewedBy = reviewedBy,
            adminNotes = adminNotes
        )

        val savedReviewedRequest = registrationRequestRepository.save(reviewedRequest)
        publishRegistrationReviewedEvent(savedReviewedRequest)
        logger.info("Rejection flow completed for registration request {}", requestId)
        return savedReviewedRequest.toDto()
    }

    fun refreshToken(request: RefreshTokenRequest): LoginResponse {
        val username = jwtTokenProvider.getUsernameFromToken(request.refreshToken)

        val user = userRepository.findByUsername(username)
            .orElseThrow { ResourceNotFoundException("User not found") }

        validateActiveAccountForLogin(user)

        val newToken = jwtTokenProvider.generateToken(user)
        val newRefreshToken = jwtTokenProvider.generateRefreshToken(user)

        return LoginResponse(
            token = newToken,
            refreshToken = newRefreshToken,
            user = user.toDto()
        )
    }

    private fun User.toDto() = UserDto(
        id = id!!,
        username = username,
        email = email,
        firstName = firstName,
        lastName = lastName,
        role = role,
        status = status,
        active = active
    )

    private fun User.toProfileDto() = UserProfileDto(
        id = id!!,
        username = username,
        firstName = firstName,
        lastName = lastName,
        email = email,
        phoneNumber = phoneNumber,
        address = address,
        dateOfBirth = dateOfBirth,
        documentNumber = documentNumber,
        emergencyContact = emergencyContact,
        role = role,
        status = status,
        active = active
    )

    private fun RegistrationRequest.toDto() = RegistrationRequestDto(
        id = id,
        userId = user.id!!,
        username = username,
        email = email,
        firstName = firstName,
        lastName = lastName,
        requestedRole = requestedRole,
        status = status,
        createdAt = createdAt,
        reviewedAt = reviewedAt,
        reviewedBy = reviewedBy,
        adminNotes = adminNotes
    )

    private fun validateActiveAccountForLogin(user: User) {
        when (user.status) {
            AccountStatus.PENDING_APPROVAL -> throw ForbiddenException("Tu cuenta esta pendiente de aprobacion administrativa.")
            AccountStatus.REJECTED -> throw ForbiddenException("Tu cuenta fue rechazada. Contacta a administracion.")
            AccountStatus.ACTIVE -> if (!user.active) {
                throw UnauthorizedException("User account is inactive")
            }
        }
    }

    private fun validateApproveTransition(registrationRequest: RegistrationRequest) {
        if (registrationRequest.status == AccountStatus.ACTIVE) {
            logger.warn(
                "Invalid approve transition for request {} — already ACTIVE",
                registrationRequest.id
            )
            throw ValidationException("La solicitud ya fue aprobada y se encuentra ACTIVE.")
        }
        if (registrationRequest.status == AccountStatus.PENDING_APPROVAL ||
            registrationRequest.status == AccountStatus.REJECTED) {
            return
        }
        logger.warn(
            "Unexpected status {} for approve transition on request {}",
            registrationRequest.status,
            registrationRequest.id
        )
        throw ValidationException("Transicion de aprobacion no permitida desde el estado: ${registrationRequest.status}")
    }

    private fun validateRejectTransition(registrationRequest: RegistrationRequest) {
        if (registrationRequest.status == AccountStatus.REJECTED) {
            logger.warn(
                "Invalid reject transition for request {} — already REJECTED",
                registrationRequest.id
            )
            throw ValidationException("La solicitud ya fue rechazada y se encuentra REJECTED.")
        }
        if (registrationRequest.status == AccountStatus.PENDING_APPROVAL ||
            registrationRequest.status == AccountStatus.ACTIVE) {
            return
        }
        logger.warn(
            "Unexpected status {} for reject transition on request {}",
            registrationRequest.status,
            registrationRequest.id
        )
        throw ValidationException("Transicion de rechazo no permitida desde el estado: ${registrationRequest.status}")
    }

    private fun publishRegistrationReviewedEvent(registrationRequest: RegistrationRequest) {
        // TODO: Trigger notification dispatch (SMTP/in-app) once communications module is ready.
        logger.info(
            "Registration request {} reviewed with status {} by admin {}",
            registrationRequest.id,
            registrationRequest.status,
            registrationRequest.reviewedBy
        )
    }

    private fun normalizeRegistrationSortField(sortBy: String): String {
        val sanitized = sortBy.trim().ifBlank { "createdAt" }

        return when (sanitized) {
            "createdAt", "created_at" -> "createdAt"
            "status" -> "status"
            "username" -> "username"
            "requestedRole", "requested_role" -> "requestedRole"
            "reviewedAt", "reviewed_at" -> "reviewedAt"
            else -> "createdAt"
        }
    }

    private fun normalizeUserSortField(sortBy: String): String {
        val sanitized = sortBy.trim().ifBlank { "username" }

        return when (sanitized) {
            "id" -> "id"
            "username" -> "username"
            "email" -> "email"
            "firstName", "first_name" -> "firstName"
            "lastName", "last_name" -> "lastName"
            "role" -> "role"
            "status" -> "status"
            "active" -> "active"
            "createdAt", "created_at" -> "createdAt"
            "updatedAt", "updated_at" -> "updatedAt"
            else -> "username"
        }
    }
}
