package org.around.ieuri.tourism.application.port.out.query

import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory

/**
 * 카탈로그 분할 후보를 찾는 읽기 포트(SPEC-0029).
 *
 * **PostGIS `ST_Distance(...::geography)`가 필요해 포트가 갈린다** — 원본 간 최대 거리는
 * QueryDSL로 표현할 수 없다. 다른 카탈로그 읽기([SpotCatalogQueryPort])는 전부 QueryDSL이라
 * 한 포트에 섞으면 어느 경로가 생 SQL인지 애플리케이션에서 보이지 않는다.
 */
interface SpotCatalogSpatialQueryPort {

    /**
     * 서로 다른 장소가 한 카탈로그로 접힌 것으로 보이는 카탈로그 id.
     *
     * 두 조건을 **함께** 만족해야 한다. 언어가 4개뿐이라 원본이 그보다 많으면 이미 무언가
     * 접힌 것이고, 그중 실제로 다른 장소인 것만 거리로 가른다. 어느 한쪽만으로는 오탐이 난다 —
     * 행 수만 보면 상류가 같은 장소를 중복 등록한 경우가 걸리고(실측 6건), 거리만 보면
     * 한라산처럼 정당하게 넓은 장소가 걸린다(실측 32건).
     *
     * @param minOriginCount 이 수를 **초과**하는 원본을 가진 카탈로그만 본다.
     * @param minDistanceMeters 원본 간 최대 거리가 이 값 **이상**이어야 후보다.
     */
    fun findSplitCandidates(minOriginCount: Int, minDistanceMeters: Double): List<Long>

    /**
     * 한 카탈로그에 매달린 원본 전부. 군집을 나누고 새 카탈로그의 표준값을 정하는 근거다.
     *
     * **좌표가 유효한 행만 온다** — 배정이 그런 행만 붙이므로 여기서도 같은 기준이다.
     */
    fun findOrigins(catalogId: Long): List<CatalogOrigin>
}

/**
 * 카탈로그에 매달린 원본 spot 한 건.
 *
 * 분할이 이 값들로 새 카탈로그의 표준값을 만들므로 [UnassignedSpot]과 담는 것이 거의 같다.
 * 다른 점은 [spotId]가 이미 배정되어 있다는 것뿐이라 별도 타입으로 둔다 — 같은 타입을
 * 재사용하면 "미배정"이라는 이름이 거짓이 된다.
 */
data class CatalogOrigin(
    val spotId: Long,
    val language: DomainLocale,
    val title: String,
    val address: String?,
    val thumbnailImageUrl: String,
    val mapX: Double,
    val mapY: Double,
    val category: SpotCategory,
    val copyright: CopyrightType,
)
