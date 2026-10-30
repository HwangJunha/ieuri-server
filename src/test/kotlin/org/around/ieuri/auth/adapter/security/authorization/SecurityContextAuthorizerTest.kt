package org.around.ieuri.auth.adapter.security.authorization
import org.around.ieuri.auth.adapter.security.authorization.SecurityContextAuthorizer
import org.around.ieuri.common.security.ForbiddenException
import org.around.ieuri.common.security.UnauthorizedException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** SecurityContextAuthorizer — 권한(perms) 기반 인가 판정. 역할명이 아닌 권한 코드로만 판정한다. */
class SecurityContextAuthorizerTest {

    private val authorizer = SecurityContextAuthorizer()

    // SecurityContextHolder는 ThreadLocal이라 Gradle이 스레드를 재사용하면 앞선 테스트의 인증이 남을 수 있다.
    // 시작 전에도 비워 각 테스트가 실행 순서에 의존하지 않게 한다.
    @BeforeTest
    fun setUp() = SecurityContextHolder.clearContext()

    @AfterTest
    fun tearDown() = SecurityContextHolder.clearContext()

    private fun authenticateWith(vararg permissions: String) {
        val authorities = permissions.map { SimpleGrantedAuthority(it) }
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(1L, null, authorities)
    }

    @Test
    fun `권한을 보유하면 통과한다`() {
        authenticateWith("member:read", "member:suspend")
        authorizer.require("member:read")
        authorizer.require("member:suspend")
    }

    @Test
    fun `권한이 없으면 Forbidden`() {
        authenticateWith("member:read")
        assertFailsWith<ForbiddenException> { authorizer.require("role:assign") }
    }

    @Test
    fun `인증 자체가 없으면 Unauthorized`() {
        assertFailsWith<UnauthorizedException> { authorizer.require("member:read") }
    }
}
