package org.around.ieuri.auth.application.member.provided.dto

import org.around.ieuri.auth.domain.member.Provider

/** 회원 행 삭제. provider 연결 해제가 실패했으면 그 사유를 함께 남긴다(탈퇴는 그대로 진행한다). */
data class EraseMemberCommand(
    val memberId: Long,
    val provider: Provider,
    val unlinkFailureReason: String?,
)
