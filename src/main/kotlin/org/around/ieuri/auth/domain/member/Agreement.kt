package org.around.ieuri.auth.domain.member
import java.time.Instant

/** 특정 약관 버전에 대한 동의/비동의 스냅샷. 비동의(agreed=false)도 기록한다. */
data class Agreement(
    val termsId: Long,
    val termsCode: String,
    val termsVersion: String,
    val agreed: Boolean,
    val agreedAt: Instant,
)
