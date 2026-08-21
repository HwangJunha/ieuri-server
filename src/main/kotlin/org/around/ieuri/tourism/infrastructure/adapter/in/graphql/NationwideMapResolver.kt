package org.around.ieuri.tourism.infrastructure.adapter.`in`.graphql

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsData
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import org.around.ieuri.common.graphql.generated.types.CellImage as GqlCellImage
import org.around.ieuri.common.graphql.generated.types.CellImageKind as GqlCellImageKind
import org.around.ieuri.common.graphql.generated.types.CellSpotConnection as GqlCellSpotConnection
import org.around.ieuri.common.graphql.generated.types.CellSpotEdge as GqlCellSpotEdge
import org.around.ieuri.common.graphql.generated.types.Coordinates as GqlCoordinates
import org.around.ieuri.common.graphql.generated.types.CopyrightType as GqlCopyrightType
import org.around.ieuri.common.graphql.generated.types.SharedMap as GqlSharedMap
import org.around.ieuri.common.security.CurrentMemberProvider
import org.around.ieuri.tourism.application.policy.NationwideMapPolicy
import org.around.ieuri.tourism.application.port.`in`.query.NationwideMapUseCase
import org.around.ieuri.tourism.application.query.CellSpotsQuery
import org.around.ieuri.tourism.application.query.MapCellView
import org.around.ieuri.tourism.infrastructure.adapter.`in`.graphql.mapper.toGql
import org.around.ieuri.tourism.infrastructure.adapter.`in`.graphql.mapper.toGqlPageInfo
import org.around.ieuri.tourism.infrastructure.locale.domainLocale

/**
 * 전국 지도 조회 리졸버(SPEC-0030).
 *
 * **루트가 읽기 DTO를 반환하고 비싼 필드는 각자 조회한다.** 대표 이미지와 spot 목록은 각각
 * 별도 I/O이므로 `{ code name viewerVisited }`만 물으면 그 둘은 조회되지 않는다 — 필드 선택이
 * I/O를 바꾸지 못하면 GraphQL을 쓰는 이유가 사라진다.
 *
 * 생성 타입이 아니라 [MapCellView]를 돌려주는 이유도 같다: 생성 타입은 non-null 필드를 생성자
 * 필수 인자로 요구해서 지연 필드를 비워 둘 수 없다.
 *
 * `mapCells`·`mapCell`·`sharedMap` 셋 다 비인증을 허용한다 — 지도 자체는 로그인 없이 보인다.
 */
@DgsComponent
class NationwideMapResolver(
    private val nationwideMapUseCase: NationwideMapUseCase,
    private val currentMemberProvider: CurrentMemberProvider,
    private val policy: NationwideMapPolicy,
) {

    @DgsQuery
    fun mapCells(dfe: DgsDataFetchingEnvironment): List<MapCellView> =
        nationwideMapUseCase.mapCells(
            locale = dfe.domainLocale(),
            // 비로그인 허용 — 모든 칸이 미방문으로 나온다(requireMemberId 아님).
            memberId = currentMemberProvider.currentMemberId(),
        )

    @DgsQuery
    fun mapCell(dfe: DgsDataFetchingEnvironment, @InputArgument id: Long): MapCellView =
        nationwideMapUseCase.mapCell(
            cellId = id,
            locale = dfe.domainLocale(),
            memberId = currentMemberProvider.currentMemberId(),
        )

    @DgsQuery
    fun sharedMap(@InputArgument token: String): GqlSharedMap {
        val view = nationwideMapUseCase.sharedMap(
            token = token,
            viewerMemberId = currentMemberProvider.currentMemberId(),
        )
        return GqlSharedMap(
            ownerNickname = view.ownerNickname,
            visitedCellIds = view.visitedCellIds,
            overlappingCellIds = view.overlappingCellIds,
            visitedCount = view.visitedCount,
        )
    }

    /**
     * 칸의 대표 좌표.
     *
     * **도메인 `Coordinates`를 그대로 노출할 수 없다.** 그 값 객체는 `latitude`/`longitude`인데
     * 스키마는 `lat`/`lng`이라, DTO를 그냥 내보내면 DGS가 필드를 찾지 못해 null이 되고
     * `Coordinates!`가 non-null이라 **응답 전체가 null로 무너진다**(실측: 161칸 × 2필드 = 322 오류).
     *
     * 조회를 하지 않으므로 지연 필드는 아니다 — 이름이 어긋난 변환을 태우려고 여기 둔다.
     */
    @DgsData(parentType = "MapCell", field = "center")
    fun center(dfe: DgsDataFetchingEnvironment): GqlCoordinates = dfe.mapCell().center.toGql()

    /**
     * 칸의 대표 이미지(R9). **선택했을 때만 조회한다.**
     *
     * 161칸을 각각 부르면 161왕복이 되므로 요청 스코프에 한 번 모아 담는다 — `mapCells`가
     * 이 필드를 포함하면 칸 전체의 이미지를 한 질의로 읽고 그 뒤로는 맵에서 꺼낸다.
     */
    @DgsData(parentType = "MapCell", field = "representativeImage")
    fun representativeImage(dfe: DgsDataFetchingEnvironment): GqlCellImage? {
        val cell = dfe.mapCell()
        return CellImageBatchLoader.load(dfe, cell.id, nationwideMapUseCase)?.let {
            GqlCellImage(
                imageUrl = it.imageUrl,
                thumbnailUrl = it.thumbnailUrl,
                caption = it.caption,
                kind = GqlCellImageKind.valueOf(it.kind.name),
                copyright = GqlCopyrightType.valueOf(it.copyright.name),
                attributionText = it.attributionText,
            )
        }
    }

    /** 칸에 속한 장소(R11). **선택했을 때만 조회한다.** */
    @DgsData(parentType = "MapCell", field = "spots")
    fun spots(
        dfe: DgsDataFetchingEnvironment,
        @InputArgument first: Int?,
        @InputArgument after: String?,
    ): GqlCellSpotConnection {
        val page = nationwideMapUseCase.cellSpots(
            CellSpotsQuery(
                cellId = dfe.mapCell().id,
                first = first ?: DEFAULT_CELL_SPOT_PAGE_SIZE,
                after = after,
            ),
            locale = dfe.domainLocale(),
        )
        return GqlCellSpotConnection(
            edges = page.edges.map {
                GqlCellSpotEdge(
                    node = it.node.toGql(),
                    cursor = it.cursor,
                    likeCount = it.node.likeCount.toInt(),
                )
            },
            pageInfo = page.toGqlPageInfo(),
        )
    }

    /**
     * 부모가 돌려준 읽기 DTO를 꺼낸다.
     *
     * `!!` 대신 명시적으로 실패시킨다 — 부모 리졸버가 반환한 값이라 실제로 null이 될 수 없지만,
     * 그 보장은 DGS의 실행 순서에 달려 있어 타입이 증명하지 못한다.
     */
    private fun DgsDataFetchingEnvironment.mapCell(): MapCellView =
        getSource<MapCellView>()
            ?: error("MapCell 필드 리졸버는 부모가 반환한 MapCellView를 소스로 받아야 한다.")

    private companion object {
        /**
         * 스키마의 `first: Int = 20`이 실제로 동작하는 기본값이다 — GraphQL이 리졸버에 닿기 전에
         * 채워 넣으므로 이 상수는 인자가 명시적으로 null일 때만 쓰인다. 설정값으로 두지 않는
         * 이유가 그것이다: 설정을 바꿔도 스키마 기본값이 이기므로 조용히 아무 일도 없다.
         */
        const val DEFAULT_CELL_SPOT_PAGE_SIZE = 20
    }

}
