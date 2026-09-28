package org.around.ieuri.auth.application.query

import org.around.ieuri.auth.domain.vo.MemberStatus
import org.around.ieuri.auth.domain.vo.Provider
import java.time.Instant

/* 읽기 전용 read model. QueryDSL이 직접 프로젝션하며 리졸버가 GraphQL 응답 타입으로 매핑한다. */

/** 본인 정보(me). 이메일은 평문이며 권한/역할은 클라이언트 메뉴 렌더에 쓴다. */
data class MeView(
    val memberId: Long,
    val email: String,
    val nickname: String,
    val profileImageUrl: String?,
    val status: MemberStatus,
    val emailVerified: Boolean,
    val provider: Provider,
    val roles: List<String>,
    val permissions: List<String>,
    val createdAt: Instant,
) {
    /** 이메일 변경 가능 여부(SPEC-0024 R6). 저장하지 않고 provider에서 파생한다. */
    val canChangeEmail: Boolean get() = provider == Provider.EMAIL
}

/** 관리자 회원 단건 상세(member). member:read 권한 필요. 단건이므로 이메일 평문 노출. */
data class MemberDetailView(
    val memberId: Long,
    val email: String,
    val nickname: String,
    val profileImageUrl: String?,
    val status: MemberStatus,
    val emailVerified: Boolean,
    val suspendedAt: Instant?,
    val suspendedUntil: Instant?,
    val suspensionReason: String?,
    val roles: List<String>,
    val createdAt: Instant,
)

/** 표시용 최소 회원 정보. 이메일을 담지 않으며 다른 도메인이 작성자 표시에 쓴다. */
data class MemberPublicProfileView(
    val memberId: Long,
    val nickname: String,
    val profileImageUrl: String?,
)

/** 관리자 회원 목록 항목. 이메일은 포함하지 않는다(대량 복호화 회피). */
data class MemberSummaryView(
    val memberId: Long,
    val nickname: String,
    val status: MemberStatus,
    val roles: List<String>,
    val createdAt: Instant,
)

/** 회원 목록 조회 조건. first는 개수, after는 커서(없으면 처음부터), status/roleCode는 선택 필터. */
data class MembersQuery(
    val status: MemberStatus?,
    val roleCode: String?,
    val first: Int,
    val after: String?,
)
