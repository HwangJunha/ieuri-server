package org.around.ieuri.auth.adapter.persistence.query

import org.around.ieuri.auth.adapter.persistence.mapper.toMeView
import org.around.ieuri.auth.adapter.persistence.mapper.toMemberDetailView
import org.around.ieuri.auth.adapter.persistence.mapper.toMemberPublicProfileView
import org.around.ieuri.auth.adapter.persistence.mapper.toMemberSummaryView
import org.around.ieuri.auth.adapter.persistence.table.MemberProfileTable
import org.around.ieuri.auth.adapter.persistence.table.MemberRoleTable
import org.around.ieuri.auth.adapter.persistence.table.MemberTable
import org.around.ieuri.auth.adapter.persistence.table.PermissionTable
import org.around.ieuri.auth.adapter.persistence.table.RolePermissionTable
import org.around.ieuri.auth.adapter.persistence.table.RoleTable
import org.around.ieuri.auth.application.member.provided.dto.MeView
import org.around.ieuri.auth.application.member.provided.dto.MemberDetailView
import org.around.ieuri.auth.application.member.provided.dto.MemberPublicProfileView
import org.around.ieuri.auth.application.member.provided.dto.MemberSummaryView
import org.around.ieuri.auth.application.member.provided.dto.MembersQuery
import org.around.ieuri.auth.application.member.required.EmailEncryptorPort
import org.around.ieuri.auth.application.member.required.MemberQueryPort
import org.around.ieuri.common.pagination.CursorEdge
import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.common.pagination.CursorPageInfo
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.exists
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Component

/**
 * 회원 읽기 포트 구현. 트랜잭션은 조회 서비스가 연다.
 * - 평문 이메일이 필요한 조회(me/단건)만 여기서 복호화하고, 목록은 이메일을 읽지 않는다.
 */
@Component
class MemberQueryAdapter(
    private val encryptor: EmailEncryptorPort,
) : MemberQueryPort {

    override fun findMe(memberId: Long): MeView? {
        val row = memberWithProfile().where { MemberTable.id eq memberId }.singleOrNull() ?: return null
        return row.toMeView(
            email = encryptor.decrypt(row[MemberTable.emailEncrypted]),
            roles = roleCodesOf(memberId),
            permissions = permissionCodesOf(memberId),
        )
    }

    override fun isEmailVerified(memberId: Long): Boolean = MemberTable.select(MemberTable.emailVerified)
        .where { MemberTable.id eq memberId }
        .singleOrNull()
        ?.get(MemberTable.emailVerified)
        ?: false

    override fun findMemberDetail(memberId: Long): MemberDetailView? {
        val row = memberWithProfile().where { MemberTable.id eq memberId }.singleOrNull() ?: return null
        return row.toMemberDetailView(
            email = encryptor.decrypt(row[MemberTable.emailEncrypted]),
            roles = roleCodesOf(memberId),
        )
    }

    /** 프로필이 없어도 회원은 존재하므로 left join이다. */
    override fun findPublicProfile(memberId: Long): MemberPublicProfileView? = memberWithProfile()
        .where { MemberTable.id eq memberId }
        .singleOrNull()
        ?.toMemberPublicProfileView()

    override fun findPublicProfilesIn(memberIds: List<Long>): Map<Long, MemberPublicProfileView> {
        if (memberIds.isEmpty()) return emptyMap()
        return memberWithProfile()
            .where { MemberTable.id inList memberIds }
            .associate { it[MemberTable.id] to it.toMemberPublicProfileView() }
    }

    /** id 내림차순 keyset. 커서는 회원 id 문자열이고, `first`는 1~100으로 맞춘다. */
    override fun findMembers(query: MembersQuery): CursorPage<MemberSummaryView> {
        val first = query.first.coerceIn(1, MAX_PAGE_SIZE)
        val afterId = query.after?.let {
            it.toLongOrNull() ?: throw IllegalArgumentException("유효하지 않은 커서입니다.")
        }

        val filter = filterOf(query)
        val rows = memberWithProfile()
            .where { afterId?.let { filter and (MemberTable.id less it) } ?: filter }
            .orderBy(MemberTable.id to SortOrder.DESC)
            .limit(first + 1)
            .toList()

        val hasNextPage = rows.size > first
        val pageRows = if (hasNextPage) rows.take(first) else rows
        val rolesByMember = roleCodesOf(pageRows.map { it[MemberTable.id] })

        val edges = pageRows.map { row ->
            val id = row[MemberTable.id]
            CursorEdge(cursor = id.toString(), node = row.toMemberSummaryView(rolesByMember[id].orEmpty()))
        }
        val total = MemberTable.selectAll().where { filter }.count()

        return CursorPage(
            edges = edges,
            pageInfo = CursorPageInfo(
                hasNextPage = hasNextPage,
                hasPreviousPage = afterId != null,
                startCursor = edges.firstOrNull()?.cursor,
                endCursor = edges.lastOrNull()?.cursor,
            ),
            totalCount = total,
        )
    }

    private fun memberWithProfile(): Query = MemberTable
        .join(MemberProfileTable, JoinType.LEFT, MemberTable.id, MemberProfileTable.memberId)
        .selectAll()

    /** status·roleCode 필터. 페이지와 전체 개수가 함께 쓴다. */
    private fun filterOf(query: MembersQuery): Op<Boolean> {
        var filter: Op<Boolean> = Op.TRUE
        query.status?.let { filter = filter and (MemberTable.status eq it.name) }
        query.roleCode?.let { filter = filter and hasRole(it) }
        return filter
    }

    /** 그 코드의 역할을 가진 회원만. 행이 불어나지 않게 exists로 건다. */
    private fun hasRole(code: String): Op<Boolean> = exists(
        MemberRoleTable
            .join(RoleTable, JoinType.INNER, MemberRoleTable.roleId, RoleTable.id)
            .select(MemberRoleTable.memberId)
            .where { (MemberRoleTable.memberId eq MemberTable.id) and (RoleTable.code eq code) },
    )

    private fun roleCodesOf(memberId: Long): List<String> = MemberRoleTable
        .join(RoleTable, JoinType.INNER, MemberRoleTable.roleId, RoleTable.id)
        .select(RoleTable.code)
        .where { MemberRoleTable.memberId eq memberId }
        .orderBy(RoleTable.code to SortOrder.ASC)
        .map { it[RoleTable.code] }

    private fun roleCodesOf(memberIds: List<Long>): Map<Long, List<String>> {
        if (memberIds.isEmpty()) return emptyMap()
        return MemberRoleTable
            .join(RoleTable, JoinType.INNER, MemberRoleTable.roleId, RoleTable.id)
            .select(MemberRoleTable.memberId, RoleTable.code)
            .where { MemberRoleTable.memberId inList memberIds }
            .orderBy(RoleTable.code to SortOrder.ASC)
            .groupBy({ it[MemberRoleTable.memberId] }, { it[RoleTable.code] })
    }

    /** 회원의 유효 권한 코드(역할들의 권한 합집합). */
    private fun permissionCodesOf(memberId: Long): List<String> = MemberRoleTable
        .join(RolePermissionTable, JoinType.INNER, MemberRoleTable.roleId, RolePermissionTable.roleId)
        .join(PermissionTable, JoinType.INNER, RolePermissionTable.permissionId, PermissionTable.id)
        .select(PermissionTable.code)
        .withDistinct()
        .where { MemberRoleTable.memberId eq memberId }
        .orderBy(PermissionTable.code to SortOrder.ASC)
        .map { it[PermissionTable.code] }

    private companion object {
        const val MAX_PAGE_SIZE = 100
    }
}
