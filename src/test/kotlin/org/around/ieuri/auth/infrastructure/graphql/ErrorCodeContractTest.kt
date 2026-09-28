package org.around.ieuri.auth.infrastructure.graphql

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import org.around.ieuri.auth.application.exception.UserFacingException
import org.around.ieuri.auth.infrastructure.adapter.`in`.graphql.toUserErrorOrNull
import org.around.ieuri.common.graphql.generated.types.ErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** `userErrors[].code`로 나갈 수 있는 값과 스키마 `ErrorCode` enum이 어긋나지 않는지 고정한다(클라이언트는 스키마만 본다). */
class ErrorCodeContractTest {

    /** 프로덕션 클래스패스의 모든 `UserFacingException` 구현이 내는 코드. */
    private fun declaredCodes(): Set<String> {
        val classes = ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("org.around.ieuri.auth.application.exception")

        return classes
            .filter { it.isAssignableTo(UserFacingException::class.java) && it != classes.get(UserFacingException::class.java) }
            .map { javaClass ->
                // 코드는 생성자에서 상위로 넘기므로, 인스턴스를 만들어 읽는 것이 가장 정확하다.
                val instance = Class.forName(javaClass.name)
                    .getDeclaredConstructor(String::class.java)
                    .newInstance("test") as UserFacingException
                instance.code
            }
            .toSet()
    }

    @Test
    fun `모든 UserFacingException 코드가 스키마 ErrorCode에 있다`() {
        val schemaCodes = ErrorCode.entries.map { it.name }.toSet()
        val missing = declaredCodes() - schemaCodes

        assertTrue(
            missing.isEmpty(),
            "auth.graphqls의 ErrorCode enum에 없는 코드가 있다: $missing — " +
                "스키마에 추가해야 클라이언트가 이 분기를 만들 수 있다.",
        )
    }

    /** 반대 방향. 쓰이지 않는 값이 남아 있으면 클라이언트가 오지 않을 분기를 만든다. */
    @Test
    fun `스키마 ErrorCode에 실제로 나가지 않는 값이 없다`() {
        val schemaCodes = ErrorCode.entries.map { it.name }.toSet()
        // INVALID_INPUT은 예외 클래스가 아니라 IllegalArgumentException 매핑으로 나간다.
        val actual = declaredCodes() + "INVALID_INPUT"
        val unused = schemaCodes - actual

        assertTrue(
            unused.isEmpty(),
            "ErrorCode에 있으나 서버가 내지 않는 값이 있다: $unused",
        )
    }

    @Test
    fun `UserFacingException은 code와 codeValue를 함께 채운다`() {
        val error = assertNotNull(toUserErrorOrNull(org.around.ieuri.auth.application.exception.NotEmailMemberException("m")))

        assertEquals("NOT_EMAIL_MEMBER", error.code)
        assertEquals(ErrorCode.NOT_EMAIL_MEMBER, error.codeValue)
        assertEquals("m", error.message)
    }

    @Test
    fun `IllegalArgumentException은 INVALID_INPUT으로 나간다`() {
        val error = assertNotNull(toUserErrorOrNull(IllegalArgumentException("형식이 틀렸다")))

        assertEquals("INVALID_INPUT", error.code)
        assertEquals(ErrorCode.INVALID_INPUT, error.codeValue)
    }

    /** 최상위 오류로 나가야 하는 예외는 payload로 새지 않는다. */
    @Test
    fun `인증류 예외는 userErrors로 변환되지 않는다`() {
        assertNull(toUserErrorOrNull(org.around.ieuri.common.security.UnauthorizedException("m")))
        assertNull(toUserErrorOrNull(org.around.ieuri.auth.application.exception.InvalidTokenException("m")))
        assertNull(toUserErrorOrNull(org.around.ieuri.auth.application.exception.TokenReuseDetectedException("m")))
    }
}
