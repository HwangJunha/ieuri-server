package org.around.ieuri.auth.domain.audit

import java.time.Duration
import java.time.Instant

/** 회원 감사 기록 한 건 — 회원에게 무슨 일이 있었는지. 한 번 쓰고 고치지 않는다. */
data class MemberAuditLog(
    val memberId: Long,
    val eventType: String,
    /** 개인정보(이메일·토큰)를 담지 않는다(POL-013). */
    val payload: Map<String, Any?>,
) {
    companion object {
        /** 보관 기간(POL-014). 정책값이라 설정으로 빼지 않는다. */
        val RETENTION: Duration = Duration.ofDays(365)

        /** 이 시각보다 먼저 만들어진 기록이 파기 대상이다. 정확히 기간에 걸친 기록은 아직 아니다. */
        fun cutoff(now: Instant): Instant = now.minus(RETENTION)
    }
}
