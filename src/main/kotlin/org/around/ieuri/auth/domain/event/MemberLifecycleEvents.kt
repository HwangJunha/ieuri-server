package org.around.ieuri.auth.domain.event

import java.time.Instant

/** 회원 탈퇴(삭제) 이벤트. 추후 크로스도메인 데이터 정리의 트리거. */
data class MemberWithdrawn(
    val memberId: Long,
    val occurredAt: Instant,
)

/** 회원 정지 이벤트. */
data class MemberSuspended(
    val memberId: Long,
    val reason: String,
    val suspendedUntil: Instant?,
    val occurredAt: Instant,
)

/** 회원 복구 이벤트. */
data class MemberReactivated(
    val memberId: Long,
    val occurredAt: Instant,
)
