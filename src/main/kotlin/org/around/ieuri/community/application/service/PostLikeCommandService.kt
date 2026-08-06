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
 * 좋아요·취소. 양방향 모두 멱등이다 — 이미 좋아요한 글에 다시 눌러도, 좋아요하지 않은 글을
 * 취소해도 오류가 아니다.
 *
 * 멱등 판정을 저장소에 맡기고 그 결과로 집계를 올릴지 결정한다. 집계를 먼저 올리고 저장에서
 * 유일 제약에 걸리는 순서로 쓰면 카운트가 실제 좋아요 수보다 커진다.
 *
 * **애그리게이트를 복원하지 않는다.** 여기서 필요한 것은 좋아요 수 하나인데, 복원하면
 * 태그·사진·카테고리·장소까지 읽는다. 게다가 읽고 고쳐 쓰면 동시에 누른 두 요청이 서로의
 * 증가를 덮어써 카운트가 실제 좋아요 행 수보다 작아진다 — 계수는 DB가 원자적으로 올린다.
 *
 * 갱신값은 **읽기 포트로 다시 조회한다**(CQRS). 쓰기가 애그리게이트를 거치지 않으므로
 * 갱신값이 커맨드의 반환으로 따라오지 않기 때문이다.
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

    /** 방금 존재를 확인했으므로 없을 수 없다 — 그 사이에 사라졌다면 0으로 답한다. */
    private fun currentLikeCount(postId: Long): Int =
        communityQueryPort.findLikeCount(postId) ?: 0
}
