package org.around.ieuri.community.application.service

import org.around.ieuri.community.application.command.LikeFeedItemCommand
import org.around.ieuri.community.application.exception.FeedItemNotFoundException
import org.around.ieuri.community.application.port.`in`.command.FeedItemLikeCommandUseCase
import org.around.ieuri.community.application.port.out.persistence.FeedItemCounterPort
import org.around.ieuri.community.application.port.out.persistence.FeedItemLikeRepositoryPort
import org.around.ieuri.community.application.port.out.persistence.FeedItemModerationPort
import org.around.ieuri.community.application.port.out.query.FeedQueryPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 피드 좋아요·취소(R6). 양방향 모두 멱등이다 — 이미 좋아요한 피드에 다시 눌러도, 좋아요하지
 * 않은 피드를 취소해도 오류가 아니며 좋아요 수가 음수가 되지 않는다(AC6-2·6-3).
 *
 * 구조는 [PostLikeCommandService]와 같다 — 멱등 판정을 저장소의 유일 제약에 맡기고 그 결과로
 * 집계를 올릴지 정하며, 계수는 DB가 원자적으로 올린다. 같은 문제라 같은 모양을 쓴다.
 */
@Service
class FeedItemLikeCommandService(
    private val feedItemLikeRepositoryPort: FeedItemLikeRepositoryPort,
    private val feedItemCounterPort: FeedItemCounterPort,
    private val feedItemModerationPort: FeedItemModerationPort,
    private val feedQueryPort: FeedQueryPort,
) : FeedItemLikeCommandUseCase {

    @Transactional
    override fun like(command: LikeFeedItemCommand, memberId: Long): Int {
        requireFeedItemExists(command.feedItemId)

        val inserted = feedItemLikeRepositoryPort.save(command.feedItemId, memberId)
        // 이미 좋아요한 피드면 숫자를 건드리지 않고 현재 값만 돌려준다(멱등).
        if (inserted) feedItemCounterPort.increaseLikeCount(command.feedItemId)

        return currentLikeCount(command.feedItemId)
    }

    @Transactional
    override fun unlike(command: LikeFeedItemCommand, memberId: Long): Int {
        requireFeedItemExists(command.feedItemId)

        val deleted = feedItemLikeRepositoryPort.delete(command.feedItemId, memberId)
        if (deleted) feedItemCounterPort.decreaseLikeCount(command.feedItemId)

        return currentLikeCount(command.feedItemId)
    }

    private fun requireFeedItemExists(feedItemId: Long) {
        if (!feedItemModerationPort.exists(feedItemId)) throw FeedItemNotFoundException()
    }

    /** 방금 존재를 확인했으므로 없을 수 없다 — 그 사이에 사라졌다면 0으로 답한다. */
    private fun currentLikeCount(feedItemId: Long): Int =
        feedQueryPort.findLikeCount(feedItemId) ?: 0
}
