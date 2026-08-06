package org.around.ieuri.community.infrastructure.adapter.out.event

import org.around.ieuri.community.application.event.PostViewed
import org.around.ieuri.community.application.port.out.persistence.PostCounterPort
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

/**
 * 조회수 증가 리스너. tourism의 write-behind 리스너와 같은 형태다.
 *
 * **`@Async`가 반드시 있어야 한다.** Spring의 이벤트는 기본이 **동기**라 리스너가 발행
 * 스레드(= 요청 스레드)에서 실행된다. 그러면 글 상세 응답이 UPDATE를 기다리게 된다.
 *
 * `@TransactionalEventListener`가 아니라 `@EventListener`인 이유는 발행 지점이 조회 경로라
 * 기다릴 커밋이 없기 때문이다.
 *
 * **어떤 예외도 밖으로 내보내지 않는다.** 조회수 증가 실패로 글이 안 보이면 안 된다 —
 * 근사치 계수 하나 때문에 상세 화면을 깨뜨릴 이유가 없다.
 */
@Component
class PostViewCountListener(
    private val postCounterPort: PostCounterPort,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async(CommunityAsyncConfig.VIEW_COUNT_EXECUTOR)
    @EventListener
    fun on(event: PostViewed) {
        try {
            postCounterPort.increaseViewCount(event.postId)
        } catch (e: Exception) {
            // DB 장애 등. 조회수는 근사치라 재시도하지 않는다.
            log.warn("post view count increase failed: postId={}", event.postId, e)
        }
    }
}
