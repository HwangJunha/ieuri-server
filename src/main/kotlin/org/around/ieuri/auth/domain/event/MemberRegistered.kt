package org.around.ieuri.auth.domain.event

import org.around.ieuri.auth.domain.vo.Provider
import java.time.Instant
import java.util.UUID

/**
 * 회원가입 성공 도메인 이벤트. 가입은 검증 완료를 기다리지 않고 성공 처리되며,
 * 미검증인 경우 verificationPublicId(검증 핸들)가 채워져 AFTER_COMMIT 리스너가 코드 메일 발송을 트리거한다.
 */
data class MemberRegistered(
    val memberId: Long,
    val provider: Provider,
    val emailVerified: Boolean,
    val verificationPublicId: UUID?,
    val occurredAt: Instant,
)
