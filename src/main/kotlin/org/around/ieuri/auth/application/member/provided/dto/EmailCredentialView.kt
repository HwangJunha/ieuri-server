package org.around.ieuri.auth.application.member.provided.dto

/** 자체(EMAIL) 로그인 대조용. 비밀번호 해시가 없으면 이메일로 로그인할 수 없는 계정이다. */
data class EmailCredentialView(
    val memberId: Long,
    val passwordHash: String?,
)
