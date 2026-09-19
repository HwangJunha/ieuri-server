package org.around.ieuri.tourism.application.service

import org.around.ieuri.tourism.application.exception.UpstreamRateLimitedException
import org.around.ieuri.tourism.application.exception.UpstreamTooManyRequestsException
import org.around.ieuri.tourism.application.exception.UpstreamUnavailableException
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
import org.slf4j.LoggerFactory
import org.springframework.core.task.AsyncTaskExecutor
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger

/**
 * 상세 사전 수집(SPEC-0037). 대상 → upstream → 적재를 한 묶음씩 처리한다.
 *
 * **조회 경로의 write-behind를 재사용하지 않는다.** 그쪽 리스너는 예외를 삼키고 전용 executor는
 * 큐가 차면 작업을 버린다 — "캐시 충전은 버려도 된다"는 전제 위에 서 있어서다. 여기서는 결과가
 * 곧 데이터이므로 동기로 적재하고 실패를 센다.
 *
 * 한 묶음 안에서는 [AsyncTaskExecutor]로 동시에 던지고 전부 모아서 적재한다 — 순차로는 FR-002
 * (1일 이내)를 지킬 수 없다.
 *
 * **빈 등록은 `SpotDetailPrefetchConfig`가 한다**(batch 프로파일 전용). 이 클래스에 `@Service`를
 * 붙이면 API 서버도 생성하려 들지만 executor와 정책 빈이 그쪽에 없다.
 */
class SpotDetailPrefetchService(
    private val targetQueryPort: SpotDetailPrefetchTargetQueryPort,
    private val tourInfoPort: TourInfoPort,
    private val upsertPort: SpotDetailUpsertPort,
    private val executor: AsyncTaskExecutor,
    private val policy: SpotDetailPrefetchPolicy,
) : SpotDetailPrefetchUseCase {

    private val log = LoggerFactory.getLogger(SpotDetailPrefetchService::class.java)

    override fun prefetchNext(part: SpotDetailPart, afterSpotId: Long, limit: Int): PrefetchResult {
        val staleBefore = Instant.now().minus(policy.refreshAfter)
        val targets = targetQueryPort.findTargets(part, afterSpotId, limit, staleBefore)
        if (targets.isEmpty()) return PrefetchResult(0, 0, 0, 0, 0, 0, afterSpotId)

        // **묶음마다 전부 모으고 다음 묶음을 던진다.** 전체를 한 번에 던지면 재시도 중 sleep이
        // 워커를 점유하는 동안 다음 작업이 밀고 들어와 실효 부하가 예측하기 어려워진다 — 초당
        // 한도가 고지되지 않은 상대에게는 순간 동시성을 상한 이하로 묶어 두는 편이 안전하다.
        val calls = AtomicInteger()
        val outcomes = targets.chunked(policy.concurrency).flatMap { chunk ->
            chunk.map { target -> executor.submit<Outcome> { fetch(part, target, calls) } }
                .map(Future<Outcome>::get)
        }

        persist(part, targets, outcomes)
        return tally(
            targets,
            outcomes,
            calls = calls.get(),
            lastSpotId = targets.last().spotId,
            dailyLimitReached = outcomes.any { it is Outcome.DailyLimit },
        )
    }

    /**
     * 한 장소의 한 갈래를 확보한다. **부재(null·빈 목록)와 실패를 섞지 않는다** — 부재는 원본이
     * 정상적으로 "없다"고 답한 것이라 다시 물어도 같고, 실패는 답을 듣지 못한 것이다(FR-012).
     */
    private fun fetch(part: SpotDetailPart, target: PrefetchTarget, calls: AtomicInteger): Outcome {
        val query = SpotDetailQuery(target.contentId, target.language)
        return try {
            withRetry(calls) {
                when (part) {
                    SpotDetailPart.BODY -> Outcome.Body(tourInfoPort.findDetail(query))
                    SpotDetailPart.GALLERY -> Outcome.Gallery(tourInfoPort.findGallery(query))
                    SpotDetailPart.INTRO -> Outcome.Intro(tourInfoPort.findIntro(query, target.category))
                    SpotDetailPart.INFO -> Outcome.Info(tourInfoPort.findInfoItems(query, target.category))
                }
            }
        } catch (e: UpstreamRateLimitedException) {
            log.warn("일일 호출 한도 도달: part={} spotId={}", part, target.spotId)
            Outcome.DailyLimit
        } catch (e: RuntimeException) {
            log.warn("상세 확보 실패: part={} spotId={} cause={}", part, target.spotId, e.javaClass.simpleName)
            Outcome.Failed
        }
    }

    /**
     * 네트워크 오류와 초당 한도만 재시도한다(FR-008·AC-012). 간격은 [SpotDetailPrefetchPolicy]의
     * 기준값에서 시도마다 두 배가 된다.
     *
     * **일일 한도는 재시도하지 않는다** — 그날이 끝난 것이라 기다려도 풀리지 않는다. 그대로 올려
     * 호출자가 갈래를 중단하게 한다.
     */
    private fun withRetry(calls: AtomicInteger, block: () -> Outcome): Outcome {
        var delay = policy.retryBaseDelay
        // 첫 시도 1회 + 재시도 retryCount회. 마지막 시도의 예외는 그대로 나가 호출자가 실패로 센다.
        repeat(policy.retryCount) {
            try {
                calls.incrementAndGet()
                return block()
            } catch (e: UpstreamRateLimitedException) {
                throw e // 그날이 끝났다 — 기다려도 풀리지 않으므로 재시도하지 않는다.
            } catch (e: UpstreamTooManyRequestsException) {
                sleep(delay)
                delay = delay.multipliedBy(2)
            } catch (e: UpstreamUnavailableException) {
                sleep(delay)
                delay = delay.multipliedBy(2)
            }
        }
        calls.incrementAndGet()
        return block()
    }

    private fun sleep(duration: Duration) {
        try {
            Thread.sleep(duration.toMillis())
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw e
        }
    }

    /** 성공한 것만 적재한다. 실패·한도는 아무것도 쓰지 않아 기존 행이 그대로 남는다(BR-001·AC-020). */
    private fun persist(part: SpotDetailPart, targets: List<PrefetchTarget>, outcomes: List<Outcome>) {
        targets.zip(outcomes).forEach { (target, outcome) ->
            when (outcome) {
                // 본문 부재도 행으로 남긴다 — 그래야 미확보와 구분되고(AC-005), 조회가 다시
                // 묻지 않는다(FR-011). NOT NULL 컬럼은 `spot` 값으로 채운다(SPEC-0008 R-2).
                is Outcome.Body ->
                    if (outcome.detail != null) {
                        upsertPort.saveDetail(target.spotId, outcome.detail)
                    } else {
                        upsertPort.saveDetailAbsence(target.spotId, target.toAbsentDetail())
                    }

                is Outcome.Gallery -> upsertPort.saveGallery(target.spotId, outcome.images)
                is Outcome.Intro -> upsertPort.saveIntro(target.spotId, target.category, outcome.intro)
                is Outcome.Info -> upsertPort.saveInfoItems(target.spotId, target.category, outcome.items)
                Outcome.Failed, Outcome.DailyLimit -> Unit
            }
        }
    }

    private fun tally(
        targets: List<PrefetchTarget>,
        outcomes: List<Outcome>,
        calls: Int,
        lastSpotId: Long,
        dailyLimitReached: Boolean,
    ): PrefetchResult = PrefetchResult(
        processed = targets.size,
        fetched = outcomes.count { it.isContent() },
        empty = outcomes.count { it.isEmpty() },
        failed = outcomes.count { it is Outcome.Failed },
        skipped = outcomes.count { it is Outcome.DailyLimit },
        calls = calls,
        lastSpotId = lastSpotId,
        dailyLimitReached = dailyLimitReached,
    )

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

    /** 한 장소·한 갈래의 결과. 갈래마다 담는 값이 달라 sealed로 둔다. */
    private sealed interface Outcome {
        data class Body(val detail: SpotDetail?) : Outcome
        data class Gallery(val images: List<SpotImage>) : Outcome
        data class Intro(val intro: SpotIntro?) : Outcome
        data class Info(val items: List<SpotInfoItem>) : Outcome
        data object Failed : Outcome
        data object DailyLimit : Outcome

        fun isContent(): Boolean = when (this) {
            is Body -> detail != null
            is Gallery -> images.isNotEmpty()
            is Intro -> intro != null
            is Info -> items.isNotEmpty()
            Failed, DailyLimit -> false
        }

        fun isEmpty(): Boolean = when (this) {
            is Body -> detail == null
            is Gallery -> images.isEmpty()
            is Intro -> intro == null
            is Info -> items.isEmpty()
            Failed, DailyLimit -> false
        }
    }
}
