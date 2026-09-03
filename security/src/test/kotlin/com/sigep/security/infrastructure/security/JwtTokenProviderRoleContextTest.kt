package com.sigep.security.infrastructure.security

import com.sigep.security.domain.model.AccountStatus
import com.sigep.security.domain.model.User
import com.sigep.security.domain.model.UserRole
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JwtTokenProviderRoleContextTest {
    private val jwtSecret = "a-local-test-secret-that-is-long-enough-for-hmac-sha-256"
    private val roleSelectionExpiration = 600_000L
    private val provider = JwtTokenProvider(
        jwtSecret = jwtSecret,
        jwtExpiration = 60_000,
        refreshExpiration = 120_000,
        roleSelectionExpiration = roleSelectionExpiration
    )
    private val user = User(
        id = 81,
        username = "multi",
        email = "multi@sigep.test",
        password = "hash",
        firstName = "Multi",
        lastName = "Role",
        role = UserRole.ADMIN,
        status = AccountStatus.ACTIVE,
        active = true
    )

    @Test
    fun `selection token cannot authenticate functional APIs`() {
        val token = provider.generateRoleSelectionToken(user)

        assertTrue(provider.validateToken(token))
        assertTrue(provider.isRoleSelectionToken(token))
        assertFalse(provider.isAccessToken(token))
        assertFalse(provider.isRefreshToken(token))
        assertNull(provider.getRoleFromTokenOrNull(token))
    }

    @Test
    fun `selection token uses the configured ten minute lifetime`() {
        val token = provider.generateRoleSelectionToken(user)
        val claims = Jwts.parser()
            .verifyWith(Keys.hmacShaKeyFor(jwtSecret.toByteArray()))
            .build()
            .parseSignedClaims(token)
            .payload

        assertEquals(roleSelectionExpiration, claims.expiration.time - claims.issuedAt.time)
    }

    @Test
    fun `access and refresh tokens contain only the selected guardian role`() {
        val access = provider.generateToken(user, UserRole.GUARDIAN)
        val refresh = provider.generateRefreshToken(user, UserRole.GUARDIAN)

        assertEquals(UserRole.GUARDIAN.name, provider.getRoleFromToken(access))
        assertEquals(UserRole.GUARDIAN.name, provider.getRoleFromToken(refresh))
        assertTrue(provider.isAccessToken(access))
        assertTrue(provider.isRefreshToken(refresh))
        assertFalse(provider.isRefreshToken(access))
        assertFalse(provider.isAccessToken(refresh))
    }
}
