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
) {

    @DgsQuery
    fun mapCells(dfe: DgsDataFetchingEnvironment): List<MapCellView> = nationwideMapUseCase.mapCells(
        locale = dfe.domainLocale(),
        // 비로그인 허용 — 모든 칸이 미방문으로 나온다(requireMemberId 아님). 다만 만료
        // 토큰은 비로그인으로 강등하지 않는다(R1) — 그러면 방문 칸이 조용히 사라진다.
        memberId = dfe.viewerMemberId(),
    )

    @DgsQuery
    fun mapCell(dfe: DgsDataFetchingEnvironment, @InputArgument id: Long): MapCellView = nationwideMapUseCase.mapCell(
        cellId = id,
        locale = dfe.domainLocale(),
        memberId = dfe.viewerMemberId(),
    )

    /**
     * 생성 타입이 아니라 읽기 DTO를 돌려준다 — `overlappingCells`가 지연 필드이기 때문이다.
     * 생성 타입은 그 필드까지 생성자에서 요구해서 비워 둘 수 없다.
     */
    @DgsQuery
    fun sharedMap(dfe: DgsDataFetchingEnvironment, @InputArgument token: String): SharedMapView = nationwideMapUseCase.sharedMap(
        token = token,
        // 겹친 칸은 뷰어 신원으로 계산된다 — 만료를 비로그인으로 다루면 조용히 사라진다(R1).
        viewerMemberId = dfe.viewerMemberId(),
    )

    /**
     * 겹치는 칸의 이름까지 필요한 화면을 위한 필드. **선택했을 때만 조회한다.**
     *
     * 비인증이면 null이다 — `overlappingCellIds`와 같은 규약이라, 빈 배열(겹침 없음)과
     * 구분된다.
     */
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
    fun representativeImage(dfe: DgsDataFetchingEnvironment): CompletableFuture<GqlCellImage?> = dfe.loader<CellImageView>(CellImageDataLoader.NAME)
        .load(dfe.mapCell().id)
        // 이미지가 없는 칸은 로더가 키를 채우지 않는다 — 필드가 null이 된다(SPEC-0030 R10).
        .thenApply { it?.toGql() }

    /**
     * 칸에 속한 장소(R11). **선택했을 때만 조회한다.**
     *
     * 페이지네이션이 없다 — 한 칸의 장소는 한 화면에 담기는 양이라 나눌 이유가 없고,
     * 칸마다 커서를 들고 다니면 클라이언트가 161칸분의 커서를 관리하게 된다.
     */
    @DgsData(parentType = "MapCell", field = "spots")
    fun spots(
        dfe: DgsDataFetchingEnvironment,
        @InputArgument first: Int?,
    ): List<GqlCellSpot> = nationwideMapUseCase.cellSpots(
        cellId = dfe.mapCell().id,
        first = first ?: DEFAULT_CELL_SPOT_PAGE_SIZE,
        locale = dfe.domainLocale(),
    ).map { it.toGql() }

    /**
     * 뷰어 신원. **비로그인은 null이지만 만료 토큰은 오류다**(SPEC-0032 R1) — 지도 자체는
     * 로그인 없이 보이되, 만료를 비로그인으로 다루면 방문 칸이 조용히 사라진다.
     */
    private fun DgsDataFetchingEnvironment.viewerMemberId(): Long? = currentMemberProvider.optionalMemberId(tokenPresentation())

    /**
     * 부모가 돌려준 읽기 DTO를 꺼낸다.
     *
     * `!!` 대신 명시적으로 실패시킨다 — 부모 리졸버가 반환한 값이라 실제로 null이 될 수 없지만,
     * 그 보장은 DGS의 실행 순서에 달려 있어 타입이 증명하지 못한다.
     */
    private fun DgsDataFetchingEnvironment.mapCell(): MapCellView = getSource<MapCellView>()
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
