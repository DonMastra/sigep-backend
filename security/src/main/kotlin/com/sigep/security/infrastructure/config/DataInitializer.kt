package com.sigep.security.infrastructure.config

import com.sigep.security.domain.model.User
import com.sigep.security.domain.model.UserRole
import com.sigep.security.domain.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.crypto.password.PasswordEncoder

@Configuration
@Profile("dev")
class DataInitializer {

    companion object {
        private val log = LoggerFactory.getLogger(DataInitializer::class.java)
    }

    @Bean
    fun initTestUsers(
        userRepository: UserRepository,
        passwordEncoder: PasswordEncoder
    ) = CommandLineRunner {
        if (userRepository.count() == 0L) {
            log.info("Initializing test users for development environment...")

            val testUsers = listOf(
                User(
                    username = "admin",
                    email = "admin@sigep.edu.mx",
                    password = passwordEncoder.encode("password123"),
                    firstName = "Admin",
                    lastName = "Sistema",
                    role = UserRole.ADMIN
                ),
                User(
                    username = "teacher",
                    email = "teacher@sigep.edu.mx",
                    password = passwordEncoder.encode("password123"),
                    firstName = "Juan",
                    lastName = "Profesor",
                    role = UserRole.TEACHER
                ),
                User(
                    username = "guardian",
                    email = "guardian@sigep.edu.mx",
                    password = passwordEncoder.encode("password123"),
                    firstName = "Pedro",
                    lastName = "Responsable",
                    role = UserRole.GUARDIAN
                )
            )

            userRepository.saveAll(testUsers)
            log.info("Created {} development-only test users", testUsers.size)
        } else {
            log.info("Users already exist in database, skipping initialization")
        }
    }
}

