package org.around.ieuri.tourism.application.service

import org.around.ieuri.common.pagination.CursorEdge
import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.common.pagination.CursorPageInfo
import org.around.ieuri.tourism.application.exception.CategoryNotAvailableForLocaleException
import org.around.ieuri.tourism.application.exception.InvalidCoordinatesException
import org.around.ieuri.tourism.application.exception.RadiusTooLargeException
import org.around.ieuri.tourism.application.policy.SpotQueryPolicy
import org.around.ieuri.tourism.application.port.`in`.query.SpotQueryUseCase
import org.around.ieuri.tourism.application.port.out.client.NearbySpotData
import org.around.ieuri.tourism.application.port.out.client.NearbySpotQuery
import org.around.ieuri.tourism.application.port.out.client.TourInfoPort
import org.around.ieuri.tourism.application.query.SpotView
import org.around.ieuri.tourism.application.query.SpotsQuery
import org.around.ieuri.tourism.domain.aggregate.Spot
import org.around.ieuri.tourism.domain.vo.Coordinates
import org.around.ieuri.tourism.domain.vo.Distance
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.springframework.stereotype.Service

/**
 * 관광지 조회 유스케이스 구현(CQRS 읽기). 파이프라인 책임 분배(spec §3):
 *  - 좌표/반경/카테고리 검증      : 여기(application)
 *  - TourAPI 호출·필터·정규화·거리 : TourInfoPort 뒤 어댑터
 *  - 정렬((거리, id))·커서 페이지  : 여기(application) — 정렬은 계약(POL-019)
 *
 * 커서는 (거리, contentId) 복합키. upstream(arrange=Q)이 거리순이 아니므로, after 요청 시 그 키보다 큰
 * 항목이 나오도록 upstream을 필요한 만큼(정책 상한 내) 조회·병합해 정렬한 뒤 keyset으로 자른다.
 */
@Service
class SpotQueryService(
    private val tourInfoPort: TourInfoPort,
    private val policy: SpotQueryPolicy,
) : SpotQueryUseCase {

    override fun spots(query: SpotsQuery, locale: DomainLocale): CursorPage<SpotView> {
        val center = validatedCenter(query.latitude, query.longitude)
        validateRadius(query.radiusMeters)
        query.category?.let { validateCategory(it, locale) }

        val first = query.first.coerceIn(1, policy.maxPageSize)
        val afterKey = query.after?.let { SpotCursorCodec.decode(it) }

        // upstream 페이지를 상한까지 병합. 라이브 upstream이라 절대적 누락 방지는 불가하나,
        // 동일 요청 세션 내 커서 진행에서 (거리, id) 키 초과 항목만 이어지므로 중복은 없다(AC-1).
        val merged = LinkedHashMap<String, Spot>() // contentId 기준 dedup(같은 항목 페이지 재출현 방지)
        var pageNo = 1
        var upstreamHasMore = true
        var upstreamTotalCount: Int? = null

        while (pageNo <= policy.maxUpstreamPagesPerCursor && upstreamHasMore) {
            val result = tourInfoPort.findNearby(
                NearbySpotQuery(
                    locale = locale,
                    center = center,
                    radiusMeters = query.radiusMeters,
                    category = query.category,
                    pageNo = pageNo,
                    numOfRows = policy.upstreamPageSize,
                ),
            )
            if (upstreamTotalCount == null) upstreamTotalCount = result.totalCount
            // DTO→Domain: 여기서 거리(도메인 로직)를 채워 Spot으로 조립한다.
            result.spots.forEach { data -> merged.putIfAbsent(data.id, data.toSpot(center)) }
            upstreamHasMore = result.hasMoreUpstreamPages
            pageNo++

            // 커서 초과분을 한 페이지+1개 이상 확보하면 조기 종료(과호출 방지). afterKey 유무 모두 대칭 적용.
            val enough = merged.values.count { afterKey == null || it.sortKey > afterKey } >= first + 1
            if (enough) break
        }

        // 정렬 (거리 ASC, id ASC) — 복합 정렬키.
        val sorted = merged.values.sortedBy { it.sortKey }

        // keyset: afterKey보다 큰 항목만.
        val afterApplied = if (afterKey == null) sorted else sorted.filter { it.sortKey > afterKey }

        val hasNextPage = afterApplied.size > first
        val pageItems = if (hasNextPage) afterApplied.take(first) else afterApplied

        val edges = pageItems.map { spot ->
            CursorEdge(cursor = SpotCursorCodec.encode(spot.sortKey), node = spot.toView())
        }

        val pageInfo = CursorPageInfo(
            hasNextPage = hasNextPage,
            hasPreviousPage = afterKey != null,
            startCursor = edges.firstOrNull()?.cursor,
            endCursor = edges.lastOrNull()?.cursor,
        )
        return CursorPage(edges = edges, pageInfo = pageInfo, totalCount = upstreamTotalCount?.toLong())
    }

    override fun availableCategories(locale: DomainLocale): List<SpotCategory> =
        SpotCategory.availableIn(locale)

    private fun validatedCenter(latitude: Double, longitude: Double): Coordinates =
        try {
            Coordinates(latitude, longitude)
        } catch (e: IllegalArgumentException) {
            throw InvalidCoordinatesException(e.message ?: "유효하지 않은 좌표입니다.")
        }

    private fun validateRadius(radiusMeters: Int) {
        if (radiusMeters <= 0) throw InvalidCoordinatesException("반경은 양수여야 합니다.")
        if (radiusMeters > policy.maxRadiusMeters) {
            throw RadiusTooLargeException("반경은 최대 ${policy.maxRadiusMeters}m까지 허용됩니다.")
        }
    }

    private fun validateCategory(category: SpotCategory, locale: DomainLocale) {
        if (!category.isAvailableIn(locale)) {
            throw CategoryNotAvailableForLocaleException("$category 는 현재 언어에서 제공되지 않습니다.")
        }
    }

    /** 포트 중립 DTO → 도메인 Spot. 거리(도메인 로직)를 사용자 좌표 기준으로 채운다(DTO→Domain). */
    private fun NearbySpotData.toSpot(center: Coordinates): Spot = Spot(
        id = id,
        title = title,
        address = address,
        thumbnailImageUrl = thumbnailImageUrl,
        coordinates = coordinates,
        distance = Distance.between(center, coordinates),
        category = category,
        copyright = copyright,
        attributionText = copyright.attributionText,
    )

    private fun Spot.toView(): SpotView = SpotView(
        id = id,
        title = title,
        address = address,
        thumbnailImageUrl = thumbnailImageUrl,
        distanceMeters = distance.meters,
        category = category,
        copyright = copyright,
        attributionText = attributionText,
        mapX = coordinates.longitude,
        mapY = coordinates.latitude,
    )
}
