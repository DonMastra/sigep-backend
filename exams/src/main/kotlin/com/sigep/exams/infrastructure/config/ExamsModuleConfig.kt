package com.sigep.exams.infrastructure.config

import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@Configuration
@ComponentScan(basePackages = ["com.sigep.exams"])
@EnableJpaRepositories(basePackages = ["com.sigep.exams.domain.repository"])
@EntityScan(basePackages = ["com.sigep.exams.domain.model"])
@EnableCaching
class ExamsModuleConfig

