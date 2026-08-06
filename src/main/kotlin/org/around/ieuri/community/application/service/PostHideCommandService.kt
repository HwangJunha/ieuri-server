package org.around.ieuri.community.application.service

import org.around.ieuri.community.application.command.HidePostCommand
import org.around.ieuri.community.application.exception.PostNotFoundException
import org.around.ieuri.community.application.port.`in`.command.PostHideCommandUseCase
import org.around.ieuri.community.application.port.out.persistence.PostHideRepositoryPort
import org.around.ieuri.community.application.port.out.persistence.PostModerationPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 글 숨김·해제. **자기 화면에서만 감춘다** — 글 자체는 그대로이고 다른 사람에게는 계속 보인다.
 * 신고 누적으로 모두에게 감춰지는 `Post.hidden`과는 다른 개념이다.
 *
 * **남의 글이든 자기 글이든 숨길 수 있다.** 소유권을 보지 않는 이유는 이것이 조치가 아니라
 * 개인 화면 설정이기 때문이다.
 *
 * 양방향 모두 멱등이다 — 판정은 저장소의 유일 제약이 맡고 여기서는 결과를 쓰지 않는다.
 */
@Service
class PostHideCommandService(
    private val postHideRepositoryPort: PostHideRepositoryPort,
    private val postModerationPort: PostModerationPort,
) : PostHideCommandUseCase {

    @Transactional
    override fun hide(command: HidePostCommand, memberId: Long) {
        // 존재 확인만 필요하다 — 애그리게이트를 복원하면 태그·사진까지 읽는다.
        if (!postModerationPort.exists(command.postId)) throw PostNotFoundException()
        postHideRepositoryPort.save(memberId, command.postId)
    }

    /**
     * 해제는 **글의 존재를 확인하지 않는다.** 이미 삭제된 글의 숨김 기록도 지울 수 있어야
     * 목록에서 사라지지 않고 남는 일이 없다.
     */
    @Transactional
    override fun unhide(command: HidePostCommand, memberId: Long) {
        postHideRepositoryPort.delete(memberId, command.postId)
    }
}
