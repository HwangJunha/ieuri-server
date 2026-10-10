package org.around.ieuri.auth.adapter.graphql.resolver
import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsData
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import org.around.ieuri.auth.adapter.graphql.mapper.toGql
import org.around.ieuri.auth.adapter.security.RequiresPermission
import org.around.ieuri.auth.application.member.provided.MemberQueryUseCase
import org.around.ieuri.auth.application.member.provided.dto.MeView
import org.around.ieuri.auth.application.member.provided.dto.MembersQuery
import org.around.ieuri.auth.domain.member.MemberStatus
import org.around.ieuri.auth.domain.member.Permission
import org.around.ieuri.common.security.CurrentMemberProvider
import org.around.ieuri.common.graphql.generated.types.MemberConnection as GqlMemberConnection
import org.around.ieuri.common.graphql.generated.types.MemberDetail as GqlMemberDetail
import org.around.ieuri.common.graphql.generated.types.MemberStatus as GqlMemberStatus

/** 회원 조회 쿼리 리졸버. `me`는 인증만, `member`/`members`는 member:read 권한을 요구한다. */
@DgsComponent
class MemberQueryResolver(
    private val memberQueryUseCase: MemberQueryUseCase,
    private val currentMemberProvider: CurrentMemberProvider,
) {
    /**
     * 본인 정보. 생성 타입이 아니라 read DTO를 돌려준다 — 지연 필드(`notificationPreferences`)가 `getSource`로 받는다.
     * - 이름이 어긋나는 `id`·`status`·`createdAt`만 아래에서 옮긴다.
     */
    @DgsQuery
    fun me(): MeView {
        val memberId = currentMemberProvider.requireMemberId()
        return memberQueryUseCase.me(memberId)
    }

    @DgsData(parentType = "Me", field = "id")
    fun meId(dfe: DgsDataFetchingEnvironment): Long = dfe.meSource().memberId

    @DgsData(parentType = "Me", field = "status")
    fun meStatus(dfe: DgsDataFetchingEnvironment): GqlMemberStatus = GqlMemberStatus.valueOf(dfe.meSource().status.name)

    @DgsData(parentType = "Me", field = "createdAt")
    fun meCreatedAt(dfe: DgsDataFetchingEnvironment): String = dfe.meSource().createdAt.toString()

    private fun DgsDataFetchingEnvironment.meSource(): MeView = getSource<MeView>() ?: error("Me 필드 리졸버는 부모가 반환한 MeView를 소스로 받아야 한다.")

    @DgsQuery
    @RequiresPermission(Permission.MEMBER_READ)
    fun member(@InputArgument id: Long): GqlMemberDetail = memberQueryUseCase.member(id).toGql()

    @DgsQuery
    @RequiresPermission(Permission.MEMBER_READ)
    fun members(
        @InputArgument status: GqlMemberStatus?,
        @InputArgument roleCode: String?,
        @InputArgument first: Int?,
        @InputArgument after: String?,
    ): GqlMemberConnection {
        val result = memberQueryUseCase.members(
            MembersQuery(
                status = status?.let { MemberStatus.valueOf(it.name) },
                roleCode = roleCode,
                first = first ?: DEFAULT_PAGE_SIZE,
                after = after,
            ),
        )
        return result.toGql()
    }

    private companion object {
        const val DEFAULT_PAGE_SIZE = 20
    }
}
