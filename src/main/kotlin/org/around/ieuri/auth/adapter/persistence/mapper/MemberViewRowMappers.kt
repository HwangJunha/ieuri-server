package org.around.ieuri.auth.adapter.persistence.mapper

import org.around.ieuri.auth.adapter.persistence.table.MemberProfileTable
import org.around.ieuri.auth.adapter.persistence.table.MemberTable
import org.around.ieuri.auth.adapter.persistence.table.RoleTable
import org.around.ieuri.auth.application.member.provided.dto.MeView
import org.around.ieuri.auth.application.member.provided.dto.MemberDetailView
import org.around.ieuri.auth.application.member.provided.dto.MemberPublicProfileView
import org.around.ieuri.auth.application.member.provided.dto.MemberSummaryView
import org.around.ieuri.auth.application.member.provided.dto.RoleInfoView
import org.around.ieuri.auth.domain.member.MemberStatus
import org.around.ieuri.auth.domain.member.Provider
import org.jetbrains.exposed.v1.core.ResultRow

/** 프로필 행이 없는 회원에게 보이는 닉네임. */
const val DEFAULT_NICKNAME = "여행자"

/** member ⟕ member_profile 행 → 본인 정보. 복호화한 이메일과 역할·권한 코드는 호출자가 넘긴다. */
fun ResultRow.toMeView(email: String, roles: List<String>, permissions: List<String>): MeView = MeView(
    memberId = this[MemberTable.id],
    email = email,
    nickname = getOrNull(MemberProfileTable.nickname) ?: DEFAULT_NICKNAME,
    profileImageUrl = getOrNull(MemberProfileTable.profileImageUrl),
    status = MemberStatus.valueOf(this[MemberTable.status]),
    emailVerified = this[MemberTable.emailVerified],
    provider = Provider.valueOf(this[MemberTable.provider]),
    roles = roles,
    permissions = permissions,
    createdAt = this[MemberTable.createdAt].toInstant(),
)

/** member ⟕ member_profile 행 → 관리자 단건 상세. */
fun ResultRow.toMemberDetailView(email: String, roles: List<String>): MemberDetailView = MemberDetailView(
    memberId = this[MemberTable.id],
    email = email,
    nickname = getOrNull(MemberProfileTable.nickname) ?: DEFAULT_NICKNAME,
    profileImageUrl = getOrNull(MemberProfileTable.profileImageUrl),
    status = MemberStatus.valueOf(this[MemberTable.status]),
    emailVerified = this[MemberTable.emailVerified],
    suspendedAt = this[MemberTable.suspendedAt]?.toInstant(),
    suspendedUntil = this[MemberTable.suspendedUntil]?.toInstant(),
    suspensionReason = this[MemberTable.suspensionReason],
    roles = roles,
    createdAt = this[MemberTable.createdAt].toInstant(),
)

/** member ⟕ member_profile 행 → 표시용 최소 정보. */
fun ResultRow.toMemberPublicProfileView(): MemberPublicProfileView = MemberPublicProfileView(
    memberId = this[MemberTable.id],
    nickname = getOrNull(MemberProfileTable.nickname) ?: DEFAULT_NICKNAME,
    profileImageUrl = getOrNull(MemberProfileTable.profileImageUrl),
)

/** member ⟕ member_profile 행 → 관리자 목록 항목. */
fun ResultRow.toMemberSummaryView(roles: List<String>): MemberSummaryView = MemberSummaryView(
    memberId = this[MemberTable.id],
    nickname = getOrNull(MemberProfileTable.nickname) ?: DEFAULT_NICKNAME,
    status = MemberStatus.valueOf(this[MemberTable.status]),
    roles = roles,
    createdAt = this[MemberTable.createdAt].toInstant(),
)

/** role 행 → 부여 가능한 역할. */
fun ResultRow.toRoleInfoView(): RoleInfoView = RoleInfoView(
    code = this[RoleTable.code],
    name = this[RoleTable.name],
    description = this[RoleTable.description],
)
