package org.around.ieuri.tourism.application.congestion

import org.around.ieuri.tourism.application.congestion.provided.CongestionAreaReadUseCase
import org.around.ieuri.tourism.application.congestion.provided.CongestionQueryUseCase
import org.around.ieuri.tourism.application.congestion.provided.dto.CongestionAreaView
import org.around.ieuri.tourism.application.congestion.provided.dto.CulturalEventView
import org.around.ieuri.tourism.application.congestion.provided.dto.SeoulCongestionView
import org.around.ieuri.tourism.application.congestion.required.SeoulOpenDataCachePort
import org.around.ieuri.tourism.application.congestion.required.SeoulOpenDataPort
import org.around.ieuri.tourism.application.congestion.required.SeoulOpenDataSnapshot
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.around.ieuri.tourism.domain.shared.MapBounds
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime

/**
 * 서울 실시간 혼잡도 조회 구현(SPEC-0014). 캐시 우선이며, 신선도는 저장 시각 기준이다.
 * - 트랜잭션이 없다 — 구역 조회는 [CongestionAreaReadUseCase]가 짧게 열고, Valkey·서울 열린데이터는 연결을 잡지 않고 부른다(D-83).
 * - 업스트림 장애는 예외가 아니라 degraded로 표현한다(R11). 입력 검증 실패는 예외다.
 */
@Service
class CongestionQueryService(
    private val areas: CongestionAreaReadUseCase,
    private val seoulOpenDataPort: SeoulOpenDataPort,
    private val cachePort: SeoulOpenDataCachePort,
    private val openDataPolicy: SeoulOpenDataPolicy,
    private val clock: Clock,
) : CongestionQueryUseCase {

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
        val requested = areas.requestedAreas(areaIds, bounds)
        if (requested.isEmpty()) {
            return SeoulCongestionView(areas = emptyList(), degraded = false, staleSince = null)
        }

        val views = mutableListOf<CongestionAreaView>()
        var degraded = false
        var staleSince: LocalDateTime? = null

        for ((code, name) in requested) {
            // 업스트림은 국문명으로 조회한다 — 활성 언어와 무관하게 항상 ko를 넘긴다.
            val resolved = resolveSnapshot(code, name.ko)
            val snapshot = resolved.snapshot
            if (resolved.degraded) degraded = true

            val congestion = snapshot?.congestion ?: continue
            if (resolved.stale) {
                // 만료된 캐시면 그 시각을 알린다(R11). 여러 구역이면 가장 오래된 시각을 쓴다.
                staleSince = listOfNotNull(staleSince, congestion.observedAt).minOrNull()
            }
            views += CongestionAreaView(
                id = code,
                name = name.resolve(locale),
                congestion = congestion,
                forecast = snapshot.forecast,
                commercial = snapshot.commercial,
            )
        }

        return SeoulCongestionView(areas = views, degraded = degraded, staleSince = staleSince)
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
    override fun cachedEventsWithin(bounds: MapBounds): List<CulturalEventView> = areas.areaCodesWithin(bounds)
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
