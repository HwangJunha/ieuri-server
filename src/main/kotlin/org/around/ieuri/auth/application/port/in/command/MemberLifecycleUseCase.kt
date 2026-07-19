package org.around.ieuri.auth.application.port.`in`.command

import org.around.ieuri.auth.application.command.ReactivateMemberCommand
import org.around.ieuri.auth.application.command.SuspendMemberCommand
import org.around.ieuri.auth.application.command.WithdrawMemberCommand

/**
 * 회원 생명주기 계열 유스케이스(쓰기). 탈퇴(본인)·정지/복구(관리자)를 하나의 응집된 포트로 묶는다.
 * 인가는 인바운드 리졸버에서 판정한다: 탈퇴는 본인 인증, 정지/복구는 member:suspend/member:reactivate 권한.
 */
interface MemberLifecycleUseCase {
    /** 탈퇴(본인). 회원과 소유 데이터를 삭제한다. */
    fun withdraw(command: WithdrawMemberCommand): LifecycleResult

    /** 정지(관리자). */
    fun suspend(command: SuspendMemberCommand): LifecycleResult

    /** 복구(관리자). */
    fun reactivate(command: ReactivateMemberCommand): LifecycleResult
}

data class LifecycleResult(
    val success: Boolean,
)
