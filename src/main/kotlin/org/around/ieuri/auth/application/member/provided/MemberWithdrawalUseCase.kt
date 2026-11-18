package org.around.ieuri.auth.application.member.provided

import org.around.ieuri.auth.application.member.provided.dto.LifecycleResult

/** 탈퇴(본인). provider 연결 해제 → 세션 폐기 → 삭제 순서다. [ipAddress]는 접속기록용이며 알 수 없으면 null이다. */
interface MemberWithdrawalUseCase {
    fun withdraw(memberId: Long, ipAddress: String?): LifecycleResult
}
