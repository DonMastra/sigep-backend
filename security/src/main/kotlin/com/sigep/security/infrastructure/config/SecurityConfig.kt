package com.sigep.security.infrastructure.config

import com.sigep.security.infrastructure.ratelimit.RateLimitFilter
import com.sigep.security.infrastructure.security.CustomAccessDeniedHandler
import com.sigep.security.infrastructure.security.CustomAuthenticationEntryPoint
import com.sigep.security.infrastructure.security.JwtAuthenticationFilter
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true, jsr250Enabled = true)
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val rateLimitFilter: RateLimitFilter,
    private val customAuthenticationEntryPoint: CustomAuthenticationEntryPoint,
    private val customAccessDeniedHandler: CustomAccessDeniedHandler,
    @Value("\${app.cors.allowed-origins:http://localhost:4200}")
    private val allowedOrigins: String,
    @Value("\${app.cors.allowed-origin-patterns:}")
    private val allowedOriginPatterns: String
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .exceptionHandling { exceptions ->
                exceptions
                    .authenticationEntryPoint(customAuthenticationEntryPoint)
                    .accessDeniedHandler(customAccessDeniedHandler)
            }
            .authorizeHttpRequests { auth ->
                auth
                    // Public endpoints - Authentication
                    .requestMatchers(
                        "/api/v1/auth/login",
                        "/api/v1/auth/register",
                        "/api/v1/auth/refresh",
                        "/api/v1/auth/refresh-token",
                        "/api/v1/auth/registration-status",
                        "/api/v1/courses/published"
                    ).permitAll()

                    // Public endpoints - Documentation (only in development)
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()

                    // Public endpoints - Health checks
                    .requestMatchers("/actuator/health", "/actuator/info").permitAll()

                    // Protected actuator endpoints (only for ADMIN)
                    .requestMatchers("/actuator/**").hasRole("ADMIN")

                    // All other endpoints require authentication
                    // Specific permissions are handled by @PreAuthorize annotations in controllers
                    .anyRequest().authenticated()
            }
            // Add filters in order
            .addFilterBefore(rateLimitFilter, BasicAuthenticationFilter::class.java)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder {
        // Using BCrypt with strength 12 for better security
        return BCryptPasswordEncoder(12)
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()

        val origins = allowedOrigins.split(",").map { it.trim() }.filter { it.isNotBlank() }
        val originPatterns = allowedOriginPatterns.split(",").map { it.trim() }.filter { it.isNotBlank() }

        configuration.allowedOrigins = origins
        configuration.allowedOriginPatterns = originPatterns

        // Allowed methods
        configuration.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")

        // Allowed headers
        configuration.allowedHeaders = listOf(
            "Authorization",
            "Content-Type",
            "X-Requested-With",
            "Accept",
            "Origin",
            "Access-Control-Request-Method",
            "Access-Control-Request-Headers"
        )

        // Exposed headers (so frontend can read them)
        configuration.exposedHeaders = listOf(
            "Authorization",
            "X-Total-Count",
            "X-Page-Number",
            "X-Page-Size"
        )

        configuration.allowCredentials = true
        configuration.maxAge = 3600L

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }
}

