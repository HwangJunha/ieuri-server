package org.around.ieuri.auth.application.member.provided.dto

/** 복구 커맨드(관리자). */
data class ReactivateMemberCommand(
    val memberId: Long,
)
