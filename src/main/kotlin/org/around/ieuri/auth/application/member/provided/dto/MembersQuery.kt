package org.around.ieuri.auth.application.member.provided.dto
import org.around.ieuri.auth.domain.member.MemberStatus

/** 회원 목록 조회 조건. first는 개수, after는 커서(없으면 처음부터), status/roleCode는 선택 필터. */
data class MembersQuery(
    val status: MemberStatus?,
    val roleCode: String?,
    val first: Int,
    val after: String?,
)
