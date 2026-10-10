package org.around.ieuri.auth.application.member.provided

import org.around.ieuri.auth.application.member.provided.dto.LifecycleResult
import org.around.ieuri.auth.application.member.provided.dto.WithdrawMemberCommand

/** 탈퇴(본인). provider 연결 해제 → 세션 폐기 → 삭제 순서다. */
interface MemberWithdrawalUseCase {
    fun withdraw(command: WithdrawMemberCommand): LifecycleResult
}
