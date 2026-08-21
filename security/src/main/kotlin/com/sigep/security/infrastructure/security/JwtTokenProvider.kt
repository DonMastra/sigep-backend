package com.sigep.security.infrastructure.security

import com.sigep.security.domain.model.User
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.*
import javax.crypto.SecretKey

@Component
class JwtTokenProvider(
    @Value("\${jwt.secret:mySecretKeyForJWTTokenGenerationShouldBeAtLeast256BitsLong}")
    private val jwtSecret: String,

    @Value("\${jwt.expiration:86400000}") // 24 hours
    private val jwtExpiration: Long,

    @Value("\${jwt.refresh-expiration:604800000}") // 7 days
    private val refreshExpiration: Long
) {

    private val key: SecretKey = Keys.hmacShaKeyFor(jwtSecret.toByteArray())

    fun generateToken(user: User): String {
        val now = Date()
        val expiryDate = Date(now.time + jwtExpiration)

        return Jwts.builder()
            .subject(user.username)
            .claim("userId", user.id)
            .claim("role", user.role.name)
            .claim("email", user.email)
            .claim("mustChangePassword", user.mustChangePassword)
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(key)
            .compact()
    }

    fun generateRefreshToken(user: User): String {
        val now = Date()
        val expiryDate = Date(now.time + refreshExpiration)

        return Jwts.builder()
            .subject(user.username)
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(key)
            .compact()
    }

    fun getUsernameFromToken(token: String): String {
        return getClaims(token).subject
    }

    fun getUserIdFromToken(token: String): Long {
        return getClaims(token).get("userId", java.lang.Long::class.java).toLong()
    }

    fun getRoleFromToken(token: String): String {
        return getClaims(token).get("role", String::class.java)
    }

    fun getMustChangePasswordFromToken(token: String): Boolean {
        return getClaims(token).get("mustChangePassword", Boolean::class.javaObjectType) ?: false
    }

    fun validateToken(token: String): Boolean {
        return try {
            Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun getClaims(token: String): Claims {
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload
    }
}
