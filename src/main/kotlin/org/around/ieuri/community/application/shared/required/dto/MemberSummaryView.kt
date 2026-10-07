package org.around.ieuri.community.application.shared.required.dto

/** 글·댓글에 실리는 최소 작성자 정보. 커뮤니티 표면에는 이메일·권한·상태가 필요 없다. */
data class MemberSummaryView(
    val id: Long,
    val nickname: String,
    val profileImageUrl: String?,
)
