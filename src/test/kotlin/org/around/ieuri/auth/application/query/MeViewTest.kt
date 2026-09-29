package org.around.ieuri.auth.application.query

import org.around.ieuri.auth.domain.vo.MemberStatus
import org.around.ieuri.auth.domain.vo.Provider
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** `MeView`의 파생 값 검증(SPEC-0024 R6). */
class MeViewTest {

    private fun meView(provider: Provider) = MeView(
        memberId = 42L,
        email = "me@example.com",
        nickname = "닉네임값",
        profileImageUrl = "https://cdn.example.com/a.png",
        status = MemberStatus.ACTIVE,
        emailVerified = true,
        provider = provider,
        roles = listOf("USER"),
        permissions = listOf("post:write"),
        createdAt = Instant.parse("2026-03-04T05:06:07Z"),
    )

    /** R6-3. */
    @Test
    fun `EMAIL 회원은 canChangeEmail이 참이다`() {
        assertTrue(meView(Provider.EMAIL).canChangeEmail)
    }

    /** R6-4. provider별로 갈리는지 전부 확인한다 — SNS 셋은 모두 거짓이다. */
    @Test
    fun `SNS 회원은 canChangeEmail이 거짓이다`() {
        listOf(Provider.KAKAO, Provider.GOOGLE, Provider.APPLE).forEach { provider ->
            assertFalse(meView(provider).canChangeEmail, "$provider 는 이메일을 변경할 수 없다")
        }
    }
}
