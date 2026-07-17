package org.around.ieuri.auth.application.port.`in`.command

import org.around.ieuri.auth.application.command.ReactivateMemberCommand
import org.around.ieuri.auth.application.command.SuspendMemberCommand
import org.around.ieuri.auth.application.command.WithdrawMemberCommand

/** 탈퇴(본인) 유스케이스. */
interface WithdrawMemberUseCase {
    fun handle(command: WithdrawMemberCommand): LifecycleResult
}

/** 정지(관리자) 유스케이스. */
interface SuspendMemberUseCase {
    fun handle(command: SuspendMemberCommand): LifecycleResult
}

/** 복구(관리자) 유스케이스. */
interface ReactivateMemberUseCase {
    fun handle(command: ReactivateMemberCommand): LifecycleResult
}

data class LifecycleResult(
    val success: Boolean,
)
