package org.around.ieuri.tourism.application.shared

import org.around.ieuri.common.persistence.ExposedConfig
import org.around.ieuri.tourism.application.shared.MapViewportPolicy
import org.around.ieuri.tourism.application.shared.SeoulOpenDataPolicy
import org.around.ieuri.tourism.application.shared.provided.MapQueryUseCase
import org.around.ieuri.tourism.application.shared.provided.dto.CongestionAreaBoundaryView
import org.around.ieuri.tourism.application.shared.provided.dto.CongestionAreaView
import org.around.ieuri.tourism.application.shared.provided.dto.CulturalEventView
import org.around.ieuri.tourism.application.shared.provided.dto.MapSpotView
import org.around.ieuri.tourism.application.shared.provided.dto.MapViewportQuery
import org.around.ieuri.tourism.application.shared.provided.dto.MapViewportView
import org.around.ieuri.tourism.application.shared.provided.dto.SeoulCongestionView
import org.around.ieuri.tourism.application.shared.provided.exception.AreaFilterRequiredException
import org.around.ieuri.tourism.application.shared.provided.exception.BboxTooLargeException
import org.around.ieuri.tourism.application.shared.provided.exception.CongestionAreaNotFoundException
import org.around.ieuri.tourism.application.shared.provided.exception.InvalidZoomException
import org.around.ieuri.tourism.application.shared.provided.exception.TooManyAreaIdsException
import org.around.ieuri.tourism.application.shared.required.CongestionAreaQueryPort
import org.around.ieuri.tourism.application.shared.required.MapQueryPort
import org.around.ieuri.tourism.application.shared.required.MapViewportSearch
import org.around.ieuri.tourism.application.shared.required.SeoulOpenDataCachePort
import org.around.ieuri.tourism.application.shared.required.SeoulOpenDataPort
import org.around.ieuri.tourism.application.shared.required.SeoulOpenDataSnapshot
import org.around.ieuri.tourism.domain.shared.AreaName
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.around.ieuri.tourism.domain.shared.MapBounds
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime

/**
 * 지도 조회 유스케이스 구현(SPEC-0014).
 * - 업스트림 장애는 예외가 아니라 degraded로 표현한다(R11). 입력 검증 실패는 예외다.
 */
@Service
@Transactional(transactionManager = ExposedConfig.EXPOSED_TX, readOnly = true)
class MapQueryService(
    private val mapQueryPort: MapQueryPort,
    private val congestionAreaQueryPort: CongestionAreaQueryPort,
    private val seoulOpenDataPort: SeoulOpenDataPort,
    private val cachePort: SeoulOpenDataCachePort,
    private val viewportPolicy: MapViewportPolicy,
    private val openDataPolicy: SeoulOpenDataPolicy,
    private val clock: Clock,
) : MapQueryUseCase {

    /** 뷰포트 마커. 문화행사는 캐시된 값만 쓰고 업스트림을 부르지 않는다. */
    override fun mapViewport(query: MapViewportQuery, locale: DomainLocale): MapViewportView {
        if (!viewportPolicy.isValidZoom(query.zoom)) {
            throw InvalidZoomException("지도 확대 수준이 올바르지 않습니다.")
        }
        if (query.bounds.areaInSquareDegrees > viewportPolicy.maxAreaInSquareDegrees) {
            throw BboxTooLargeException("지도를 확대해 주세요.")
        }

        val events = cachedEventsWithin(query.bounds)
        val eventsTruncated = events.size > viewportPolicy.maxEventMarkers

        val spots = mapQueryPort.findSpotsWithin(
            MapViewportSearch(
                language = locale,
                bounds = query.bounds,
                categories = query.categories,
                // 상한 + 1건을 받아 초과 여부만 판정한다(R3).
                limit = viewportPolicy.maxSpotMarkers + 1,
            ),
        )
        return MapViewportView(
            spots = spots.take(viewportPolicy.maxSpotMarkers),
            events = events.take(viewportPolicy.maxEventMarkers),
            truncated = spots.size > viewportPolicy.maxSpotMarkers || eventsTruncated,
        )
    }

    /**
     * 실시간 혼잡도. 구역 지정(areaIds 또는 bbox)이 필수다(R6).
     * - `areaIds`가 주어지면 bbox는 무시한다.
     * - bbox에 걸치는 구역이 없으면 오류가 아니라 빈 결과다.
     */
    override fun seoulCongestion(
        areaIds: List<String>?,
        bounds: MapBounds?,
        locale: DomainLocale,
    ): SeoulCongestionView {
        val (requestedCodes, names) = resolveAreas(areaIds, bounds)
        if (requestedCodes.isEmpty()) {
            return SeoulCongestionView(areas = emptyList(), degraded = false, staleSince = null)
        }

        val areas = mutableListOf<CongestionAreaView>()
        var degraded = false
        var staleSince: LocalDateTime? = null

        for (code in requestedCodes) {
            val name = names[code] ?: continue
            // 업스트림은 국문명으로 조회한다 — 활성 언어와 무관하게 항상 ko를 넘긴다.
            val resolved = resolveSnapshot(code, name.ko)
            val snapshot = resolved.snapshot
            if (resolved.degraded) degraded = true

            val congestion = snapshot?.congestion ?: continue
            if (resolved.stale) {
                // 만료된 캐시면 그 시각을 알린다(R11). 여러 구역이면 가장 오래된 시각을 쓴다.
                staleSince = listOfNotNull(staleSince, congestion.observedAt).minOrNull()
            }
            areas += CongestionAreaView(
                id = code,
                name = name.resolve(locale),
                congestion = congestion,
                forecast = snapshot.forecast,
                commercial = snapshot.commercial,
            )
        }

        return SeoulCongestionView(areas = areas, degraded = degraded, staleSince = staleSince)
    }

    override fun seoulCongestionAreas(locale: DomainLocale): List<CongestionAreaBoundaryView> = congestionAreaQueryPort.findAllBoundaries(locale)

    /**
     * 조회 대상 구역과 그 이름. [areaIds]가 우선하고, 없으면 bbox에 걸치는 구역을 찾는다.
     * - 둘 다 없으면 거절한다(R6). 빈 목록(`areaIds: []`)은 지정으로 보고 빈 결과를 낸다.
     */
    private fun resolveAreas(
        areaIds: List<String>?,
        bounds: MapBounds?,
    ): Pair<List<String>, Map<String, AreaName>> {
        if (areaIds != null) {
            if (areaIds.size > openDataPolicy.maxAreaIds) {
                throw TooManyAreaIdsException(
                    "한 번에 조회할 수 있는 구역은 ${openDataPolicy.maxAreaIds}곳까지입니다.",
                )
            }
            val distinct = areaIds.distinct()
            if (distinct.isEmpty()) return emptyList<String>() to emptyMap()
            // 존재하지 않는 코드는 무시하지 않고 거절한다.
            val known = congestionAreaQueryPort.findNamesByCodes(distinct)
            val unknown = distinct.filterNot(known::containsKey)
            if (unknown.isNotEmpty()) {
                throw CongestionAreaNotFoundException("해당 구역을 찾을 수 없습니다: ${unknown.joinToString()}")
            }
            return distinct to known
        }
        if (bounds != null) {
            val codes = congestionAreaQueryPort.findAreaCodesWithin(bounds)
                .take(openDataPolicy.maxAreaIds)
            if (codes.isEmpty()) return emptyList<String>() to emptyMap()
            return codes to congestionAreaQueryPort.findNamesByCodes(codes)
        }
        throw AreaFilterRequiredException("조회할 구역 또는 지도 영역이 필요합니다.")
    }

    /**
     * 구역 1곳의 값을 캐시 우선으로 해소한다(R13·R14). 신선도는 저장 시각 기준이다.
     * - 업스트림이 실패하면 만료된 캐시라도 돌려주고 degraded를 세운다(R11).
     */
    private fun resolveSnapshot(areaCode: String, areaName: String): ResolvedSnapshot {
        val cached = cachePort.get(areaCode)
        val now = clock.instant()

        if (cached != null) {
            val age = Duration.between(cached.storedAt, now)
            if (age < openDataPolicy.minRefetchInterval) {
                return ResolvedSnapshot(cached.snapshot, degraded = false, stale = false)
            }
        }

        val fetched = seoulOpenDataPort.fetch(areaCode, areaName)
        if (fetched == null) {
            // 업스트림 실패. 캐시가 있으면 낡은 값이라도 주고, 없으면 그 구역은 빠진다.
            return ResolvedSnapshot(cached?.snapshot, degraded = true, stale = cached != null)
        }

        cachePort.put(areaCode, fetched, now)
        return ResolvedSnapshot(fetched, degraded = false, stale = false)
    }

    /**
     * bbox에 걸치는 구역의 캐시된 문화행사(R12). 업스트림을 부르지 않는다.
     * - 캐시가 비어 있으면 행사도 비어 있다(혼잡도 조회가 채운다).
     */
    private fun cachedEventsWithin(bounds: MapBounds): List<CulturalEventView> = congestionAreaQueryPort.findAreaCodesWithin(bounds)
        .mapNotNull { cachePort.get(it)?.snapshot?.events }
        .flatten()
        .distinctBy { it.id }

    /** 캐시·업스트림 해소 결과. [stale]은 만료된 캐시를 돌려주는 중이라는 뜻이다. */
    private data class ResolvedSnapshot(
        val snapshot: SeoulOpenDataSnapshot?,
        val degraded: Boolean,
        val stale: Boolean,
    )
}
