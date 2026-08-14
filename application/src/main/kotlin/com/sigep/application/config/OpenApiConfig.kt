package com.sigep.application.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun openAPI(): OpenAPI {
        return OpenAPI()
            .info(
                Info()
                    .title("SiGEP API - Sistema de Gestión de Enseñanza Privada")
                    .description("""
                        API REST para la gestión integral de un instituto educativo de inglés.
                        
                        Módulos disponibles:
                        - **Students**: Gestión de estudiantes
                        - **Courses**: Gestión de cursos e inscripciones
                        - **Staff**: Gestión de personal docente y no docente
                        - **Security**: Autenticación y autorización
                        
                        Para usar la API, primero debes autenticarte en /api/v1/auth/login
                    """.trimIndent())
                    .version("1.0.0")
                    .contact(
                        Contact()
                            .name("SiGEP Team")
                            .email("support@sigep.edu.mx")
                    )
                    .license(
                        License()
                            .name("Private License")
                    )
            )
            .addSecurityItem(SecurityRequirement().addList("Bearer Authentication"))
            .components(
                Components()
                    .addSecuritySchemes(
                        "Bearer Authentication",
                        SecurityScheme()
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("JWT")
                            .description("Enter JWT token")
                    )
            )
    }
}

