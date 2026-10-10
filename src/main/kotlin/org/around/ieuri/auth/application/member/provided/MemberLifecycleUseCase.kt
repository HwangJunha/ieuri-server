package org.around.ieuri.auth.application.member.provided
import org.around.ieuri.auth.application.member.provided.dto.LifecycleResult
import org.around.ieuri.auth.application.member.provided.dto.ReactivateMemberCommand
import org.around.ieuri.auth.application.member.provided.dto.SuspendMemberCommand
import org.around.ieuri.auth.application.member.provided.dto.WithdrawMemberCommand
/** 회원 생명주기 유스케이스: 탈퇴(본인)·정지/복구(관리자). 인가는 리졸버가 판정한다. */
interface MemberLifecycleUseCase {
    /** 탈퇴(본인). 회원과 소유 데이터를 삭제한다. */
    fun withdraw(command: WithdrawMemberCommand): LifecycleResult

    /** 정지(관리자). */
    fun suspend(command: SuspendMemberCommand): LifecycleResult

    /** 복구(관리자). */
    fun reactivate(command: ReactivateMemberCommand): LifecycleResult
}
