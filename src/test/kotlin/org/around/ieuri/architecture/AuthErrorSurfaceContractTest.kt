package org.around.ieuri.architecture

import com.tngtech.archunit.core.domain.JavaModifier
import org.around.ieuri.auth.application.token.provided.exception.AuthStoreUnavailableException
import org.around.ieuri.auth.application.token.provided.exception.InvalidTokenException
import org.around.ieuri.auth.application.token.provided.exception.TokenReuseDetectedException
import org.around.ieuri.auth.infrastructure.adapter.`in`.graphql.toUserErrorOrNull
import org.around.ieuri.common.security.ForbiddenException
import org.around.ieuri.common.security.StaleTokenException
import org.around.ieuri.common.security.SubscriptionUnauthenticatedException
import org.around.ieuri.common.security.TokenAuthenticationException
import org.around.ieuri.common.security.UnauthorizedException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 인증 실패가 최상위 `errors[]`로만 나간다는 것을 잠근다(SPEC-0032, POL-016).
 * - payload `userErrors[]`에 담기면 클라이언트의 자동 토큰 갱신이 트리거되지 않는다.
 * - 예외를 클래스패스에서 훑으므로 새 인증 예외도 자동으로 검사 대상이 된다.
 */
class AuthErrorSurfaceContractTest {

    private val classes = ArchitectureClasses.main

    /** 코드를 싣는 토큰 계열 예외 전부(클래스패스에서 찾는다). */
    private fun tokenExceptions(): List<TokenAuthenticationException> = classes
        .filter { it.isAssignableTo(TokenAuthenticationException::class.java) }
        .filterNot { it.modifiers.contains(JavaModifier.ABSTRACT) }
        .map { javaClass -> instantiate(javaClass.name) }

    /** 메시지 하나로 만든다. cause를 함께 받는 생성자도 받아들인다. */
    private fun instantiate(className: String): TokenAuthenticationException {
        val type = Class.forName(className)
        val ctor = type.declaredConstructors
            // 합성 생성자(기본 인자용)를 거른다 — 생성자 순서가 정해지지 않아 거르지 않으면 무작위로 깨진다.
            .filterNot { it.isSynthetic }
            .filter { it.parameterTypes.firstOrNull() == String::class.java }
            // 인자가 가장 적은 것 — 메시지만 받는 생성자가 있으면 그쪽이다.
            .minByOrNull { it.parameterCount }
            ?: error("$className 은 메시지를 받는 생성자가 있어야 한다.")
        // 첫 인자는 메시지, 나머지(cause 등)는 참조 타입이라 null로 채운다.
        val args = arrayOfNulls<Any?>(ctor.parameterCount).also { it[0] = "m" }
        return ctor.newInstance(*args) as TokenAuthenticationException
    }

    /** 최상위 오류로만 나가야 하는 예외 전부. 코드를 싣지 않는 두 종류는 별도 계층이다. */
    private fun topLevelOnlyExceptions(): List<Throwable> = tokenExceptions() + listOf(UnauthorizedException("m"), ForbiddenException("m"))

    /** 어떤 인증 예외도 `userErrors`로 변환되지 않는다. */
    @Test
    fun `인증 계열 예외는 userErrors로 변환되지 않는다`() {
        topLevelOnlyExceptions().forEach { e ->
            assertNull(
                toUserErrorOrNull(e),
                "${e::class.simpleName}이 userErrors로 새고 있다 — 인증 실패는 최상위 errors[]로 가야 " +
                    "클라이언트의 토큰 갱신이 발동한다(POL-016).",
            )
        }
    }

    /** 토큰 계열 예외의 코드가 서로 다르다 — "갱신하면 되는 상태"와 "재로그인"을 클라이언트가 구분한다. */
    @Test
    fun `토큰 계열 예외의 코드가 서로 겹치지 않는다`() {
        val codes = tokenExceptions().map { it.code }

        assertEquals(codes.size, codes.toSet().size, "코드가 겹치는 토큰 예외가 있다: $codes")
    }

    /** 클라이언트가 분기하는 코드 문자열 자체가 계약이다. */
    @Test
    fun `클라이언트가 분기하는 토큰 코드가 계약대로다`() {
        assertEquals("INVALID_TOKEN", InvalidTokenException("m").code)
        assertEquals("TOKEN_REUSE_DETECTED", TokenReuseDetectedException("m").code)
        assertEquals("AUTH_STORE_UNAVAILABLE", AuthStoreUnavailableException("m").code)
        assertEquals("UNAUTHENTICATED_TOKEN_REJECTED", StaleTokenException("m").code)
        assertEquals("SUBSCRIPTION_UNAUTHENTICATED", SubscriptionUnauthenticatedException("m").code)
    }

    /** 서버가 내는 토큰 코드가 스키마 `TopLevelErrorCode`(클라이언트가 읽는 정본)에 전부 있다. 코드젠 타입이 없어 SDL을 읽는다. */
    @Test
    fun `서버가 내는 토큰 코드가 전부 스키마에 적혀 있다`() {
        val sdl = javaClass.classLoader.getResource("schema/auth.graphqls")!!.readText()
        val declared = Regex("enum TopLevelErrorCode \\{(.*?)\\n\\}", RegexOption.DOT_MATCHES_ALL)
            .find(sdl)
            ?.groupValues
            ?.get(1)
            ?: error("auth.graphqls에서 TopLevelErrorCode enum을 찾지 못했다.")

        tokenExceptions().map { it.code }.forEach { code ->
            assertTrue(
                Regex("^\\s*$code\\s*$", RegexOption.MULTILINE).containsMatchIn(declared),
                "TopLevelErrorCode에 없는 코드를 서버가 낸다: $code — " +
                    "스키마에 적어야 클라이언트가 이 분기를 만들 수 있다.",
            )
        }
    }
}
