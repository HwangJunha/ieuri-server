package org.around.ieuri.auth.application.query

import org.around.ieuri.auth.domain.vo.MemberStatus
import java.time.Instant

/**
 * 읽기(CQRS) 전용 read model. 애그리거트를 우회해 QueryDSL이 직접 프로젝션한 결과이며,
 * 도메인/JPA 어느 쪽도 아니다. 리졸버가 이 DTO를 GraphQL 응답 타입으로 매핑한다.
 */

/** 본인 정보(me). 인증만 필요(권한 불요). 이메일은 본인이므로 평문 노출, 권한/역할로 클라이언트 메뉴 렌더에 사용. */
data class MeView(
    val memberId: Long,
    val email: String,
    val nickname: String,
    val profileImageUrl: String?,
    val status: MemberStatus,
    val emailVerified: Boolean,
    val roles: List<String>,
    val permissions: List<String>,
    val createdAt: Instant,
)

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

/** 관리자 회원 목록 항목. 최소수집: 대량 복호화 회피를 위해 이메일은 포함하지 않는다. */
data class MemberSummaryView(
    val memberId: Long,
    val nickname: String,
    val status: MemberStatus,
    val roles: List<String>,
    val createdAt: Instant,
)

/** 회원 목록 페이지. */
data class MembersPage(
    val items: List<MemberSummaryView>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
) {
    val totalPages: Int
        get() = if (size <= 0) 0 else ((totalElements + size - 1) / size).toInt()
}

/** 회원 목록 조회 조건. status/roleCode는 선택 필터. */
data class MembersQuery(
    val status: MemberStatus?,
    val roleCode: String?,
    val page: Int,
    val size: Int,
)
