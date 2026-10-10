package org.around.ieuri.auth.domain.member

import org.around.ieuri.auth.domain.member.MemberFixtures.NOW
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 시행 중인 약관 고르기와 회원 동의 얹기. 버전 비교는 문자열이다 — 기존 동작이라 그대로 고정한다. */
class ActiveTermsTest {

    private fun terms(id: Long, code: String, version: String, required: Boolean = true) = Terms(id, code, version, required)

    private fun agreement(code: String, version: String, agreed: Boolean) = Agreement(termsId = 1L, termsCode = code, termsVersion = version, agreed = agreed, agreedAt = NOW)

    @Test
    fun `코드마다 가장 큰 버전 하나만 남는다`() {
        val active = ActiveTerms.of(listOf(terms(1, "SERVICE", "1.0"), terms(2, "SERVICE", "2.0"), terms(3, "SERVICE", "1.5")))

        assertEquals(listOf(2L), active.all.map { it.id })
        assertEquals("2.0", active.find("SERVICE")?.version)
    }

    /** 숫자가 아니라 문자열 비교다: "1.9"가 "1.10"보다 크다. */
    @Test
    fun `버전은 문자열로 비교해 1점9가 1점10을 이긴다`() {
        val active = ActiveTerms.of(listOf(terms(1, "SERVICE", "1.10"), terms(2, "SERVICE", "1.9"), terms(3, "SERVICE", "1.2")))

        assertEquals("1.9", active.find("SERVICE")?.version)
        assertEquals(2L, active.find("SERVICE")?.id)
    }

    @Test
    fun `순서는 받은 목록에서 코드가 처음 나온 순이다`() {
        val active = ActiveTerms.of(
            listOf(
                terms(1, "PRIVACY", "1.0"),
                terms(2, "SERVICE", "1.0"),
                terms(3, "PRIVACY", "2.0"),
                terms(4, "MARKETING", "1.0", required = false),
            ),
        )

        assertEquals(listOf("PRIVACY", "SERVICE", "MARKETING"), active.all.map { it.code })
        assertEquals(listOf(3L, 2L, 4L), active.all.map { it.id })
    }

    @Test
    fun `필수 코드는 남은 버전의 필수 여부로 정한다`() {
        val active = ActiveTerms.of(
            listOf(
                terms(1, "SERVICE", "1.0", required = true),
                terms(2, "LOCATION", "1.0", required = true),
                terms(3, "LOCATION", "2.0", required = false),
                terms(4, "MARKETING", "1.0", required = false),
            ),
        )

        assertEquals(setOf("SERVICE"), active.requiredCodes)
    }

    @Test
    fun `없는 코드는 찾지 못하고 빈 목록은 빈 약관이다`() {
        assertNull(ActiveTerms.of(listOf(terms(1, "SERVICE", "1.0"))).find("PRIVACY"))
        assertEquals(emptyList(), ActiveTerms.of(emptyList()).all)
        assertEquals(emptySet(), ActiveTerms.of(emptyList()).requiredCodes)
    }

    @Test
    fun `같은 버전에 동의했으면 동의 시각과 함께 동의다`() {
        val service = terms(1, "SERVICE", "2.0")
        val status = ActiveTerms.of(listOf(service)).statusOf(listOf(agreement("SERVICE", "2.0", agreed = true))).single()

        assertEquals(service, status.terms)
        assertEquals(true, status.agreed)
        assertEquals(NOW, status.agreedAt)
    }

    @Test
    fun `이전 버전에 한 동의는 이월되지 않고 동의 시각도 없다`() {
        val status = ActiveTerms.of(listOf(terms(1, "SERVICE", "2.0"))).statusOf(listOf(agreement("SERVICE", "1.0", agreed = true))).single()

        assertEquals(false, status.agreed)
        assertNull(status.agreedAt)
    }

    @Test
    fun `비동의 기록과 기록 없음은 모두 미동의이고 동의 시각이 없다`() {
        val active = ActiveTerms.of(listOf(terms(1, "SERVICE", "1.0"), terms(2, "PRIVACY", "1.0")))

        val statuses = active.statusOf(listOf(agreement("SERVICE", "1.0", agreed = false)))

        assertEquals(listOf("SERVICE", "PRIVACY"), statuses.map { it.terms.code })
        assertEquals(listOf(false, false), statuses.map { it.agreed })
        assertEquals(listOf(null, null), statuses.map { it.agreedAt })
    }

    @Test
    fun `다른 약관의 동의를 가져다 쓰지 않는다`() {
        val active = ActiveTerms.of(listOf(terms(1, "SERVICE", "1.0"), terms(2, "PRIVACY", "1.0")))

        val statuses = active.statusOf(listOf(agreement("PRIVACY", "1.0", agreed = true)))

        assertEquals(listOf(false, true), statuses.map { it.agreed })
    }
}
