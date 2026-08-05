package org.around.ieuri.auth.infrastructure.adapter.out.query

import com.querydsl.core.BooleanBuilder
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.jpa.JPAExpressions
import com.querydsl.jpa.impl.JPAQueryFactory
import org.around.ieuri.auth.application.port.out.query.MemberQueryPort
import org.around.ieuri.auth.application.port.out.security.EmailEncryptorPort
import org.around.ieuri.auth.application.query.MembersQuery
import org.around.ieuri.auth.application.query.MeView
import org.around.ieuri.auth.application.query.MemberDetailView
import org.around.ieuri.auth.application.query.MemberPublicProfileView
import org.around.ieuri.auth.application.query.MemberSummaryView
import org.around.ieuri.common.pagination.CursorEdge
import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.common.pagination.CursorPageInfo
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.QMemberEntity
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.QMemberProfileEntity
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.QMemberRoleEntity
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.QPermissionEntity
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.QRoleEntity
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.QRolePermissionEntity
import org.springframework.stereotype.Component

/**
 * 회원 읽기 포트 구현(CQRS 읽기 측). QueryDSL이 read DTO로 직접 프로젝션하며 애그리거트를 우회한다.
 * 평문 이메일이 필요한 조회(me/단건)는 여기서 EmailEncryptorPort로 복호화해 DTO를 완성한다(도메인/리졸버 아님).
 * 목록은 최소수집 원칙으로 이메일을 프로젝션하지 않는다(대량 복호화 회피).
 */
@Component
class MemberQueryAdapter(
    private val queryFactory: JPAQueryFactory,
    private val encryptor: EmailEncryptorPort,
) : MemberQueryPort {

    private val member = QMemberEntity.memberEntity
    private val profile = QMemberProfileEntity.memberProfileEntity
    private val memberRole = QMemberRoleEntity.memberRoleEntity
    private val role = QRoleEntity.roleEntity
    private val rolePermission = QRolePermissionEntity.rolePermissionEntity
    private val permission = QPermissionEntity.permissionEntity

    override fun findMe(memberId: Long): MeView? {
        val row = queryFactory
            .select(
                member.emailEncrypted, member.emailVerified, member.status, member.createdAt,
                profile.nickname, profile.profileImageUrl,
            )
            .from(member)
            .leftJoin(profile).on(profile.memberId.eq(member.id))
            .where(member.id.eq(memberId))
            .fetchOne() ?: return null

        return MeView(
            memberId = memberId,
            email = encryptor.decrypt(requireNotNull(row.get(member.emailEncrypted))),
            nickname = row.get(profile.nickname) ?: DEFAULT_NICKNAME,
            profileImageUrl = row.get(profile.profileImageUrl),
            status = requireNotNull(row.get(member.status)),
            emailVerified = requireNotNull(row.get(member.emailVerified)),
            roles = roleCodesOf(memberId),
            permissions = permissionCodesOf(memberId),
            createdAt = requireNotNull(row.get(member.createdAt)),
        )
    }

    override fun findMemberDetail(memberId: Long): MemberDetailView? {
        val row = queryFactory
            .select(
                member.emailEncrypted, member.emailVerified, member.status, member.createdAt,
                member.suspendedAt, member.suspendedUntil, member.suspensionReason,
                profile.nickname, profile.profileImageUrl,
            )
            .from(member)
            .leftJoin(profile).on(profile.memberId.eq(member.id))
            .where(member.id.eq(memberId))
            .fetchOne() ?: return null

        return MemberDetailView(
            memberId = memberId,
            email = encryptor.decrypt(requireNotNull(row.get(member.emailEncrypted))),
            nickname = row.get(profile.nickname) ?: DEFAULT_NICKNAME,
            profileImageUrl = row.get(profile.profileImageUrl),
            status = requireNotNull(row.get(member.status)),
            emailVerified = requireNotNull(row.get(member.emailVerified)),
            suspendedAt = row.get(member.suspendedAt),
            suspendedUntil = row.get(member.suspendedUntil),
            suspensionReason = row.get(member.suspensionReason),
            roles = roleCodesOf(memberId),
            createdAt = requireNotNull(row.get(member.createdAt)),
        )
    }

    /**
     * 표시용 최소 정보. `member` 존재를 확인하고 프로필을 얹는다 — 프로필이 없어도 회원은
     * 존재하므로 leftJoin이며, 닉네임은 기존 조회와 같은 기본값으로 채운다.
     */
    override fun findPublicProfile(memberId: Long): MemberPublicProfileView? =
        queryFactory
            .select(profile.nickname, profile.profileImageUrl)
            .from(member)
            .leftJoin(profile).on(profile.memberId.eq(member.id))
            .where(member.id.eq(memberId))
            .fetchOne()
            ?.let {
                MemberPublicProfileView(
                    memberId = memberId,
                    nickname = it.get(profile.nickname) ?: DEFAULT_NICKNAME,
                    profileImageUrl = it.get(profile.profileImageUrl),
                )
            }

    /**
     * 여러 회원을 한 번에. 목록을 그리는 호출자가 행마다 묻지 않게 한다 —
     * 피드 카드 20장이면 조회가 20번이 된다.
     */
    override fun findPublicProfilesIn(memberIds: List<Long>): Map<Long, MemberPublicProfileView> {
        if (memberIds.isEmpty()) return emptyMap()

        return queryFactory
            .select(member.id, profile.nickname, profile.profileImageUrl)
            .from(member)
            .leftJoin(profile).on(profile.memberId.eq(member.id))
            .where(member.id.`in`(memberIds))
            .fetch()
            .associate { row ->
                val id = requireNotNull(row.get(member.id))
                id to MemberPublicProfileView(
                    memberId = id,
                    nickname = row.get(profile.nickname) ?: DEFAULT_NICKNAME,
                    profileImageUrl = row.get(profile.profileImageUrl),
                )
            }
    }

    override fun findMembers(query: MembersQuery): CursorPage<MemberSummaryView> {
        val first = query.first.coerceIn(1, MAX_PAGE_SIZE)
        // 커서 = 회원 id(문자열). id는 이미 공개 값이라 별도 인코딩 없이 그대로 쓴다.
        val afterId = query.after?.let {
            it.toLongOrNull() ?: throw IllegalArgumentException("유효하지 않은 커서입니다.")
        }

        // status/roleCode 필터(전체 개수·페이지 공통).
        val baseWhere = BooleanBuilder()
        query.status?.let { baseWhere.and(member.status.eq(it)) }
        query.roleCode?.let { baseWhere.and(hasRole(it)) }

        // keyset: id 내림차순 정렬에서 다음 페이지는 id < afterId. first+1로 hasNextPage 판정.
        val pageWhere = BooleanBuilder().and(baseWhere)
        afterId?.let { pageWhere.and(member.id.lt(it)) }

        val rows = queryFactory
            .select(member.id, profile.nickname, member.status, member.createdAt)
            .from(member)
            .leftJoin(profile).on(profile.memberId.eq(member.id))
            .where(pageWhere)
            .orderBy(member.id.desc())
            .limit((first + 1).toLong())
            .fetch()

        val hasNextPage = rows.size > first
        val pageRows = if (hasNextPage) rows.take(first) else rows

        val ids = pageRows.mapNotNull { it.get(member.id) }
        val rolesByMember = roleCodesOf(ids)

        val edges = pageRows.map { r ->
            val id = requireNotNull(r.get(member.id))
            CursorEdge(
                cursor = id.toString(),
                node = MemberSummaryView(
                    memberId = id,
                    nickname = r.get(profile.nickname) ?: DEFAULT_NICKNAME,
                    status = requireNotNull(r.get(member.status)),
                    roles = rolesByMember[id].orEmpty(),
                    createdAt = requireNotNull(r.get(member.createdAt)),
                ),
            )
        }

        val total = queryFactory.select(member.count()).from(member).where(baseWhere).fetchOne() ?: 0L

        val pageInfo = CursorPageInfo(
            hasNextPage = hasNextPage,
            hasPreviousPage = afterId != null,
            startCursor = edges.firstOrNull()?.cursor,
            endCursor = edges.lastOrNull()?.cursor,
        )
        return CursorPage(edges = edges, pageInfo = pageInfo, totalCount = total)
    }

    /** 역할 코드 필터: 해당 코드의 역할을 가진 회원만(중복 행 방지를 위해 exists 서브쿼리 사용). */
    private fun hasRole(code: String): BooleanExpression =
        JPAExpressions.selectOne()
            .from(memberRole)
            .where(
                memberRole.memberId.eq(member.id).and(
                    memberRole.roleId.`in`(
                        JPAExpressions.select(role.id).from(role).where(role.code.eq(code)),
                    ),
                ),
            )
            .exists()

    /** 회원의 역할 코드 목록. */
    private fun roleCodesOf(memberId: Long): List<String> =
        queryFactory
            .select(role.code)
            .from(memberRole)
            .join(role).on(role.id.eq(memberRole.roleId))
            .where(memberRole.memberId.eq(memberId))
            .orderBy(role.code.asc())
            .fetch()

    /** 여러 회원의 역할 코드를 한 번에 조회(목록 N+1 회피). */
    private fun roleCodesOf(memberIds: List<Long>): Map<Long, List<String>> {
        if (memberIds.isEmpty()) return emptyMap()
        return queryFactory
            .select(memberRole.memberId, role.code)
            .from(memberRole)
            .join(role).on(role.id.eq(memberRole.roleId))
            .where(memberRole.memberId.`in`(memberIds))
            .orderBy(role.code.asc())
            .fetch()
            .groupBy({ requireNotNull(it.get(memberRole.memberId)) }, { requireNotNull(it.get(role.code)) })
    }

    /** 회원의 유효 권한 코드(역할들의 권한 합집합). */
    private fun permissionCodesOf(memberId: Long): List<String> =
        queryFactory
            .selectDistinct(permission.code)
            .from(memberRole)
            .join(rolePermission).on(rolePermission.roleId.eq(memberRole.roleId))
            .join(permission).on(permission.id.eq(rolePermission.permissionId))
            .where(memberRole.memberId.eq(memberId))
            .orderBy(permission.code.asc())
            .fetch()

    private companion object {
        const val DEFAULT_NICKNAME = "여행자"
        const val MAX_PAGE_SIZE = 100
    }
}
