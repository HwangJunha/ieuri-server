package org.around.ieuri.auth.application.member.provided.dto

/** SNS 가입의 저장 결과. [memberId]는 응답에 싣지 않고 provider 토큰 저장에만 쓴다. */
data class SnsRegistrationResult(
    val memberId: Long,
    val result: RegisterMemberResult,
)
