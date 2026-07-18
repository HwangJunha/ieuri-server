package org.around.ieuri.auth.infrastructure.adapter.out.query

import com.querydsl.core.BooleanBuilder
import com.querydsl.jpa.JPAExpressions
import com.querydsl.jpa.impl.JPAQueryFactory
import org.around.ieuri.auth.application.port.out.query.MemberQueryPort
import org.around.ieuri.auth.application.port.out.security.EmailEncryptorPort
import org.around.ieuri.auth.application.query.MembersQuery
import org.around.ieuri.auth.application.query.MeView
import org.around.ieuri.auth.application.query.MemberDetailView
import org.around.ieuri.auth.application.query.MemberSummaryView
import org.around.ieuri.auth.application.query.MembersPage
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
            email = encryptor.decrypt(row.get(member.emailEncrypted)!!),
            nickname = row.get(profile.nickname) ?: DEFAULT_NICKNAME,
            profileImageUrl = row.get(profile.profileImageUrl),
            status = row.get(member.status)!!,
            emailVerified = row.get(member.emailVerified)!!,
            roles = roleCodesOf(memberId),
            permissions = permissionCodesOf(memberId),
            createdAt = row.get(member.createdAt)!!,
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
            email = encryptor.decrypt(row.get(member.emailEncrypted)!!),
            nickname = row.get(profile.nickname) ?: DEFAULT_NICKNAME,
            profileImageUrl = row.get(profile.profileImageUrl),
            status = row.get(member.status)!!,
            emailVerified = row.get(member.emailVerified)!!,
            suspendedAt = row.get(member.suspendedAt),
            suspendedUntil = row.get(member.suspendedUntil),
            suspensionReason = row.get(member.suspensionReason),
            roles = roleCodesOf(memberId),
            createdAt = row.get(member.createdAt)!!,
        )
    }

    override fun findMembers(query: MembersQuery): MembersPage {
        val size = query.size.coerceIn(1, MAX_PAGE_SIZE)
        val page = query.page.coerceAtLeast(0)

        val where = BooleanBuilder()
        query.status?.let { where.and(member.status.eq(it)) }
        query.roleCode?.let { code ->
            // 역할 코드 필터: 해당 코드의 역할을 가진 회원만(중복 행 방지를 위해 exists 서브쿼리 사용).
            where.and(
                JPAExpressions.selectOne()
                    .from(memberRole)
                    .where(
                        memberRole.memberId.eq(member.id).and(
                            memberRole.roleId.`in`(
                                JPAExpressions.select(role.id).from(role).where(role.code.eq(code)),
                            ),
                        ),
                    )
                    .exists(),
            )
        }

        val total = queryFactory.select(member.count()).from(member).where(where).fetchOne() ?: 0L

        val rows = queryFactory
            .select(member.id, profile.nickname, member.status, member.createdAt)
            .from(member)
            .leftJoin(profile).on(profile.memberId.eq(member.id))
            .where(where)
            .orderBy(member.id.desc())
            .offset(page.toLong() * size)
            .limit(size.toLong())
            .fetch()

        val ids = rows.mapNotNull { it.get(member.id) }
        val rolesByMember = roleCodesOf(ids)

        val items = rows.map { r ->
            val id = r.get(member.id)!!
            MemberSummaryView(
                memberId = id,
                nickname = r.get(profile.nickname) ?: DEFAULT_NICKNAME,
                status = r.get(member.status)!!,
                roles = rolesByMember[id].orEmpty(),
                createdAt = r.get(member.createdAt)!!,
            )
        }

        return MembersPage(items = items, page = page, size = size, totalElements = total)
    }

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
            .groupBy({ it.get(memberRole.memberId)!! }, { it.get(role.code)!! })
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
