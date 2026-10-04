package org.around.ieuri.community.adapter.event

import org.around.ieuri.community.application.event.PostViewed
import org.around.ieuri.community.application.post.provided.PostCommandUseCase
import org.around.ieuri.community.infrastructure.adapter.out.event.CommunityAsyncConfig
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

/**
 * 조회수 증가 리스너. 예외를 밖으로 내보내지 않는다.
 * - `@Async`를 빼면 글 상세 응답이 UPDATE를 기다린다(Spring 이벤트는 기본 동기).
 */
@Component
class PostViewCountListener(
    private val postCommandUseCase: PostCommandUseCase,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async(CommunityAsyncConfig.VIEW_COUNT_EXECUTOR)
    @EventListener
    fun on(event: PostViewed) {
        try {
            postCommandUseCase.recordView(event.postId)
        } catch (e: Exception) {
            // 조회수는 근사치라 재시도하지 않는다.
            log.warn("post view count increase failed: postId={}", event.postId, e)
        }
    }
}
