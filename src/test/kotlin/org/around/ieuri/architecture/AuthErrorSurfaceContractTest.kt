package org.around.ieuri.architecture

import com.tngtech.archunit.core.domain.JavaModifier
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import org.around.ieuri.auth.application.exception.AuthStoreUnavailableException
import org.around.ieuri.auth.application.exception.InvalidTokenException
import org.around.ieuri.auth.application.exception.TokenReuseDetectedException
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
 * 인증 실패가 **최상위 `errors[]`로만 나간다**는 것을 잠근다(SPEC-0032 R3, POL-016).
 *
 * 클라이언트의 자동 토큰 갱신은 최상위 `errors[]`를 보는 링크 계층에서 일어난다. 인증 실패가
 * payload의 `userErrors[]`에 담기면 **갱신이 아예 트리거되지 않는다** — POL-016이 "실기기에서
 * 실제로 겪은 버그"라고 적은 그것이다.
 *
 * `UserErrorTaxonomyTest`가 예외를 손으로 열거하는 것과 달리, 이 테스트는 **클래스패스를
 * 훑는다** — 새 인증 예외를 추가하고 이 목록에 넣는 것을 잊어도 여기서 깨진다.
 *
 * `GraphqlErrorSupport.withUserErrors`가 `Exception`을 넓게 잡는 구조라, catch 범위가 한 번만
 * 넓어지면 인증 실패가 조용히 payload로 샌다. 그것을 막는 것이 이 테스트의 존재 이유다.
 */
class AuthErrorSurfaceContractTest {

    private val classes = ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("org.around.ieuri")

    /**
     * 코드를 싣는 토큰 계열 예외 전부. **클래스패스에서 찾는다** — 손으로 열거하면 새 예외가
     * 빠지고, 빠진 것을 아무도 알아채지 못한다.
     */
    private fun tokenExceptions(): List<TokenAuthenticationException> = classes
        .filter { it.isAssignableTo(TokenAuthenticationException::class.java) }
        .filterNot { it.modifiers.contains(JavaModifier.ABSTRACT) }
        .map { javaClass -> instantiate(javaClass.name) }

    /**
     * 메시지 하나로 만든다. **cause를 함께 받는 생성자도 받아들인다** — 원인을 싣는 예외
     * (`AuthStoreUnavailableException`)가 있고, 그것을 빠뜨리면 검사 대상에서 조용히 빠진다.
     */
    private fun instantiate(className: String): TokenAuthenticationException {
        val type = Class.forName(className)
        val ctor = type.declaredConstructors
            // **합성 생성자를 거른다.** Kotlin의 기본 인자는 (…, int, DefaultConstructorMarker)
            // 형태의 합성 생성자를 함께 만드는데, 거기에 null을 넣으면 primitive int에서
            // IllegalArgumentException이 난다. 생성자 순서는 JVM 명세가 정하지 않으므로
            // 거르지 않으면 **테스트가 무작위로 깨진다** — 보안 불변식을 지키는 테스트가
            // 엉뚱한 이유로 빨개지면 다음 사람은 테스트를 느슨하게 만든다.
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
    private fun topLevelOnlyExceptions(): List<Throwable> =
        tokenExceptions() + listOf(UnauthorizedException("m"), ForbiddenException("m"))

    /**
     * **이것이 R3의 핵심 단언이다.** 어떤 인증 예외도 `userErrors`로 변환되지 않는다.
     */
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

    /**
     * 토큰 계열 예외가 **코드를 서로 다르게** 가진다. 뭉뚱그리면 클라이언트가 "갱신하면 되는
     * 상태"와 "재로그인해야 하는 상태"를 구분하지 못한다 — 이 슬라이스가 고친 문제다.
     */
    @Test
    fun `토큰 계열 예외의 코드가 서로 겹치지 않는다`() {
        val codes = tokenExceptions().map { it.code }

        assertEquals(codes.size, codes.toSet().size, "코드가 겹치는 토큰 예외가 있다: $codes")
    }

    /**
     * 클라이언트가 분기하는 코드들이 실제로 방출되는지 고정한다. 값이 바뀌면 클라이언트의
     * 분기가 조용히 죽으므로, 문자열 자체가 계약이다.
     */
    @Test
    fun `클라이언트가 분기하는 토큰 코드가 계약대로다`() {
        assertEquals("INVALID_TOKEN", InvalidTokenException("m").code)
        assertEquals("TOKEN_REUSE_DETECTED", TokenReuseDetectedException("m").code)
        assertEquals("AUTH_STORE_UNAVAILABLE", AuthStoreUnavailableException("m").code)
        assertEquals("UNAUTHENTICATED_TOKEN_REJECTED", StaleTokenException("m").code)
        assertEquals("SUBSCRIPTION_UNAUTHENTICATED", SubscriptionUnauthenticatedException("m").code)
    }

    /**
     * 스키마의 `TopLevelErrorCode`는 **클라이언트가 읽는 정본이다.** 서버가 내는 코드가 거기
     * 없으면 클라이언트는 그 분기를 만들 수 없고, 존재조차 알 수 없다.
     *
     * `ErrorCode`(payload)와 달리 이쪽은 코드젠 타입이 없어(문서용 enum) SDL 텍스트를 읽는다.
     */
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
