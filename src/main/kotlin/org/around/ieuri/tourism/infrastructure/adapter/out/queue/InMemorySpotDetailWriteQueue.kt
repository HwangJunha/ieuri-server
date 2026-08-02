package org.around.ieuri.tourism.infrastructure.adapter.out.queue

import jakarta.annotation.PreDestroy
import org.around.ieuri.tourism.application.port.out.persistence.SpotDetailStorePort
import org.around.ieuri.tourism.application.port.out.queue.SpotDetailWriteQueuePort
import org.around.ieuri.tourism.domain.aggregate.SpotDetail
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.domain.vo.SpotImage
import org.around.ieuri.tourism.domain.vo.SpotInfoItem
import org.around.ieuri.tourism.domain.vo.SpotIntro
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * 인메모리 write-behind 큐(SPEC-0004 R-14~R-16). 조회 경로가 적재를 기다리지 않게 하는 장치다.
 *
 * **유계 큐 + 폐기 정책**([TBD-5] 결정). 블로킹은 R-15(가용성 유지)와 정면 충돌하고,
 * `CallerRunsPolicy`는 호출 스레드(= 요청 스레드)가 적재를 수행해 AC-39(응답 비지연)를 위반한다.
 * 남는 것은 폐기이며, **write-behind는 캐시 충전이지 데이터 보존이 아니므로** 버려도 정합성이
 * 깨지지 않는다 — 다음 조회 때 다시 큐에 들어온다.
 *
 * `@Async`가 아니라 `ThreadPoolExecutor`를 직접 구성하는 이유는 큐 상한과 거부 정책을 명시적으로
 * 통제하기 위해서다. Spring의 기본 executor는 무제한 큐를 쓰기 쉬워 메모리가 조용히 늘어난다.
 *
 * **종료 시**(R-16): 새 유입을 막고 진행 중인 작업을 **유한 시간** 안에서 마무리한 뒤 내려간다.
 * 각 적재는 어댑터에서 단일 트랜잭션이므로 중간에 끊겨도 절반만 쓰인 상태가 남지 않는다(AC-44).
 * 종료 중 들어온 요청은 조용히 버려지며 **조회 응답 자체는 실패하지 않는다**(AC-45).
 *
 * Kafka 이관은 후속이다(POL-028) — 이 포트 뒤에서 구현체만 바뀐다.
 */
@Component
class InMemorySpotDetailWriteQueue(
    private val store: SpotDetailStorePort,
) : SpotDetailWriteQueuePort {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 폐기 건수. 꾸준히 늘면 큐 상한이나 워커 수가 부족하다는 신호다(AC-43). */
    private val discarded = AtomicLong()

    /** shutdown 멱등성 보장. @PreDestroy는 한 번이지만 테스트·수동 호출이 겹칠 수 있다. */
    private val shuttingDown = java.util.concurrent.atomic.AtomicBoolean(false)

    private val executor = ThreadPoolExecutor(
        WORKER_COUNT, WORKER_COUNT,
        0L, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(QUEUE_CAPACITY),
        { runnable -> Thread(runnable, "spot-detail-writer").apply { isDaemon = true } },
        // **AbortPolicy를 쓰고 submit에서 잡는다.** DiscardPolicy는 조용히 버려서 폐기를 감지할
        // 방법이 taskCount 비교밖에 없는데, 그 값은 근사치이고 여러 요청 스레드가 동시에 읽어
        // 경합한다 — 실제로 실행된 작업을 폐기로 세거나 그 반대가 된다. 거부 핸들러는 거부 1건당
        // 정확히 한 번, 제출 스레드에서 호출되므로 경합이 없다.
        ThreadPoolExecutor.AbortPolicy(),
    )

    override fun enqueueDetail(contentId: String, locale: DomainLocale, detail: SpotDetail) =
        submit("detail:$contentId") { store.saveDetail(contentId, locale, detail) }

    override fun enqueueGallery(contentId: String, locale: DomainLocale, gallery: List<SpotImage>) =
        submit("gallery:$contentId") { store.saveGallery(contentId, locale, gallery) }

    override fun enqueueIntro(
        contentId: String,
        locale: DomainLocale,
        category: SpotCategory,
        intro: SpotIntro?,
    ) = submit("intro:$contentId") { store.saveIntro(contentId, locale, category, intro) }

    override fun enqueueInfoItems(
        contentId: String,
        locale: DomainLocale,
        category: SpotCategory,
        items: List<SpotInfoItem>,
    ) = submit("info:$contentId") { store.saveInfoItems(contentId, locale, category, items) }

    /**
     * **어떤 예외도 호출자에게 전파하지 않는다.** 여기서 새는 예외는 곧 조회 응답의 오류가 되는데,
     * 적재 실패는 사용자에게 알릴 일이 아니다(AC-40).
     */
    private fun submit(label: String, task: () -> Unit) {
        try {
            executor.execute {
                try {
                    task()
                } catch (e: Exception) {
                    // 제약 위반·DB 장애 등. 다음 조회 때 다시 큐에 들어오므로 재시도하지 않는다.
                    log.warn("spot detail write-behind failed: {}", label, e)
                }
            }
        } catch (e: RejectedExecutionException) {
            // 큐 포화 또는 종료 중 유입. **둘 다 정상 경로다** — 적재는 캐시 충전이라 버려도 되고,
            // 여기서 예외가 새면 조회 응답이 실패한다(AC-40·AC-42·AC-45).
            discard(label)
        }
    }

    private fun discard(label: String) {
        val count = discarded.incrementAndGet()
        // 폐기가 잦으면 로그가 폭주하므로 표본만 남긴다. 정확한 총계는 카운터가 갖는다.
        if (count % DISCARD_LOG_INTERVAL == 1L) {
            log.warn("spot detail write-behind discarded (total={}): {}", count, label)
        }
    }

    /** 관측용. 폐기 총계를 외부에서 확인할 수 있게 노출한다(AC-43). */
    fun discardedCount(): Long = discarded.get()

    /**
     * 종료 훅(R-16). 새 유입을 막고 **유한 시간**만 기다린다 — 무한 대기는 배포·재시작을 붙잡는다.
     * 시간 내에 끝나지 않은 작업은 버린다. 캐시 충전이므로 잃어도 다음 조회가 복구한다.
     */
    @PreDestroy
    fun shutdown() {
        if (!shuttingDown.compareAndSet(false, true)) return
        executor.shutdown()
        try {
            if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                val dropped = executor.shutdownNow().size
                log.warn("spot detail write-behind shutdown timed out — {} pending task(s) dropped", dropped)
            }
        } catch (e: InterruptedException) {
            // 대기 중 인터럽트. 남은 작업을 버리고 인터럽트 상태를 복원한다 —
            // 삼키면 상위 종료 로직이 인터럽트를 놓친다.
            executor.shutdownNow()
            Thread.currentThread().interrupt()
        }
        if (discarded.get() > 0) {
            log.info("spot detail write-behind discarded {} task(s) in total", discarded.get())
        }
    }

    private companion object {
        /** 적재는 I/O 대기가 대부분이라 소수로 충분하다. 늘리면 DB 커넥션 풀을 잠식한다. */
        const val WORKER_COUNT = 2
        const val QUEUE_CAPACITY = 500
        const val SHUTDOWN_TIMEOUT_SECONDS = 10L
        const val DISCARD_LOG_INTERVAL = 100L
    }
}
