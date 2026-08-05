package org.around.ieuri.community.application.service

import org.around.ieuri.community.application.command.LikePostCommand
import org.around.ieuri.community.application.exception.PostNotFoundException
import org.around.ieuri.community.application.port.`in`.command.PostLikeCommandUseCase
import org.around.ieuri.community.application.port.out.persistence.PostLikeRepositoryPort
import org.around.ieuri.community.application.port.out.persistence.PostRepositoryPort
import org.around.ieuri.community.domain.aggregate.PostLike
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 좋아요·취소. 양방향 모두 멱등이다 — 이미 좋아요한 글에 다시 눌러도, 좋아요하지 않은 글을
 * 취소해도 오류가 아니다.
 *
 * 멱등 판정을 저장소에 맡기고 그 결과로 집계를 올릴지 결정한다. 집계를 먼저 올리고 저장에서
 * 유일 제약에 걸리는 순서로 쓰면 카운트가 실제 좋아요 수보다 커진다.
 */
@Service
class PostLikeCommandService(
    private val postLikeRepositoryPort: PostLikeRepositoryPort,
    private val postRepositoryPort: PostRepositoryPort,
) : PostLikeCommandUseCase {

    @Transactional
    override fun like(command: LikePostCommand, memberId: Long): Int {
        val post = postRepositoryPort.findById(command.postId) ?: throw PostNotFoundException()
        val inserted = postLikeRepositoryPort.save(
            PostLike.create(postId = command.postId, memberId = memberId),
        )
        if (!inserted) return post.likeCount

        post.increaseLikeCount()
        return postRepositoryPort.save(post).likeCount
    }

    @Transactional
    override fun unlike(command: LikePostCommand, memberId: Long): Int {
        val post = postRepositoryPort.findById(command.postId) ?: throw PostNotFoundException()
        val deleted = postLikeRepositoryPort.delete(command.postId, memberId)
        if (!deleted) return post.likeCount

        post.decreaseLikeCount()
        return postRepositoryPort.save(post).likeCount
    }
}
