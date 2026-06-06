package com.sigep.scheduling.infrastructure.config

import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@Configuration
@ComponentScan(basePackages = ["com.sigep.scheduling"])
@EnableJpaRepositories(basePackages = ["com.sigep.scheduling.domain.repository"])
@EntityScan(basePackages = ["com.sigep.scheduling.domain.model"])
@EnableCaching
class SchedulingModuleConfig
