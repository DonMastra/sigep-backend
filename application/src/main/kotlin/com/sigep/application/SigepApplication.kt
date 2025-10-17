package com.sigep.application

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.runApplication
import org.springframework.cache.annotation.EnableCaching
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@SpringBootApplication(scanBasePackages = ["com.sigep"])
@EnableJpaRepositories(basePackages = ["com.sigep.*.domain.repository"])
@EntityScan(basePackages = ["com.sigep.*.domain.model"])
@EnableCaching
class SigepApplication

fun main(args: Array<String>) {
	runApplication<SigepApplication>(*args)
}

