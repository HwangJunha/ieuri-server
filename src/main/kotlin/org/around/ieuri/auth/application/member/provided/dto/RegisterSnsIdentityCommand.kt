package org.around.ieuri.auth.application.member.provided.dto

/** 검증을 마친 SNS 신원으로 가입한다. [request]는 클라이언트가 보낸 가입 요청이다. */
data class RegisterSnsIdentityCommand(
    val request: RegisterMemberCommand,
    val providerUserId: String,
    /** provider가 준 이메일. 없으면 [RegisterMemberCommand.submittedEmail]을 쓰고 인증을 요구한다. */
    val email: String?,
    val nickname: String?,
    val profileImageUrl: String?,
)
