package org.around.ieuri.auth.application.member.required.dto

/** 검증된 SNS 신원. 애플은 최초 인증에서만 이름/이메일을 준다. email이 null이면 사용자가 직접 입력해야 한다. */
data class SnsIdentity(
    val providerUserId: String,
    val email: String?,
    val nickname: String?,
    val profileImageUrl: String?,
)
