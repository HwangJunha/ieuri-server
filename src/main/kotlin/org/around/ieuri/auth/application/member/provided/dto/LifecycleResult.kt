package org.around.ieuri.auth.application.member.provided.dto
import org.around.ieuri.auth.application.member.provided.dto.ReactivateMemberCommand
import org.around.ieuri.auth.application.member.provided.dto.SuspendMemberCommand
import org.around.ieuri.auth.application.member.provided.dto.WithdrawMemberCommand
data class LifecycleResult(
    val success: Boolean,
)
