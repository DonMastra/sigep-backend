package com.sigep.guardians.infrastructure.config

import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@Configuration
@ComponentScan(basePackages = ["com.sigep.guardians"])
@EnableJpaRepositories(basePackages = ["com.sigep.guardians.domain.repository"])
@EntityScan(basePackages = ["com.sigep.guardians.domain.model"])
class GuardiansModuleConfig
