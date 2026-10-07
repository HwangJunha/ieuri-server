package org.around.ieuri.tourism.adapter.graphql.resolver

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import org.around.ieuri.tourism.adapter.graphql.mapper.toBounds
import org.around.ieuri.tourism.adapter.graphql.mapper.toCategorySelection
import org.around.ieuri.tourism.adapter.graphql.mapper.toGql
import org.around.ieuri.tourism.adapter.graphql.support.domainLocale
import org.around.ieuri.tourism.application.catalog.provided.SpotCatalogQueryUseCase
import org.around.ieuri.tourism.application.catalog.provided.dto.MapViewportQuery
import org.around.ieuri.tourism.application.congestion.provided.CongestionQueryUseCase
import org.around.ieuri.common.graphql.generated.types.CongestionAreaBoundary as GqlCongestionAreaBoundary
import org.around.ieuri.common.graphql.generated.types.MapBoundsInput as GqlMapBoundsInput
import org.around.ieuri.common.graphql.generated.types.MapViewportResult as GqlMapViewportResult
import org.around.ieuri.common.graphql.generated.types.SeoulCongestionResult as GqlSeoulCongestionResult

/**
 * 지도 조회 GraphQL 리졸버(CQRS 읽기, SPEC-0014). 언어는 Accept-Language 헤더에서 읽는다.
 * - 세 쿼리는 각자 I/O가 하나로 묶여 지연 필드가 없다.
 * - 세 쿼리를 별개 루트로 두어 혼잡도의 업스트림 장애가 spot·문화행사 마커에 번지지 않는다.
 */
@DgsComponent
class MapQueryResolver(
    private val catalogQueryUseCase: SpotCatalogQueryUseCase,
    private val congestionQueryUseCase: CongestionQueryUseCase,
) {

    @DgsQuery
    fun mapViewport(
        dfe: DgsDataFetchingEnvironment,
        @InputArgument bbox: GqlMapBoundsInput,
        @InputArgument zoom: Int,
        @InputArgument categories: List<String>?,
    ): GqlMapViewportResult = catalogQueryUseCase.mapViewport(
        MapViewportQuery(
            bounds = bbox.toBounds(),
            zoom = zoom,
            categories = toCategorySelection(categories),
        ),
        locale = dfe.domainLocale(),
    ).toGql()

    @DgsQuery
    fun seoulCongestion(
        dfe: DgsDataFetchingEnvironment,
        @InputArgument areaIds: List<String>?,
        @InputArgument bbox: GqlMapBoundsInput?,
    ): GqlSeoulCongestionResult = congestionQueryUseCase.seoulCongestion(
        areaIds = areaIds,
        bounds = bbox?.toBounds(),
        locale = dfe.domainLocale(),
    ).toGql()

    @DgsQuery
    fun seoulCongestionAreas(dfe: DgsDataFetchingEnvironment): List<GqlCongestionAreaBoundary> = congestionQueryUseCase.seoulCongestionAreas(dfe.domainLocale()).map { it.toGql() }
}
