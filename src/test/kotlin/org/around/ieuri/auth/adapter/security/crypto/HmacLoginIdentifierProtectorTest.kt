package org.around.ieuri.auth.adapter.security.crypto
import org.around.ieuri.auth.adapter.config.EncryptionProperties
import org.around.ieuri.auth.adapter.security.crypto.HmacLoginIdentifierProtector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * 로그인 식별자 보호 변환의 성질을 고정한다(SPEC-0012 R1).
 * 조회와 유니크 제약이 이 변환 위에서 동작하므로 결정론성이 깨지면 로그인 자체가 깨진다.
 */
class HmacLoginIdentifierProtectorTest {

    private val protector = HmacLoginIdentifierProtector(
        EncryptionProperties(
            key = "aWV1cmlfbG9jYWxfZGV2X2FlczI1Nl9rZXlfMzJieXQ=",
            pepper = "verification-pepper",
            identifierPepper = "identifier-pepper",
        ),
    )

    @Test
    fun `보호된 값은 평문과 다르다`() {
        val email = "user@example.com"
        assertNotEquals(email, protector.protect(email))
    }

    @Test
    fun `같은 입력은 항상 같은 결과를 낸다`() {
        // 등호 조회와 유니크 제약이 이 성질에 의존한다.
        assertEquals(protector.protect("user@example.com"), protector.protect("user@example.com"))
    }

    @Test
    fun `서로 다른 입력은 서로 다른 결과를 낸다`() {
        assertNotEquals(protector.protect("a@example.com"), protector.protect("b@example.com"))
    }

    @Test
    fun `한 글자만 달라도 결과가 달라진다`() {
        assertNotEquals(protector.protect("user@example.com"), protector.protect("user@example.con"))
    }

    @Test
    fun `결과는 64자 소문자 hex다`() {
        // provider_user_id는 VARCHAR(255)이므로 길이가 컬럼에 들어가야 한다.
        val protected = protector.protect("user@example.com")
        assertEquals(64, protected.length)
        assertTrue(protected.all { it in "0123456789abcdef" }, "예상 밖 문자 포함: $protected")
    }

    @Test
    fun `pepper가 다르면 결과가 다르다`() {
        val other = HmacLoginIdentifierProtector(
            EncryptionProperties(
                key = "aWV1cmlfbG9jYWxfZGV2X2FlczI1Nl9rZXlfMzJieXQ=",
                pepper = "verification-pepper",
                identifierPepper = "different-identifier-pepper",
            ),
        )
        assertNotEquals(protector.protect("user@example.com"), other.protect("user@example.com"))
    }

    @Test
    fun `검증 코드용 pepper는 결과에 영향을 주지 않는다`() {
        // 두 pepper를 분리한 이유 — 검증 코드 pepper 교체가 로그인을 깨뜨려선 안 된다.
        val other = HmacLoginIdentifierProtector(
            EncryptionProperties(
                key = "aWV1cmlfbG9jYWxfZGV2X2FlczI1Nl9rZXlfMzJieXQ=",
                pepper = "rotated-verification-pepper",
                identifierPepper = "identifier-pepper",
            ),
        )
        assertEquals(protector.protect("user@example.com"), other.protect("user@example.com"))
    }
}
