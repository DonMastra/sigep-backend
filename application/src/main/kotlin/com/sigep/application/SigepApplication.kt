package com.sigep.application

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.runApplication
import org.springframework.cache.annotation.EnableCaching
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication(scanBasePackages = ["com.sigep"])
@EnableJpaRepositories(basePackages = [
    "com.sigep.students.domain.repository",
    "com.sigep.courses.domain.repository",
    "com.sigep.staff.infrastructure.repository",
    "com.sigep.security.domain.repository",
    "com.sigep.security.infrastructure.repository"
])
@EntityScan(basePackages = [
    "com.sigep.students.domain.model",
    "com.sigep.courses.domain.model",
    "com.sigep.staff.domain.model",
    "com.sigep.security.domain.model",
    "com.sigep.scheduling.domain.model"
])
@EnableCaching
@EnableScheduling
class SigepApplication

fun main(args: Array<String>) {
	runApplication<SigepApplication>(*args)
}
