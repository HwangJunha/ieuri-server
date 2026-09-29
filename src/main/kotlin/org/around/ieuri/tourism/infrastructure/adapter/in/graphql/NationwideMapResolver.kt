package org.around.ieuri.tourism.infrastructure.adapter.`in`.graphql

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsData
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import org.around.ieuri.common.graphql.loader
import org.around.ieuri.common.graphql.tokenPresentation
import org.around.ieuri.common.security.CurrentMemberProvider
import org.around.ieuri.tourism.application.policy.NationwideMapPolicy
import org.around.ieuri.tourism.application.port.`in`.query.NationwideMapUseCase
import org.around.ieuri.tourism.application.query.CellImageView
import org.around.ieuri.tourism.application.query.CellSpotView
import org.around.ieuri.tourism.application.query.MapCellView
import org.around.ieuri.tourism.application.query.SharedMapView
import org.around.ieuri.tourism.infrastructure.adapter.`in`.graphql.mapper.toGql
import org.around.ieuri.tourism.infrastructure.locale.domainLocale
import java.util.concurrent.CompletableFuture
import org.around.ieuri.common.graphql.generated.types.CellImage as GqlCellImage
import org.around.ieuri.common.graphql.generated.types.CellSpot as GqlCellSpot
import org.around.ieuri.common.graphql.generated.types.Coordinates as GqlCoordinates

/**
 * 전국 지도 조회 리졸버(SPEC-0030). 루트는 읽기 DTO를 반환하고 비싼 필드는 선택됐을 때만 각자 조회한다.
 * - `mapCells`·`mapCell`·`sharedMap`은 비인증을 허용한다.
 */
@DgsComponent
class NationwideMapResolver(
    private val nationwideMapUseCase: NationwideMapUseCase,
    private val currentMemberProvider: CurrentMemberProvider,
) {

    @DgsQuery
    fun mapCells(dfe: DgsDataFetchingEnvironment): List<MapCellView> = nationwideMapUseCase.mapCells(
        locale = dfe.domainLocale(),
        // 비로그인 허용 — 모든 칸이 미방문이다. 만료 토큰은 오류다(R1).
        memberId = dfe.viewerMemberId(),
    )

    @DgsQuery
    fun mapCell(dfe: DgsDataFetchingEnvironment, @InputArgument id: Long): MapCellView = nationwideMapUseCase.mapCell(
        cellId = id,
        locale = dfe.domainLocale(),
        memberId = dfe.viewerMemberId(),
    )

    /** 읽기 DTO를 돌려준다 — `overlappingCells`가 지연 필드다. */
    @DgsQuery
    fun sharedMap(dfe: DgsDataFetchingEnvironment, @InputArgument token: String): SharedMapView = nationwideMapUseCase.sharedMap(
        token = token,
        // 만료 토큰은 오류다(R1).
        viewerMemberId = dfe.viewerMemberId(),
    )

    /** 겹치는 칸(선택했을 때만 조회). 비인증이면 null이며 빈 배열(겹침 없음)과 구분된다. */
    @DgsData(parentType = "SharedMap", field = "overlappingCells")
    fun overlappingCells(dfe: DgsDataFetchingEnvironment): List<MapCellView>? {
        val viewerMemberId = dfe.viewerMemberId()
        val ids = dfe.getSource<SharedMapView>()?.overlappingCellIds ?: return null
        return nationwideMapUseCase.mapCellsByIds(
            cellIds = ids,
            locale = dfe.domainLocale(),
            memberId = viewerMemberId,
        )
    }

    /**
     * 칸의 대표 좌표. 도메인 `Coordinates`(`latitude`/`longitude`)를 스키마의 `lat`/`lng`로 옮긴다.
     * - 그대로 내보내면 non-null 필드가 null이 되어 응답 전체가 무너진다.
     */
    @DgsData(parentType = "MapCell", field = "center")
    fun center(dfe: DgsDataFetchingEnvironment): GqlCoordinates = dfe.mapCell().center.toGql()

    /** 칸의 대표 이미지(R9, 선택했을 때만 조회). 요청 스코프 로더가 칸 전체를 한 질의로 읽는다. */
    @DgsData(parentType = "MapCell", field = "representativeImage")
    fun representativeImage(dfe: DgsDataFetchingEnvironment): CompletableFuture<GqlCellImage?> = dfe.loader<CellImageView>(CellImageDataLoader.NAME)
        .load(dfe.mapCell().id)
        // 이미지가 없는 칸은 null이다(SPEC-0030 R10).
        .thenApply { it?.toGql() }

    /** 칸에 속한 장소(R11, 선택했을 때만 조회). 페이지네이션이 없다. */
    @DgsData(parentType = "MapCell", field = "spots")
    fun spots(
        dfe: DgsDataFetchingEnvironment,
        @InputArgument first: Int?,
    ): List<GqlCellSpot> = nationwideMapUseCase.cellSpots(
        cellId = dfe.mapCell().id,
        first = first ?: DEFAULT_CELL_SPOT_PAGE_SIZE,
        locale = dfe.domainLocale(),
    ).map { it.toGql() }

    /** 뷰어 신원. 비로그인은 null, 만료 토큰은 오류다(SPEC-0032 R1). */
    private fun DgsDataFetchingEnvironment.viewerMemberId(): Long? = currentMemberProvider.optionalMemberId(tokenPresentation())

    /** 부모가 돌려준 읽기 DTO를 꺼낸다. 없으면 명시적으로 실패한다. */
    private fun DgsDataFetchingEnvironment.mapCell(): MapCellView = getSource<MapCellView>()
        ?: error("MapCell 필드 리졸버는 부모가 반환한 MapCellView를 소스로 받아야 한다.")

    private companion object {
        /** 인자가 명시적 null일 때만 쓰인다 — 실제 기본값은 스키마의 `first: Int = 20`이다. */
        const val DEFAULT_CELL_SPOT_PAGE_SIZE = 20
    }
}
