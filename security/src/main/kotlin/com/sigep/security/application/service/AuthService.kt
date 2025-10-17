package com.sigep.security.application.service

import com.sigep.common.application.exception.DuplicateResourceException
import com.sigep.common.application.exception.ResourceNotFoundException
import com.sigep.common.application.exception.UnauthorizedException
import com.sigep.security.application.dto.*
import com.sigep.security.domain.model.User
import com.sigep.security.domain.repository.UserRepository
import com.sigep.security.infrastructure.security.JwtTokenProvider
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@Transactional
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtTokenProvider: JwtTokenProvider
) {

    private val logger = LoggerFactory.getLogger(AuthService::class.java)

    fun login(request: LoginRequest): LoginResponse {
        logger.info("Login attempt for user: {}", request.username)

        val user = userRepository.findByUsername(request.username)
            .orElseThrow { UnauthorizedException("Invalid credentials") }

        if (!user.active) {
            throw UnauthorizedException("User account is inactive")
        }

        if (!passwordEncoder.matches(request.password, user.password)) {
            throw UnauthorizedException("Invalid credentials")
        }

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
            role = request.role,
            active = true,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        val savedUser = userRepository.save(user)
        logger.info("User {} registered successfully", savedUser.username)

        return savedUser.toDto()
    }

    fun refreshToken(request: RefreshTokenRequest): LoginResponse {
        val username = jwtTokenProvider.getUsernameFromToken(request.refreshToken)

        val user = userRepository.findByUsername(username)
            .orElseThrow { ResourceNotFoundException("User not found") }

        if (!user.active) {
            throw UnauthorizedException("User account is inactive")
        }

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
        active = active
    )
}
