package com.sigep.payments.infrastructure.config

import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@Configuration
@ComponentScan(basePackages = ["com.sigep.payments"])
@EnableJpaRepositories(basePackages = ["com.sigep.payments.domain.repository"])
@EntityScan(basePackages = ["com.sigep.payments.domain.model"])
class PaymentsModuleConfig
