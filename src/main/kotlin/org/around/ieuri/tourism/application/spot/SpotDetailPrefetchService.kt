package org.around.ieuri.tourism.application.spot

import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.ratelimiter.RateLimiterConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.around.ieuri.tourism.application.shared.exception.UpstreamRateLimitedException
import org.around.ieuri.tourism.application.spot.provided.PrefetchResult
import org.around.ieuri.tourism.application.spot.provided.SpotCommandUseCase
import org.around.ieuri.tourism.application.spot.provided.SpotDetailPrefetchUseCase
import org.around.ieuri.tourism.application.spot.provided.SpotPrefetchQueryUseCase
import org.around.ieuri.tourism.application.spot.required.PrefetchTarget
import org.around.ieuri.tourism.application.spot.required.SpotDetailPart
import org.around.ieuri.tourism.application.spot.required.SpotDetailQuery
import org.around.ieuri.tourism.application.spot.required.TourInfoPort
import org.around.ieuri.tourism.domain.spot.SpotDetail
import org.around.ieuri.tourism.domain.spot.SpotImage
import org.around.ieuri.tourism.domain.spot.SpotInfoItem
import org.around.ieuri.tourism.domain.spot.SpotIntro
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant

/**
 * 상세 사전 수집(SPEC-0037). 대상 → 조회 → 적재를 한 묶음씩 처리한다.
 * - 상류 호출을 끼고 돌아 자기 트랜잭션이 없다. 대상 조회와 갈래별 적재가 각자 트랜잭션이다.
 * - 실패를 그 자리에서 다시 시도하지 않는다(FR-008) — 다음 확보 차례에 대상 쿼리가 다시 집는다.
 * - 빈 등록은 `SpotDetailPrefetchConfig`가 한다(batch 프로파일 전용). `@Service`를 붙이지 않는다.
 */
class SpotDetailPrefetchService(
    private val prefetchQueryUseCase: SpotPrefetchQueryUseCase,
    private val tourInfoPort: TourInfoPort,
    private val spotCommandUseCase: SpotCommandUseCase,
    private val policy: SpotDetailPrefetchPolicy,
) : SpotDetailPrefetchUseCase {

    private val log = LoggerFactory.getLogger(SpotDetailPrefetchService::class.java)

    /** 갈래(= 오퍼레이션)마다 따로 둔다 — upstream 한도가 오퍼레이션별이다. */
    private val limiters: Map<SpotDetailPart, RateLimiter> =
        SpotDetailPart.entries.associateWith { part ->
            RateLimiter.of(
                "spot-detail-prefetch-$part",
                RateLimiterConfig.custom()
                    .limitForPeriod(policy.callsPerSecond)
                    .limitRefreshPeriod(Duration.ofSeconds(1))
                    // 마지막 호출은 (묶음 크기 / 초당 한도)초까지 기다릴 수 있다.
                    .timeoutDuration(Duration.ofMinutes(1))
                    .build(),
            )
        }

    override fun prefetchNext(part: SpotDetailPart, afterSpotId: Long, limit: Int): PrefetchResult {
        val staleBefore = Instant.now().minus(policy.refreshAfter)
        val targets = prefetchQueryUseCase.findTargets(part, afterSpotId, limit, staleBefore)
        if (targets.isEmpty()) return PrefetchResult(processed = 0, failed = 0, lastSpotId = afterSpotId)

        val fetched = runBlocking { fetchAll(part, targets) }
        targets.zip(fetched).forEach { (target, detail) -> detail?.let { save(target, it) } }

        return PrefetchResult(
            processed = targets.size,
            failed = fetched.count { it == null },
            lastSpotId = targets.last().spotId,
        )
    }

    /**
     * 묶음 전체를 동시에 받아 온다. 초당 호출 수는 [RateLimiter]가 잡는다.
     * - 한 장소가 [UpstreamRateLimitedException]을 던지면 나머지를 취소하고 그 예외를 올린다(AC-018).
     */
    private suspend fun fetchAll(part: SpotDetailPart, targets: List<PrefetchTarget>): List<Detail?> = coroutineScope {
        val limiter = limiters.getValue(part)
        targets
            .map { target ->
                async(Dispatchers.IO) {
                    // 창이 열릴 때까지 대기한다(스레드를 잡는다).
                    limiter.acquirePermission()
                    fetch(part, target)
                }
            }
            .awaitAll()
    }

    /**
     * 한 장소의 한 갈래를 원본에서 받아 온다. 실패하면 null이며 적재하지 않는다(FR-012).
     * - 일일 한도 예외는 잡지 않고 올려 호출자가 그 갈래를 멈춘다(AC-018).
     */
    private fun fetch(part: SpotDetailPart, target: PrefetchTarget): Detail? = try {
        read(part, target)
    } catch (e: UpstreamRateLimitedException) {
        throw e
    } catch (e: RuntimeException) {
        log.warn("상세 확보 실패: part={} spotId={} cause={}", part, target.spotId, e.javaClass.simpleName)
        null
    }

    /** 갈래별 조회. 빈 응답도 결과다 — "물었고 없었다"를 적재해야 미확보와 구분된다(AC-005). */
    private fun read(part: SpotDetailPart, target: PrefetchTarget): Detail {
        val query = SpotDetailQuery(target.contentId, target.language)
        return when (part) {
            SpotDetailPart.BODY -> Detail.Body(tourInfoPort.findDetail(query))
            SpotDetailPart.GALLERY -> Detail.Gallery(tourInfoPort.findGallery(query))
            SpotDetailPart.INTRO -> Detail.Intro(tourInfoPort.findIntro(query, target.category))
            SpotDetailPart.INFO -> Detail.Info(tourInfoPort.findInfoItems(query, target.category))
        }
    }

    /** 갈래별 적재. 본문 부재는 원본 값으로 채운 행으로 남는다(SPEC-0008 R-2). */
    private fun save(target: PrefetchTarget, detail: Detail) = when (detail) {
        is Detail.Body ->
            if (detail.value != null) {
                spotCommandUseCase.saveDetail(target.spotId, detail.value)
            } else {
                spotCommandUseCase.saveDetailAbsence(target.spotId, target.absentDetail())
            }

        is Detail.Gallery -> spotCommandUseCase.saveGallery(target.spotId, detail.value)

        is Detail.Intro -> spotCommandUseCase.saveIntro(target.spotId, target.category, detail.value)

        is Detail.Info -> spotCommandUseCase.saveInfoItems(target.spotId, target.category, detail.value)
    }

    /** 원본에서 받아 온 것. 갈래마다 타입이 달라 나뉘며, 적재까지만 나른다. */
    private sealed interface Detail {
        data class Body(val value: SpotDetail?) : Detail
        data class Gallery(val value: List<SpotImage>) : Detail
        data class Intro(val value: SpotIntro?) : Detail
        data class Info(val value: List<SpotInfoItem>) : Detail
    }
}
