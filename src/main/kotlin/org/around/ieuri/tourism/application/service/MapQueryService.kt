package org.around.ieuri.tourism.application.service

import org.around.ieuri.tourism.application.exception.AreaFilterRequiredException
import org.around.ieuri.tourism.application.exception.BboxTooLargeException
import org.around.ieuri.tourism.application.exception.CongestionAreaNotFoundException
import org.around.ieuri.tourism.application.exception.InvalidZoomException
import org.around.ieuri.tourism.application.exception.TooManyAreaIdsException
import org.around.ieuri.tourism.application.policy.MapViewportPolicy
import org.around.ieuri.tourism.application.policy.SeoulOpenDataPolicy
import org.around.ieuri.tourism.application.port.`in`.query.MapQueryUseCase
import org.around.ieuri.tourism.application.port.out.cache.SeoulOpenDataCachePort
import org.around.ieuri.tourism.application.port.out.client.SeoulOpenDataPort
import org.around.ieuri.tourism.application.port.out.client.SeoulOpenDataSnapshot
import org.around.ieuri.tourism.application.port.out.query.CongestionAreaQueryPort
import org.around.ieuri.tourism.application.port.out.query.MapQueryPort
import org.around.ieuri.tourism.application.port.out.query.MapViewportSearch
import org.around.ieuri.tourism.application.query.CongestionAreaBoundaryView
import org.around.ieuri.tourism.application.query.CongestionAreaView
import org.around.ieuri.tourism.application.query.CulturalEventView
import org.around.ieuri.tourism.application.query.MapSpotView
import org.around.ieuri.tourism.application.query.MapViewportQuery
import org.around.ieuri.tourism.application.query.MapViewportView
import org.around.ieuri.tourism.application.query.SeoulCongestionView
import org.around.ieuri.tourism.domain.vo.AreaName
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.MapBounds
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime

/**
 * 지도 조회 유스케이스 구현(SPEC-0014, CQRS 읽기).
 *
 * 세 경로의 데이터 출처가 다르다:
 *  - `mapViewport`          : 적재된 spot 테이블(bbox 공간 질의) + 캐시된 문화행사
 *  - `seoulCongestion`      : Valkey → 서울 실시간 API 2단. 실패는 degraded로 표현한다
 *  - `seoulCongestionAreas` : 정적 테이블만. 실시간 의존이 없다
 *
 * **업스트림 장애를 예외로 던지지 않는다**(R11) — 지도 전체가 실패한 것처럼 다뤄지지 않게
 * 데이터로 표현한다. 반대로 입력 검증 실패는 그대로 예외다(§10의 오류 코드).
 */
@Service
class MapQueryService(
    private val mapQueryPort: MapQueryPort,
    private val congestionAreaQueryPort: CongestionAreaQueryPort,
    private val seoulOpenDataPort: SeoulOpenDataPort,
    private val cachePort: SeoulOpenDataCachePort,
    private val viewportPolicy: MapViewportPolicy,
    private val openDataPolicy: SeoulOpenDataPolicy,
    private val clock: Clock,
) : MapQueryUseCase {

    /**
     * 뷰포트 마커.
     *
     * 문화행사는 서울 구역 캐시에서 모은다 — bbox에 걸치는 구역의 캐시된 값만 쓰고
     * 여기서 업스트림을 부르지 않는다. 지도를 움직일 때마다 행사 때문에 쿼터를 태우면
     * 정작 혼잡도를 볼 호출이 남지 않는다.
     */
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
                // 상한 + 1건을 받아 초과 여부만 판정한다. COUNT(*)를 따로 세지 않는다(R3).
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
     * 실시간 혼잡도. **구역 지정이 필수다**(R6) — 벌크 호출이 없어 1곳당 1회 호출이
     * 강제되므로, 범위 없는 전체 조회는 일일 쿼터를 한 번에 소진시킨다.
     *
     * `areaIds`가 주어지면 bbox는 무시한다. bbox 경로에서는 걸치는 구역이 없으면 빈
     * 결과이며 오류가 아니다 — 지도가 서울 밖이면 호출이 0이다.
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
                // 만료된 캐시를 돌려줄 때는 그 데이터의 시각을 알린다(R11). 여러 구역이면
                // 가장 오래된 시각이 응답 전체의 신선도를 대표한다.
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

    override fun seoulCongestionAreas(locale: DomainLocale): List<CongestionAreaBoundaryView> =
        congestionAreaQueryPort.findAllBoundaries(locale)

    /**
     * 조회 대상 구역과 그 이름. [areaIds]가 우선하고, 없으면 bbox에 걸치는 구역을 찾는다.
     * 둘 다 없으면 거절한다(R6).
     *
     * 코드와 이름을 **함께** 돌려주는 이유: areaIds 경로는 존재 검증에 이미 이름을 읽으므로,
     * 코드만 돌려주면 호출자가 같은 질의를 한 번 더 하게 된다.
     *
     * 빈 목록(`areaIds: []`)도 지정으로 본다 — 명시적으로 아무 구역도 요청하지 않은 것이라
     * 전체 조회와 다르다.
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
            // 존재하지 않는 코드는 조용히 무시하지 않는다 — 클라이언트가 오래된 구역 목록을
            // 들고 있다는 신호이며, 빈 응답으로 돌려주면 그 사실이 드러나지 않는다.
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
     * 구역 1곳의 값을 캐시 우선으로 해소한다(R13·R14).
     *
     * 판정은 저장 시각 기준이다 — 업스트림 데이터가 약 30분 지연되어 도착하므로 "5분
     * 경계까지 TTL"로 잡으면 같은 데이터를 반복 호출한다(spec §4.2).
     *
     * 업스트림이 실패하면 **만료된 캐시라도 돌려주고** degraded를 세운다(R11).
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
     * bbox에 걸치는 구역의 **캐시된** 문화행사(R12). 업스트림을 부르지 않는다 — 지도를
     * 움직일 때마다 행사 때문에 쿼터를 태우면 혼잡도를 볼 호출이 남지 않는다.
     *
     * 캐시가 비어 있으면 행사도 비어 있다. 혼잡도를 한 번이라도 조회한 구역은 같은 호출로
     * 행사까지 받아 두므로(seoulOpenData 1콜에 세 블록) 실사용에서는 대개 채워진다.
     */
    private fun cachedEventsWithin(bounds: MapBounds): List<CulturalEventView> =
        congestionAreaQueryPort.findAreaCodesWithin(bounds)
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
