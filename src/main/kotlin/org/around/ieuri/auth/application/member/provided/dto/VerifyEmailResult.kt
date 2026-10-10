package org.around.ieuri.auth.application.member.provided.dto

data class VerifyEmailResult(
    val verified: Boolean,
    /** 실패 사유(도메인 VerificationFailureReason 이름). 성공 시 null. */
    val failureReason: String?,
)
