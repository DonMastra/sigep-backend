package com.sigep.common.infrastructure.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

/**
 * Configuración de Jackson para soporte de tipos Java 8 date/time
 *
 * Registra explícitamente JavaTimeModule para soportar:
 * - LocalDate
 * - LocalDateTime
 * - LocalTime
 * - Instant
 * - ZonedDateTime
 * - etc.
 */
@Configuration
class JacksonConfig {

    @Bean
    @Primary
    fun objectMapper(): ObjectMapper {
        val mapper = ObjectMapper()

        // Registrar módulo para tipos Java 8 date/time
        mapper.registerModule(JavaTimeModule())

        // Configurar para NO usar timestamps (usar formato ISO-8601)
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

        return mapper
    }
}

