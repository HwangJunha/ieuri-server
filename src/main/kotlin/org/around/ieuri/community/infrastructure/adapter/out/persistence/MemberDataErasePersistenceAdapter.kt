package org.around.ieuri.community.infrastructure.adapter.out.persistence

import org.around.ieuri.community.application.port.out.persistence.MemberDataErasePort
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.BlockJpaRepository
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.CommentJpaRepository
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.FeedItemJpaRepository
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.FeedItemLikeJpaRepository
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.MessageJpaRepository
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.PostHideJpaRepository
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.PostJpaRepository
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.PostLikeJpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 탈퇴 회원의 커뮤니티 데이터 삭제 어댑터(SPEC-0011 R17).
 *
 * 파생 삭제 쿼리는 영속성 컨텍스트를 우회한다. 이 트랜잭션에서 같은 엔티티를 다시 읽는
 * 코드가 없으므로 안전하며, **DB의 ON DELETE CASCADE는 그대로 동작한다**(JPA cascade가
 * 아니라 물리 제약이라 벌크 삭제에도 따라온다).
 */
@Component
class MemberDataErasePersistenceAdapter(
    private val postRepository: PostJpaRepository,
    private val commentRepository: CommentJpaRepository,
    private val likeRepository: PostLikeJpaRepository,
    private val hideRepository: PostHideJpaRepository,
    private val blockRepository: BlockJpaRepository,
    private val feedItemRepository: FeedItemJpaRepository,
    private val feedItemLikeRepository: FeedItemLikeJpaRepository,
    private val messageRepository: MessageJpaRepository,
) : MemberDataErasePort {

    @Transactional
    override fun deletePostsByAuthor(memberId: Long): Long =
        postRepository.deleteByAuthorId(memberId)

    @Transactional
    override fun deleteCommentsByAuthor(memberId: Long): Long =
        commentRepository.deleteByAuthorId(memberId)

    @Transactional
    override fun deleteLikesByMember(memberId: Long): Long =
        likeRepository.deleteByMemberId(memberId)

    @Transactional
    override fun deleteHidesByMember(memberId: Long): Long =
        hideRepository.deleteByMemberId(memberId)

    @Transactional
    override fun deleteBlocksInvolving(memberId: Long): Long =
        blockRepository.deleteByBlockerIdOrBlockedId(memberId, memberId)

    /**
     * 자기 피드를 지우면 그 피드의 사진·좋아요는 cascade가 따라 지운다. 남는 것은 이 회원이
     * **남의 피드에** 누른 좋아요라 따로 지운다(글·댓글과 같은 순서·같은 이유).
     */
    @Transactional
    override fun deleteFeedItemsByAuthor(memberId: Long): Long {
        val feedItems = feedItemRepository.deleteByAuthorId(memberId)
        feedItemLikeRepository.deleteByMemberId(memberId)
        return feedItems
    }

    /**
     * **대화는 지우지 않는다** — 상대방 화면에서 남은 자기 메시지가 보여야 하고 조회 자체가
     * 실패하면 안 된다(SPEC-0017 AC17-2).
     */
    @Transactional
    override fun deleteMessagesBySender(memberId: Long): Long =
        messageRepository.deleteBySenderId(memberId)
}
