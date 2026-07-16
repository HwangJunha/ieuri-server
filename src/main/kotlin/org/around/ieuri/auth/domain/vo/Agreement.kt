package org.around.ieuri.auth.domain.vo

import java.time.Instant

/**
 * 특정 약관 버전에 대한 동의/비동의 스냅샷.
 * 동의 이력은 append-only이며 동의(agreed=true)뿐 아니라 비동의(false)도 이력으로 남긴다.
 * 애그리거트 간 참조는 id로 한다(termsId는 평문 식별자).
 */
data class Agreement(
    val termsId: Long,
    val termsCode: String,
    val termsVersion: String,
    val agreed: Boolean,
    val agreedAt: Instant,
)
