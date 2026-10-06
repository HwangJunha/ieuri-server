package org.around.ieuri.tourism.adapter.graphql.resolver

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsMutation
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import org.around.ieuri.common.security.CurrentMemberProvider
import org.around.ieuri.tourism.adapter.graphql.mapper.toGql
import org.around.ieuri.tourism.adapter.graphql.mapper.toGqlPageInfo
import org.around.ieuri.tourism.application.relation.provided.MapRelationCommandUseCase
import org.around.ieuri.tourism.application.relation.provided.MapRelationQueryUseCase
import org.around.ieuri.tourism.application.relation.provided.dto.MapRelationsQuery
import org.around.ieuri.common.graphql.generated.types.CreateMapRelationInput as GqlCreateMapRelationInput
import org.around.ieuri.common.graphql.generated.types.CreateMapRelationPayload as GqlCreateMapRelationPayload
import org.around.ieuri.common.graphql.generated.types.MapRelation as GqlMapRelation
import org.around.ieuri.common.graphql.generated.types.MapRelationConnection as GqlMapRelationConnection
import org.around.ieuri.common.graphql.generated.types.MapRelationEdge as GqlMapRelationEdge
import org.around.ieuri.common.graphql.generated.types.RemoveMapRelationInput as GqlRemoveMapRelationInput
import org.around.ieuri.common.graphql.generated.types.RemoveMapRelationPayload as GqlRemoveMapRelationPayload

/**
 * 지도 관계 리졸버(SPEC-0030 확장). 셋 다 인증 필수이며 대상 회원을 인자로 받지 않는다.
 * - 겹침은 목록 질의 한 번에 함께 계산되므로 지연 필드가 없다.
 */
@DgsComponent
class MapRelationResolver(
    private val queryUseCase: MapRelationQueryUseCase,
    private val commandUseCase: MapRelationCommandUseCase,
    private val currentMemberProvider: CurrentMemberProvider,
) {

    @DgsQuery
    fun mapRelations(
        @InputArgument first: Int?,
        @InputArgument after: String?,
    ): GqlMapRelationConnection {
        val memberId = currentMemberProvider.requireMemberId()
        val page = queryUseCase.mapRelations(
            MapRelationsQuery(first = first ?: DEFAULT_PAGE_SIZE, after = after),
            memberId = memberId,
        )
        return GqlMapRelationConnection(
            edges = page.edges.map { GqlMapRelationEdge(node = it.node.toGql(), cursor = it.cursor) },
            pageInfo = page.toGqlPageInfo(),
        )
    }

    @DgsMutation
    fun createMapRelation(
        @InputArgument input: GqlCreateMapRelationInput,
    ): GqlCreateMapRelationPayload {
        val memberId = currentMemberProvider.requireMemberId()
        val counterpartId = commandUseCase.createRelation(memberId, input.shareUrl)

        return GqlCreateMapRelationPayload(
            success = true,
            userErrors = emptyList(),
            counterpartMemberId = counterpartId,
        )
    }

    /** 없던 관계를 지워도 성공이다. */
    @DgsMutation
    fun removeMapRelation(
        @InputArgument input: GqlRemoveMapRelationInput,
    ): GqlRemoveMapRelationPayload {
        val memberId = currentMemberProvider.requireMemberId()
        commandUseCase.removeRelation(memberId, input.memberId)

        return GqlRemoveMapRelationPayload(success = true, userErrors = emptyList())
    }

    private companion object {
        /** 스키마의 `first: Int = 20`이 실제 기본값이다 — 인자가 명시적 null일 때만 쓰인다. */
        const val DEFAULT_PAGE_SIZE = 20
    }
}
