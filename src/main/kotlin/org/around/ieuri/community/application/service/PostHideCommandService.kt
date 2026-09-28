package org.around.ieuri.community.application.service

import org.around.ieuri.community.application.command.HidePostCommand
import org.around.ieuri.community.application.exception.PostNotFoundException
import org.around.ieuri.community.application.port.`in`.command.PostHideCommandUseCase
import org.around.ieuri.community.application.port.out.persistence.PostHideRepositoryPort
import org.around.ieuri.community.application.port.out.persistence.PostModerationPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 글 숨김·해제. 자기 화면에서만 감춘다 — 신고 누적으로 모두에게 감춰지는 `Post.hidden`과 다르다.
 * - 소유권을 보지 않는다. 양방향 모두 멱등이다.
 */
@Service
class PostHideCommandService(
    private val postHideRepositoryPort: PostHideRepositoryPort,
    private val postModerationPort: PostModerationPort,
) : PostHideCommandUseCase {

    @Transactional
    override fun hide(command: HidePostCommand, memberId: Long) {
        if (!postModerationPort.exists(command.postId)) throw PostNotFoundException()
        postHideRepositoryPort.save(memberId, command.postId)
    }

    /** 해제는 글의 존재를 확인하지 않는다 — 삭제된 글의 숨김 기록도 지울 수 있다. */
    @Transactional
    override fun unhide(command: HidePostCommand, memberId: Long) {
        postHideRepositoryPort.delete(memberId, command.postId)
    }
}
