package org.around.ieuri.auth.application.query

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.around.ieuri.auth.domain.vo.MemberStatus
import org.around.ieuri.auth.domain.vo.Provider

/**
 * `MeView`의 파생 값 검증(SPEC-0024 R6).
 *
 * **매퍼 테스트에서 옮겨 왔다.** `Me`가 지연 필드(`notificationPreferences`)를 갖게 되면서
 * 루트 리졸버가 이 read DTO를 그대로 돌려주게 됐고(SPEC-0028), `MeView → Me` 변환 함수가
 * 사라졌다. `canChangeEmail`은 그 변환이 아니라 이 DTO의 규칙이므로 여기서 검증한다.
 */
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
