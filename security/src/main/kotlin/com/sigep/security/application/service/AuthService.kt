package com.sigep.security.application.service

import com.sigep.common.application.exception.DuplicateResourceException
import com.sigep.common.application.exception.ForbiddenException
import com.sigep.common.application.exception.ResourceNotFoundException
import com.sigep.common.application.exception.ResourceConflictException
import com.sigep.common.application.exception.UnauthorizedException
import com.sigep.common.application.exception.ValidationException
import com.sigep.common.application.service.GuardianClientProfileProvisioner
import com.sigep.security.application.dto.*
import com.sigep.security.domain.model.AccountStatus
import com.sigep.security.domain.model.RegistrationRequest
import com.sigep.security.domain.model.GuardianInvitation
import com.sigep.security.domain.model.User
import com.sigep.security.domain.model.UserRole
import com.sigep.security.domain.model.UserRoleContextEventType
import com.sigep.security.domain.repository.RegistrationRequestRepository
import com.sigep.security.domain.repository.GuardianInvitationRepository
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
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

@Service
@Transactional
class AuthService(
    private val userRepository: UserRepository,
    private val registrationRequestRepository: RegistrationRequestRepository,
    private val guardianInvitationRepository: GuardianInvitationRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtTokenProvider: JwtTokenProvider,
    private val roleAssignmentService: UserRoleAssignmentService,
    private val guardianClientProfileProvisioners: List<GuardianClientProfileProvisioner> = emptyList()
) {

    private val logger = LoggerFactory.getLogger(AuthService::class.java)

    fun login(request: LoginRequest): LoginResponse {
        logger.info("Login attempt received")

        val user = userRepository.findByUsername(request.username)
            .orElseThrow { UnauthorizedException("Invalid credentials") }

        validateActiveAccountForLogin(user)

        if (!passwordEncoder.matches(request.password, user.password)) {
            throw UnauthorizedException("Invalid credentials")
        }

        val roles = roleAssignmentService.ensureLegacyAssignmentIfMissing(user)
        if (roles.isEmpty()) {
            throw ForbiddenException("The account has no active role assignments", "NO_ACTIVE_ROLE_ASSIGNMENTS")
        }

        if (roles.size > 1) {
            logger.info("Login requires role selection for user id {}", user.id)
            return LoginResponse(
                roleSelectionRequired = true,
                roleSelectionToken = jwtTokenProvider.generateRoleSelectionToken(user),
                availableRoles = roles
            )
        }

        logger.info("Login completed successfully for user id {}", user.id)
        return issueSession(user, roles.single(), eventType = UserRoleContextEventType.LOGIN)
    }

    fun selectRole(request: RoleSelectionRequest): LoginResponse {
        if (!jwtTokenProvider.validateToken(request.roleSelectionToken) ||
            !jwtTokenProvider.isRoleSelectionToken(request.roleSelectionToken)
        ) {
            throw UnauthorizedException("Role selection token is invalid or expired", "ROLE_SELECTION_TOKEN_INVALID")
        }
        val user = userRepository.findByUsername(jwtTokenProvider.getUsernameFromToken(request.roleSelectionToken))
            .orElseThrow { ResourceNotFoundException("User not found") }
        validateActiveAccountForLogin(user)
        requireAssignedRole(user, request.activeRole)
        return issueSession(user, request.activeRole, eventType = UserRoleContextEventType.LOGIN_SELECTION)
    }

    fun switchRole(userId: Long, currentRole: String?, request: RoleContextRequest): LoginResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { ResourceNotFoundException("User not found") }
        validateActiveAccountForLogin(user)
        requireAssignedRole(user, request.activeRole)

        val previousRole = currentRole?.let { runCatching { UserRole.valueOf(it) }.getOrNull() }
        if (request.activeRole == UserRole.ADMIN && previousRole != UserRole.ADMIN) {
            if (request.currentPassword.isNullOrBlank() || !passwordEncoder.matches(request.currentPassword, user.password)) {
                throw ValidationException(
                    message = "La contrasena actual no es correcta",
                    code = "ADMIN_ROLE_REAUTHENTICATION_FAILED",
                    field = "currentPassword"
                )
            }
        }

        return issueSession(
            user,
            request.activeRole,
            previousRole = previousRole,
            eventType = UserRoleContextEventType.SWITCH
        )
    }

    fun register(request: RegisterRequest): UserDto {
        logger.info("Public registration attempt received")

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
        roleAssignmentService.ensureAssignment(savedUser, request.role)

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

        if (savedUser.role == UserRole.GUARDIAN) {
            guardianClientProfileProvisioners.forEach {
                it.provisionGuardianClient(savedUser.id!!)
            }
        }

        logger.info("Public registration completed for user id {}", savedUser.id)

        return savedUser.toDto()
    }

    fun createGuardianByAdmin(
        request: AdminCreateGuardianRequest,
        createdBy: Long
    ): AdminCreateGuardianResponse {
        val username = request.username.trim()
        val email = request.email.trim().lowercase()
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw DuplicateResourceException("Username already exists", "USERNAME_ALREADY_EXISTS")
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw DuplicateResourceException("Email already exists", "GUARDIAN_EMAIL_ALREADY_EXISTS")
        }
        if (request.activationMode == AdminGuardianActivationMode.ACTIVE && request.initialPassword.isNullOrBlank()) {
            throw ValidationException(
                message = "initialPassword is required for ACTIVE guardian creation",
                code = "INITIAL_PASSWORD_REQUIRED",
                field = "initialPassword"
            )
        }

        val now = LocalDateTime.now()
        val invitationToken = if (request.activationMode == AdminGuardianActivationMode.INVITE) secureToken() else null
        val rawPassword = request.initialPassword ?: secureToken()
        val user = userRepository.save(
            User(
                username = username,
                email = email,
                password = passwordEncoder.encode(rawPassword),
                firstName = request.firstName.trim(),
                lastName = request.lastName.trim(),
                phoneNumber = request.phoneNumber?.trim(),
                address = request.address?.trim(),
                dateOfBirth = request.dateOfBirth,
                documentNumber = request.documentNumber?.trim(),
                emergencyContact = request.emergencyContact?.trim(),
                role = UserRole.GUARDIAN,
                status = if (request.activationMode == AdminGuardianActivationMode.ACTIVE) AccountStatus.ACTIVE else AccountStatus.PENDING_APPROVAL,
                active = request.activationMode == AdminGuardianActivationMode.ACTIVE,
                createdAt = now,
                updatedAt = now
            )
        )
        roleAssignmentService.ensureAssignment(user, UserRole.GUARDIAN, createdBy)

        guardianClientProfileProvisioners.forEach {
            it.provisionGuardianClient(user.id!!, createdBy)
        }

        val invitation = invitationToken?.let { token ->
            guardianInvitationRepository.save(
                GuardianInvitation(
                    user = user,
                    tokenHash = hashToken(token),
                    expiresAt = now.plusHours(48),
                    createdBy = createdBy,
                    createdAt = now
                )
            )
        }
        return AdminCreateGuardianResponse(
            user = user.toDto(),
            activationMode = request.activationMode,
            invitationToken = invitationToken,
            invitationExpiresAt = invitation?.expiresAt
        )
    }

    fun acceptGuardianInvitation(request: AcceptGuardianInvitationRequest): UserDto {
        val invitation = guardianInvitationRepository.findByTokenHash(hashToken(request.token.trim()))
            .orElseThrow { ValidationException("Invitation is invalid", code = "GUARDIAN_INVITATION_INVALID") }
        val now = LocalDateTime.now()
        if (invitation.acceptedAt != null) {
            throw ResourceConflictException("Invitation was already accepted", "GUARDIAN_INVITATION_ALREADY_ACCEPTED")
        }
        if (!invitation.expiresAt.isAfter(now)) {
            throw ValidationException("Invitation has expired", code = "GUARDIAN_INVITATION_EXPIRED")
        }

        val activated = userRepository.save(
            invitation.user.copy(
                password = passwordEncoder.encode(request.password),
                status = AccountStatus.ACTIVE,
                active = true,
                updatedAt = now
            )
        )
        guardianInvitationRepository.save(invitation.copy(acceptedAt = now))
        return activated.toDto()
    }

    @Transactional(readOnly = true)
    fun getMyProfile(userId: Long, activeRole: UserRole? = null): UserProfileDto {
        val user = userRepository.findById(userId)
            .orElseThrow { ResourceNotFoundException("User not found") }

        return user.toProfileDto(activeRole ?: user.role)
    }

    fun changePassword(userId: Long, request: ChangePasswordRequest): UserDto {
        val user = userRepository.findById(userId)
            .orElseThrow { ResourceNotFoundException("User not found") }

        if (!passwordEncoder.matches(request.currentPassword, user.password)) {
            throw ValidationException(
                message = "La contrasena actual no es correcta",
                code = "CURRENT_PASSWORD_INVALID",
                field = "currentPassword"
            )
        }

        if (passwordEncoder.matches(request.newPassword, user.password)) {
            throw ValidationException(
                message = "La nueva contrasena debe ser diferente de la actual",
                code = "PASSWORD_UNCHANGED",
                field = "newPassword"
            )
        }

        val now = LocalDateTime.now()
        val updatedUser = userRepository.save(
            user.copy(
                password = passwordEncoder.encode(request.newPassword),
                mustChangePassword = false,
                passwordChangedAt = now,
                updatedAt = now
            )
        )

        logger.info("Password changed successfully for user id {}", userId)
        return updatedUser.toDto()
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
            val activeRoleUserIds = roleAssignmentService.activeUserIdsByRole(role)
            val usersWithAssignments = roleAssignmentService.assignedUserIds()
            filters += Specification { root, _, criteriaBuilder ->
                val assignedRole = root.get<Long>("id").`in`(activeRoleUserIds)
                val legacyRole = criteriaBuilder.and(
                    criteriaBuilder.equal(root.get<UserRole>("role"), role),
                    criteriaBuilder.not(root.get<Long>("id").`in`(usersWithAssignments))
                )
                criteriaBuilder.or(assignedRole, legacyRole)
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
        if (!jwtTokenProvider.validateToken(request.refreshToken) || !jwtTokenProvider.isRefreshToken(request.refreshToken)) {
            throw UnauthorizedException("Refresh token is invalid or expired", "REFRESH_TOKEN_INVALID")
        }
        val username = jwtTokenProvider.getUsernameFromToken(request.refreshToken)

        val user = userRepository.findByUsername(username)
            .orElseThrow { ResourceNotFoundException("User not found") }

        validateActiveAccountForLogin(user)
        val activeRole = jwtTokenProvider.getRoleFromTokenOrNull(request.refreshToken)
            ?.let { runCatching { UserRole.valueOf(it) }.getOrNull() }
            ?: user.role
        requireAssignedRole(user, activeRole)
        return issueSession(user, activeRole)
    }

    @Transactional(readOnly = true)
    fun getUserRoleAssignments(userId: Long): UserRoleAssignmentsDto =
        UserRoleAssignmentsDto(userId, roleAssignmentService.activeRoles(userId))

    fun grantUserRole(userId: Long, role: UserRole, assignedBy: Long): UserRoleAssignmentsDto {
        val roles = roleAssignmentService.grantRole(userId, role, assignedBy)
        if (role == UserRole.GUARDIAN) {
            guardianClientProfileProvisioners.forEach { it.provisionGuardianClient(userId, assignedBy) }
        }
        return UserRoleAssignmentsDto(userId, roles)
    }

    fun revokeUserRole(userId: Long, role: UserRole, revokedBy: Long): UserRoleAssignmentsDto =
        UserRoleAssignmentsDto(userId, roleAssignmentService.revokeRole(userId, role, revokedBy))

    private fun User.toDto(activeRole: UserRole = role): UserDto {
        val assignedRoles = roleAssignmentService.activeRoles(this)
        return UserDto(
        id = id!!,
        username = username,
        email = email,
        firstName = firstName,
        lastName = lastName,
        role = activeRole,
        roles = assignedRoles,
        activeRole = activeRole,
        status = status,
        active = active,
        mustChangePassword = mustChangePassword
        )
    }

    private fun secureToken(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun hashToken(token: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun User.toProfileDto(activeRole: UserRole = role) = UserProfileDto(
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
        role = activeRole,
        roles = roleAssignmentService.activeRoles(this),
        activeRole = activeRole,
        status = status,
        active = active,
        mustChangePassword = mustChangePassword
    )

    private fun issueSession(
        user: User,
        activeRole: UserRole,
        previousRole: UserRole? = null,
        eventType: UserRoleContextEventType? = null
    ): LoginResponse {
        val roles = requireAssignedRole(user, activeRole)
        eventType?.let {
            roleAssignmentService.recordContext(user.id!!, previousRole, activeRole, it)
        }
        return LoginResponse(
            token = jwtTokenProvider.generateToken(user, activeRole),
            refreshToken = jwtTokenProvider.generateRefreshToken(user, activeRole),
            user = user.toDto(activeRole),
            availableRoles = roles
        )
    }

    private fun requireAssignedRole(user: User, activeRole: UserRole): List<UserRole> {
        val roles = roleAssignmentService.activeRoles(user)
        if (activeRole !in roles) {
            throw ForbiddenException(
                message = "The requested role is not assigned to the user",
                code = "ROLE_NOT_ASSIGNED"
            )
        }
        return roles
    }

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
