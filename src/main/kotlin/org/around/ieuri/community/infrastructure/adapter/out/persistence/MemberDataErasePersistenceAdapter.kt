package org.around.ieuri.community.infrastructure.adapter.out.persistence

import org.around.ieuri.community.application.port.out.persistence.MemberDataErasePort
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.SpotQaPhotoOwnerType
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.BlockJpaRepository
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.CommentJpaRepository
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.MessageJpaRepository
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.PostHideJpaRepository
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.PostJpaRepository
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.PostLikeJpaRepository
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.SpotAnswerJpaRepository
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.SpotAnswerReplyJpaRepository
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.SpotQaPhotoJpaRepository
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.SpotQuestionJpaRepository
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
    private val messageRepository: MessageJpaRepository,
    private val questionRepository: SpotQuestionJpaRepository,
    private val answerRepository: SpotAnswerJpaRepository,
    private val replyRepository: SpotAnswerReplyJpaRepository,
    private val qaPhotoRepository: SpotQaPhotoJpaRepository,
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
     * **대화는 지우지 않는다** — 상대방 화면에서 남은 자기 메시지가 보여야 하고 조회 자체가
     * 실패하면 안 된다(SPEC-0017 AC17-2).
     */
    @Transactional
    override fun deleteMessagesBySender(memberId: Long): Long =
        messageRepository.deleteBySenderId(memberId)

    /**
     * 회원의 질문 전량(SPEC-0026 R17). 답변·답글 **행**은 FK CASCADE가 따라 지우지만,
     * **사진 행에는 FK가 없어 따라오지 않는다** — 그래서 지우기 전에 자식 id를 모아
     * 사진을 먼저 지운다. 순서를 뒤집으면 그 id를 알 방법이 없어 사진이 영구히 남는다.
     */
    @Transactional
    override fun deleteSpotQuestionsByAuthor(memberId: Long): Long {
        val questionIds = questionRepository.findByAuthorId(memberId).mapNotNull { it.id }
        if (questionIds.isEmpty()) return 0

        val answerIds = answerRepository.findByQuestionIdIn(questionIds).mapNotNull { it.id }
        val replyIds = if (answerIds.isEmpty()) {
            emptyList()
        } else {
            replyRepository.findByAnswerIdIn(answerIds).mapNotNull { it.id }
        }
        deletePhotosOf(SpotQaPhotoOwnerType.REPLY, replyIds)
        deletePhotosOf(SpotQaPhotoOwnerType.ANSWER, answerIds)
        deletePhotosOf(SpotQaPhotoOwnerType.QUESTION, questionIds)

        return questionRepository.deleteByAuthorId(memberId)
    }

    /** 회원이 남의 질문에 단 답변. 자기 질문의 답변은 질문과 함께 이미 사라진 뒤다. */
    @Transactional
    override fun deleteSpotAnswersByAuthor(memberId: Long): Long {
        val answerIds = answerRepository.findByAuthorId(memberId).mapNotNull { it.id }
        if (answerIds.isEmpty()) return 0

        val replyIds = replyRepository.findByAnswerIdIn(answerIds).mapNotNull { it.id }
        deletePhotosOf(SpotQaPhotoOwnerType.REPLY, replyIds)
        deletePhotosOf(SpotQaPhotoOwnerType.ANSWER, answerIds)

        return answerRepository.deleteByAuthorId(memberId)
    }

    @Transactional
    override fun deleteSpotAnswerRepliesByAuthor(memberId: Long): Long {
        val replyIds = replyRepository.findByAuthorId(memberId).mapNotNull { it.id }
        if (replyIds.isEmpty()) return 0

        deletePhotosOf(SpotQaPhotoOwnerType.REPLY, replyIds)
        return replyRepository.deleteByAuthorId(memberId)
    }

    private fun deletePhotosOf(ownerType: SpotQaPhotoOwnerType, ownerIds: List<Long>) {
        if (ownerIds.isEmpty()) return
        qaPhotoRepository.deleteByOwnerTypeAndOwnerIdIn(ownerType, ownerIds)
    }
}
