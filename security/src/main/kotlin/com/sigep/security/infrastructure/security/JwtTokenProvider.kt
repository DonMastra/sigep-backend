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
    private val refreshExpiration: Long,

    @Value("\${jwt.role-selection-expiration:300000}") // 5 minutes
    private val roleSelectionExpiration: Long
) {

    companion object {
        private const val TOKEN_TYPE = "tokenType"
        private const val ACCESS = "ACCESS"
        private const val REFRESH = "REFRESH"
        private const val ROLE_SELECTION = "ROLE_SELECTION"
    }

    private val key: SecretKey = Keys.hmacShaKeyFor(jwtSecret.toByteArray())

    fun generateToken(user: User): String {
        return generateToken(user, user.role)
    }

    fun generateToken(user: User, activeRole: com.sigep.security.domain.model.UserRole): String {
        val now = Date()
        val expiryDate = Date(now.time + jwtExpiration)

        return Jwts.builder()
            .subject(user.username)
            .claim(TOKEN_TYPE, ACCESS)
            .claim("userId", user.id)
            .claim("role", activeRole.name)
            .claim("email", user.email)
            .claim("mustChangePassword", user.mustChangePassword)
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(key)
            .compact()
    }

    fun generateRefreshToken(user: User): String {
        return generateRefreshToken(user, user.role)
    }

    fun generateRefreshToken(user: User, activeRole: com.sigep.security.domain.model.UserRole): String {
        val now = Date()
        val expiryDate = Date(now.time + refreshExpiration)

        return Jwts.builder()
            .subject(user.username)
            .claim(TOKEN_TYPE, REFRESH)
            .claim("userId", user.id)
            .claim("role", activeRole.name)
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(key)
            .compact()
    }

    fun generateRoleSelectionToken(user: User): String {
        val now = Date()
        val expiryDate = Date(now.time + roleSelectionExpiration)

        return Jwts.builder()
            .subject(user.username)
            .claim(TOKEN_TYPE, ROLE_SELECTION)
            .claim("userId", user.id)
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

    fun getRoleFromTokenOrNull(token: String): String? = getClaims(token).get("role", String::class.java)

    fun isAccessToken(token: String): Boolean {
        val tokenType = getClaims(token).get(TOKEN_TYPE, String::class.java)
        return tokenType == null || tokenType == ACCESS
    }

    fun isRefreshToken(token: String): Boolean {
        val tokenType = getClaims(token).get(TOKEN_TYPE, String::class.java)
        return tokenType == null || tokenType == REFRESH
    }

    fun isRoleSelectionToken(token: String): Boolean =
        getClaims(token).get(TOKEN_TYPE, String::class.java) == ROLE_SELECTION

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
