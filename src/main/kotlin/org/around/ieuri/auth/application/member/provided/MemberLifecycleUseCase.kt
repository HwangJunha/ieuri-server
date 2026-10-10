package org.around.ieuri.auth.application.member.provided

import org.around.ieuri.auth.application.member.provided.dto.EraseMemberCommand
import org.around.ieuri.auth.application.member.provided.dto.LifecycleResult
import org.around.ieuri.auth.application.member.provided.dto.ReactivateMemberCommand
import org.around.ieuri.auth.application.member.provided.dto.SuspendMemberCommand
import org.around.ieuri.auth.application.member.provided.dto.WithdrawalTargetView

/** 정지·복구(관리자)와 탈퇴의 DB 단계. 인가는 리졸버가 판정한다. */
interface MemberLifecycleUseCase {
    fun suspend(command: SuspendMemberCommand): LifecycleResult

    fun reactivate(command: ReactivateMemberCommand): LifecycleResult

    /** 탈퇴할 회원의 provider 정보. 없는 회원이면 MEMBER_NOT_FOUND다. */
    fun withdrawalTarget(memberId: Long): WithdrawalTargetView

    /** 회원과 소유 데이터를 지운다. */
    fun erase(command: EraseMemberCommand)
}
