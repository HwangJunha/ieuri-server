package org.around.ieuri.tourism.infrastructure.adapter.out.event

import org.around.ieuri.tourism.application.event.SpotDetailAbsent
import org.around.ieuri.tourism.application.event.SpotDetailFetched
import org.around.ieuri.tourism.application.event.SpotGalleryFetched
import org.around.ieuri.tourism.application.event.SpotInfoItemsFetched
import org.around.ieuri.tourism.application.event.SpotIntroFetched
import org.around.ieuri.tourism.application.port.out.persistence.SpotDetailUpsertPort
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

/**
 * 상세 write-behind 적재 리스너(SPEC-0004 R-14). upstream 조회 결과를 받아 DB에 쌓는다.
 *
 * **`@Async`가 반드시 있어야 한다.** Spring의 이벤트는 기본이 **동기**라 리스너가 발행 스레드
 * (= 요청 스레드)에서 실행된다. 그러면 조회 응답이 적재를 기다리게 되어 AC-39(응답 비지연)를
 * 위반하고, write-behind라는 이름 자체가 무의미해진다. `@Async`가 실행을 전용 executor로
 * 옮긴다([TourismAsyncConfig] 참조).
 *
 * `@TransactionalEventListener`가 아니라 `@EventListener`인 이유는 발행 지점이 조회 경로라
 * 트랜잭션이 없기 때문이다. auth의 메일 발송은 쓰기 트랜잭션 뒤라 AFTER_COMMIT이 맞지만,
 * 여기서는 커밋을 기다릴 대상이 없다.
 *
 * **어떤 예외도 밖으로 내보내지 않는다.** 적재 실패는 캐시 충전 실패일 뿐이며 사용자에게 알릴
 * 일이 아니다(AC-40). 실패한 항목은 다음 조회 때 다시 이벤트로 들어온다.
 *
 * 트랜잭션은 포트 구현이 메서드마다 연다 — `@Async`로 스레드가 갈려도 여기서 열어 줄 것이 없다.
 */
@Component
class SpotDetailWriteBehindListener(
    private val store: SpotDetailUpsertPort,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async(TourismAsyncConfig.WRITE_BEHIND_EXECUTOR)
    @EventListener
    fun on(event: SpotDetailFetched) = runSafely("detail:${event.spotId}") {
        store.saveDetail(event.spotId, event.detail)
    }

    /**
     * 본문 부재의 적재(SPEC-0008 R-3). 부재를 남기지 않으면 본문 없는 spot을 매 조회마다 upstream에
     * 다시 묻게 된다 — 다시 물어도 없는 데이터인데도.
     *
     * **실패는 여기 오지 않는다**(AC-4·AC-16). 서비스가 예외를 그대로 올리므로 이 이벤트는
     * "없음을 확인했다"는 사실일 때만 발행된다.
     */
    @Async(TourismAsyncConfig.WRITE_BEHIND_EXECUTOR)
    @EventListener
    fun on(event: SpotDetailAbsent) = runSafely("detail-absent:${event.spotId}") {
        store.saveDetailAbsence(event.spotId, event.fallback)
    }

    @Async(TourismAsyncConfig.WRITE_BEHIND_EXECUTOR)
    @EventListener
    fun on(event: SpotGalleryFetched) = runSafely("gallery:${event.spotId}") {
        store.saveGallery(event.spotId, event.gallery)
    }

    @Async(TourismAsyncConfig.WRITE_BEHIND_EXECUTOR)
    @EventListener
    fun on(event: SpotIntroFetched) = runSafely("intro:${event.spotId}") {
        store.saveIntro(event.spotId, event.category, event.intro)
    }

    @Async(TourismAsyncConfig.WRITE_BEHIND_EXECUTOR)
    @EventListener
    fun on(event: SpotInfoItemsFetched) = runSafely("info:${event.spotId}") {
        store.saveInfoItems(event.spotId, event.category, event.items)
    }

    private fun runSafely(label: String, task: () -> Unit) {
        try {
            task()
        } catch (e: Exception) {
            // 제약 위반·DB 장애 등. 다음 조회가 다시 발행하므로 재시도하지 않는다.
            log.warn("spot detail write-behind failed: {}", label, e)
        }
    }
}
