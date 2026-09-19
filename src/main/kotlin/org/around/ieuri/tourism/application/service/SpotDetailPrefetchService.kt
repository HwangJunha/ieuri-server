package org.around.ieuri.tourism.application.service

import io.github.resilience4j.ratelimiter.RateLimiter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.around.ieuri.tourism.application.exception.UpstreamRateLimitedException
import org.around.ieuri.tourism.application.policy.SpotDetailPrefetchPolicy
import org.around.ieuri.tourism.application.port.`in`.command.PrefetchResult
import org.around.ieuri.tourism.application.port.`in`.command.SpotDetailPrefetchUseCase
import org.around.ieuri.tourism.application.port.out.client.SpotDetailQuery
import org.around.ieuri.tourism.application.port.out.client.TourInfoPort
import org.around.ieuri.tourism.application.port.out.persistence.SpotDetailUpsertPort
import org.around.ieuri.tourism.application.port.out.query.PrefetchTarget
import org.around.ieuri.tourism.application.port.out.query.SpotDetailPart
import org.around.ieuri.tourism.application.port.out.query.SpotDetailPrefetchTargetQueryPort
import org.around.ieuri.tourism.domain.aggregate.SpotDetail
import org.around.ieuri.tourism.domain.vo.SpotImage
import org.around.ieuri.tourism.domain.vo.SpotInfoItem
import org.around.ieuri.tourism.domain.vo.SpotIntro
import io.github.resilience4j.ratelimiter.RateLimiterConfig
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant

/**
 * 상세 사전 수집(SPEC-0037). 대상 → 조회 → 적재를 한 묶음씩 처리한다.
 *
 * **실패를 그 자리에서 다시 시도하지 않는다**(FR-008). 실패하면 확보 기록이 남지 않아 다음 확보
 * 차례에 대상 쿼리가 다시 집고, 그 사이 사용자가 조회하면 그 시점에 채워진다(FR-010). 실시간
 * 데이터가 아니라 하루 늦어지는 비용이 없고, 재시도는 한 장소에 쿼터를 여러 번 태운다.
 *
 * **조회 경로의 write-behind를 재사용하지 않는다.** 그쪽 리스너는 예외를 삼키고 전용 executor는
 * 큐가 차면 작업을 버린다 — "캐시 충전은 버려도 된다"는 전제 위에 서 있어서다. 여기서는 결과가
 * 곧 데이터이므로 적재를 기다리고 실패를 센다.
 *
 * **빈 등록은 `SpotDetailPrefetchConfig`가 한다**(batch 프로파일 전용). 이 클래스에 `@Service`를
 * 붙이면 API 서버도 생성하려 들지만 정책 빈이 그쪽에 없다.
 */
class SpotDetailPrefetchService(
    private val targetQueryPort: SpotDetailPrefetchTargetQueryPort,
    private val tourInfoPort: TourInfoPort,
    private val upsertPort: SpotDetailUpsertPort,
    private val policy: SpotDetailPrefetchPolicy,
) : SpotDetailPrefetchUseCase {

    private val log = LoggerFactory.getLogger(SpotDetailPrefetchService::class.java)

    /**
     * **갈래마다 따로 둔다** — upstream 한도가 (언어 × 오퍼레이션) 조합마다 초당 50이라
     * 하나를 공유하면 네 갈래가 한 갈래 몫을 나눠 쓰게 된다. 갈래가 곧 오퍼레이션이다.
     */
    private val limiters: Map<SpotDetailPart, RateLimiter> =
        SpotDetailPart.entries.associateWith { part ->
            RateLimiter.of(
                "spot-detail-prefetch-$part",
                RateLimiterConfig.custom()
                    .limitForPeriod(policy.callsPerSecond)
                    .limitRefreshPeriod(Duration.ofSeconds(1))
                    // 한도를 기다리는 것은 정상 동작이다 — 묶음 전체가 창을 나눠 쓰므로
                    // 마지막 호출은 (묶음 크기 / 초당 한도)초까지 기다릴 수 있다.
                    .timeoutDuration(Duration.ofMinutes(1))
                    .build(),
            )
        }

    override fun prefetchNext(part: SpotDetailPart, afterSpotId: Long, limit: Int): PrefetchResult {
        val staleBefore = Instant.now().minus(policy.refreshAfter)
        val targets = targetQueryPort.findTargets(part, afterSpotId, limit, staleBefore)
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
     * 묶음 전체를 동시에 받아 온다. **순차로는 FR-002(1일 이내)를 지킬 수 없다** — 건당 0.2초로
     * 잡아도 18시간이다.
     *
     * 속도는 [RateLimiter]가 잡는다. **동시 실행 수를 묶는 것으로는 부족하다** — 응답이 빠르면
     * 같은 동시성으로도 초당 호출이 얼마든 늘어나고, 실제로 동시 20으로 1000건이 2초에 나가
     * 대부분이 한도에 걸렸다. 제한해야 하는 것은 초당 호출 수다.
     *
     * 한 장소가 [UpstreamRateLimitedException]을 던지면 스코프가 나머지를 취소하고 그 예외를
     * 그대로 올린다 — 그날 쿼터가 끝났으므로 남은 호출은 낭비다(AC-018).
     */
    private suspend fun fetchAll(part: SpotDetailPart, targets: List<PrefetchTarget>): List<Detail?> =
        coroutineScope {
            val limiter = limiters.getValue(part)
            targets
                .map { target ->
                    async(Dispatchers.IO) {
                        // 창이 열릴 때까지 대기한다. 대기는 스레드를 잡지만 Dispatchers.IO의 몫이다.
                        limiter.acquirePermission()
                        fetch(part, target)
                    }
                }
                .awaitAll()
        }

    /**
     * 한 장소의 한 갈래를 원본에서 받아 온다. 실패하면 null이며 **적재하지 않는다** — 답을 듣지
     * 못한 것을 "없다"로 적으면 원본에 내용이 있어도 다음 주기까지 비어 있게 된다(FR-012).
     *
     * **일일 한도는 잡지 않는다.** 그날 쿼터가 끝났다는 뜻이라 이 장소만의 문제가 아니고, 그대로
     * 올라가 호출자가 그 갈래를 멈춘다(AC-018).
     */
    private fun fetch(part: SpotDetailPart, target: PrefetchTarget): Detail? =
        try {
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

    /** 갈래별 적재. 본문 부재는 `spot` 값으로 채운 행으로 남는다(SPEC-0008 R-2). */
    private fun save(target: PrefetchTarget, detail: Detail) = when (detail) {
        is Detail.Body ->
            if (detail.value != null) {
                upsertPort.saveDetail(target.spotId, detail.value)
            } else {
                upsertPort.saveDetailAbsence(target.spotId, target.toAbsentDetail())
            }

        is Detail.Gallery -> upsertPort.saveGallery(target.spotId, detail.value)
        is Detail.Intro -> upsertPort.saveIntro(target.spotId, target.category, detail.value)
        is Detail.Info -> upsertPort.saveInfoItems(target.spotId, target.category, detail.value)
    }

    /** 본문 부재 행의 재료. 값은 전부 `spot` 행에서 오며 본문 컬럼은 null로 남는다. */
    private fun PrefetchTarget.toAbsentDetail(): SpotDetail = SpotDetail(
        title = title,
        category = category,
        thumbnailImageUrl = thumbnailImageUrl,
        overview = null,
        address = address,
        homepage = null,
        tel = null,
        copyright = copyright,
    )

    /** 원본에서 받아 온 것. 갈래마다 타입이 달라 나뉘며, 적재까지만 나른다. */
    private sealed interface Detail {
        data class Body(val value: SpotDetail?) : Detail
        data class Gallery(val value: List<SpotImage>) : Detail
        data class Intro(val value: SpotIntro?) : Detail
        data class Info(val value: List<SpotInfoItem>) : Detail
    }
}
