package org.around.ieuri.tourism.adapter.event.listener

import org.around.ieuri.tourism.adapter.config.TourismAsyncConfig
import org.around.ieuri.tourism.application.spot.SpotDetailAbsent
import org.around.ieuri.tourism.application.spot.SpotDetailFetched
import org.around.ieuri.tourism.application.spot.SpotGalleryFetched
import org.around.ieuri.tourism.application.spot.SpotInfoItemsFetched
import org.around.ieuri.tourism.application.spot.SpotIntroFetched
import org.around.ieuri.tourism.application.spot.provided.SpotCommandUseCase
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

/**
 * 상세 write-behind 적재 리스너(SPEC-0004 R-14). upstream 조회 결과를 받아 DB에 쌓는다.
 * - `@Async`가 반드시 있어야 한다 — 빠지면 요청 스레드에서 돌아 응답이 적재를 기다린다(AC-39).
 * - 발행 지점(조회 경로)에 트랜잭션이 없어 `@EventListener`다.
 * - 어떤 예외도 밖으로 내보내지 않는다(AC-40). 실패한 항목은 다음 조회 때 다시 들어온다.
 */
@Component
class SpotDetailWriteBehindListener(
    private val spotCommandUseCase: SpotCommandUseCase,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async(TourismAsyncConfig.WRITE_BEHIND_EXECUTOR)
    @EventListener
    fun on(event: SpotDetailFetched) = runSafely("detail:${event.spotId}") {
        spotCommandUseCase.saveDetail(event.spotId, event.detail)
    }

    /** 본문 부재의 적재(SPEC-0008 R-3). "없음을 확인했다"일 때만 발행되며 실패는 오지 않는다(AC-4·AC-16). */
    @Async(TourismAsyncConfig.WRITE_BEHIND_EXECUTOR)
    @EventListener
    fun on(event: SpotDetailAbsent) = runSafely("detail-absent:${event.spotId}") {
        spotCommandUseCase.saveDetailAbsence(event.spotId, event.fallback)
    }

    @Async(TourismAsyncConfig.WRITE_BEHIND_EXECUTOR)
    @EventListener
    fun on(event: SpotGalleryFetched) = runSafely("gallery:${event.spotId}") {
        spotCommandUseCase.saveGallery(event.spotId, event.gallery)
    }

    @Async(TourismAsyncConfig.WRITE_BEHIND_EXECUTOR)
    @EventListener
    fun on(event: SpotIntroFetched) = runSafely("intro:${event.spotId}") {
        spotCommandUseCase.saveIntro(event.spotId, event.category, event.intro)
    }

    @Async(TourismAsyncConfig.WRITE_BEHIND_EXECUTOR)
    @EventListener
    fun on(event: SpotInfoItemsFetched) = runSafely("info:${event.spotId}") {
        spotCommandUseCase.saveInfoItems(event.spotId, event.category, event.items)
    }

    private fun runSafely(label: String, task: () -> Unit) {
        try {
            task()
        } catch (e: Exception) {
            // 다음 조회가 다시 발행하므로 재시도하지 않는다.
            log.warn("spot detail write-behind failed: {}", label, e)
        }
    }
}
