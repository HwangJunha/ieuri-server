package org.around.ieuri.community.application.service

import org.around.ieuri.community.application.command.LikePostCommand
import org.around.ieuri.community.application.exception.PostNotFoundException
import org.around.ieuri.community.application.port.`in`.command.PostLikeCommandUseCase
import org.around.ieuri.community.application.port.out.persistence.PostCounterPort
import org.around.ieuri.community.application.port.out.persistence.PostLikeRepositoryPort
import org.around.ieuri.community.application.port.out.persistence.PostModerationPort
import org.around.ieuri.community.application.port.out.query.CommunityQueryPort
import org.around.ieuri.community.domain.aggregate.PostLike
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 좋아요·취소. 양방향 모두 멱등이며 갱신된 좋아요 수를 돌려준다.
 * - 저장 결과(실제 삽입·삭제 여부)를 본 뒤에만 계수를 바꾼다. 계수는 DB가 원자적으로 올린다.
 */
@Service
class PostLikeCommandService(
    private val postLikeRepositoryPort: PostLikeRepositoryPort,
    private val postCounterPort: PostCounterPort,
    private val postModerationPort: PostModerationPort,
    private val communityQueryPort: CommunityQueryPort,
) : PostLikeCommandUseCase {

    @Transactional
    override fun like(command: LikePostCommand, memberId: Long): Int {
        requirePostExists(command.postId)

        val inserted = postLikeRepositoryPort.save(
            PostLike.create(postId = command.postId, memberId = memberId),
        )
        // 이미 좋아요한 글이면 숫자를 건드리지 않고 현재 값만 돌려준다(멱등).
        if (inserted) postCounterPort.increaseLikeCount(command.postId)

        return currentLikeCount(command.postId)
    }

    @Transactional
    override fun unlike(command: LikePostCommand, memberId: Long): Int {
        requirePostExists(command.postId)

        val deleted = postLikeRepositoryPort.delete(command.postId, memberId)
        if (deleted) postCounterPort.decreaseLikeCount(command.postId)

        return currentLikeCount(command.postId)
    }

    private fun requirePostExists(postId: Long) {
        if (!postModerationPort.exists(postId)) throw PostNotFoundException()
    }

    /** 그 사이에 글이 사라졌다면 0이다. */
    private fun currentLikeCount(postId: Long): Int = communityQueryPort.findLikeCount(postId) ?: 0
}
