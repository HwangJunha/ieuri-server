package org.around.ieuri.auth.domain.member.event
import org.around.ieuri.auth.domain.member.Provider
import java.time.Instant

/** 회원이 탈퇴(삭제)했다. 다른 context가 이 회원의 데이터를 정리하는 계기다. */
data class MemberWithdrawn(
    val memberId: Long,
    val provider: Provider,
    val occurredAt: Instant,
)
