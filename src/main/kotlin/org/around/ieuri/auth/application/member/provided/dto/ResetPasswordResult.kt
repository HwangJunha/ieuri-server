package org.around.ieuri.auth.application.member.provided.dto

/** 비밀번호 재설정 결과. 실패 사유는 코드 챌린지 실패 이름(EXPIRED/CONSUMED/…). */
data class ResetPasswordResult(
    val success: Boolean,
    val failureReason: String?,
)
