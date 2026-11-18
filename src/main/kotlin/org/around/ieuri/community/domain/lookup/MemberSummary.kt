package org.around.ieuri.community.domain.lookup

/** community가 보는 회원(auth 소유)의 최소 정보. 글·댓글·대화에 싣는다 — 이메일·권한·상태는 필요 없다. */
data class MemberSummary(
    val id: Long,
    val nickname: String,
    val profileImageUrl: String?,
)
