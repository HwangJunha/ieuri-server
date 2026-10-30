package org.around.ieuri.auth.application.member.provided.dto

/** 표시용 최소 회원 정보. 이메일을 담지 않으며 다른 도메인이 작성자 표시에 쓴다. */
data class MemberPublicProfileView(
    val memberId: Long,
    val nickname: String,
    val profileImageUrl: String?,
)
