package org.around.ieuri.auth.infrastructure.adapter.`in`.graphql

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import org.around.ieuri.auth.application.port.`in`.query.MemberQueryUseCase
import org.around.ieuri.auth.application.query.MembersQuery
import org.around.ieuri.auth.application.query.MeView
import org.around.ieuri.auth.application.query.MemberDetailView
import org.around.ieuri.auth.application.query.MembersPage
import org.around.ieuri.auth.application.query.MemberSummaryView
import org.around.ieuri.auth.domain.vo.MemberStatus
import org.around.ieuri.auth.domain.vo.Permission
import org.around.ieuri.auth.infrastructure.security.CurrentMemberProvider
import org.around.ieuri.auth.infrastructure.security.RequiresPermission
import org.around.ieuri.auth.infrastructure.graphql.generated.types.Me as GqlMe
import org.around.ieuri.auth.infrastructure.graphql.generated.types.MemberDetail as GqlMemberDetail
import org.around.ieuri.auth.infrastructure.graphql.generated.types.MemberPage as GqlMemberPage
import org.around.ieuri.auth.infrastructure.graphql.generated.types.MemberStatus as GqlMemberStatus
import org.around.ieuri.auth.infrastructure.graphql.generated.types.MemberSummary as GqlMemberSummary

/**
 * 회원 조회 GraphQL 쿼리 리졸버(인바운드 어댑터, CQRS 읽기 측).
 * `me`는 인증만, `member`/`members`는 member:read 권한을 요구한다. read DTO를 GraphQL 응답 타입으로 매핑한다.
 */
@DgsComponent
class MemberQueryResolver(
    private val memberQueryUseCase: MemberQueryUseCase,
    private val currentMemberProvider: CurrentMemberProvider,
) {
    @DgsQuery
    fun me(): GqlMe {
        val memberId = currentMemberProvider.requireMemberId()
        return memberQueryUseCase.me(memberId).toGql()
    }

    @DgsQuery
    @RequiresPermission(Permission.MEMBER_READ)
    fun member(@InputArgument id: String): GqlMemberDetail =
        memberQueryUseCase.member(id.toLong()).toGql()

    @DgsQuery
    @RequiresPermission(Permission.MEMBER_READ)
    fun members(
        @InputArgument status: GqlMemberStatus?,
        @InputArgument roleCode: String?,
        @InputArgument page: Int?,
        @InputArgument size: Int?,
    ): GqlMemberPage {
        val result = memberQueryUseCase.members(
            MembersQuery(
                status = status?.let { MemberStatus.valueOf(it.name) },
                roleCode = roleCode,
                page = page ?: 0,
                size = size ?: DEFAULT_PAGE_SIZE,
            ),
        )
        return result.toGql()
    }

    private fun MeView.toGql(): GqlMe = GqlMe(
        id = memberId.toString(),
        email = email,
        nickname = nickname,
        profileImageUrl = profileImageUrl,
        status = GqlMemberStatus.valueOf(status.name),
        emailVerified = emailVerified,
        roles = roles,
        permissions = permissions,
        createdAt = createdAt.toString(),
    )

    private fun MemberDetailView.toGql(): GqlMemberDetail = GqlMemberDetail(
        id = memberId.toString(),
        email = email,
        nickname = nickname,
        profileImageUrl = profileImageUrl,
        status = GqlMemberStatus.valueOf(status.name),
        emailVerified = emailVerified,
        suspendedAt = suspendedAt?.toString(),
        suspendedUntil = suspendedUntil?.toString(),
        suspensionReason = suspensionReason,
        roles = roles,
        createdAt = createdAt.toString(),
    )

    private fun MembersPage.toGql(): GqlMemberPage = GqlMemberPage(
        items = items.map { it.toGql() },
        page = page,
        size = size,
        totalElements = totalElements.toInt(),
        totalPages = totalPages,
    )

    private fun MemberSummaryView.toGql(): GqlMemberSummary = GqlMemberSummary(
        id = memberId.toString(),
        nickname = nickname,
        status = GqlMemberStatus.valueOf(status.name),
        roles = roles,
        createdAt = createdAt.toString(),
    )

    private companion object {
        const val DEFAULT_PAGE_SIZE = 20
    }
}
