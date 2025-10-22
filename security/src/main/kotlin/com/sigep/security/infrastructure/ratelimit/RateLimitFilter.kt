package com.sigep.security.infrastructure.ratelimit

import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import io.github.bucket4j.Refill
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

@Component
class RateLimitFilter : OncePerRequestFilter() {

    companion object {
        private val log = LoggerFactory.getLogger(RateLimitFilter::class.java)
        private const val DEFAULT_CAPACITY = 100L
        private const val DEFAULT_REFILL_TOKENS = 100L
        private const val DEFAULT_REFILL_DURATION_MINUTES = 1L
    }

    private val cache = ConcurrentHashMap<String, Bucket>()

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val clientId = getClientId(request)
        val bucket = resolveBucket(clientId)

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response)
        } else {
            log.warn("Rate limit exceeded for client: {} accessing: {}", clientId, request.requestURI)
            response.status = HttpStatus.TOO_MANY_REQUESTS.value()
            response.contentType = "application/json"
            response.writer.write(
                """{"success":false,"message":"Too many requests. Please try again later.","errors":["RATE_LIMIT_EXCEEDED"]}"""
            )
        }
    }

    private fun resolveBucket(clientId: String): Bucket {
        return cache.computeIfAbsent(clientId) { newBucket() }
    }

    private fun newBucket(): Bucket {
        val bandwidth = Bandwidth.classic(
            DEFAULT_CAPACITY,
            Refill.intervally(DEFAULT_REFILL_TOKENS, Duration.ofMinutes(DEFAULT_REFILL_DURATION_MINUTES))
        )
        return Bucket.builder()
            .addLimit(bandwidth)
            .build()
    }

    private fun getClientId(request: HttpServletRequest): String {
        // Try to get user from authentication, otherwise use IP
        val authentication = request.getAttribute("userId")?.toString()
        return authentication ?: request.remoteAddr
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        // Don't apply rate limiting to auth endpoints to prevent lockout during login attempts
        // But you might want to apply stricter limits there separately
        val path = request.requestURI
        return path.startsWith("/swagger-ui") ||
               path.startsWith("/v3/api-docs") ||
               path.startsWith("/actuator/health")
    }
}

