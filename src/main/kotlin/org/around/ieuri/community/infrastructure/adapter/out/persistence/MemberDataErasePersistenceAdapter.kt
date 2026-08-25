package org.around.ieuri.community.infrastructure.adapter.out.persistence

import org.around.ieuri.community.application.port.out.persistence.MemberDataErasePort
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.BlockJpaRepository
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.MessageJpaRepository
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.PostHideJpaRepository
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.PostLikeJpaRepository
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.SpotAnswerJpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 탈퇴 회원의 커뮤니티 데이터 물리 삭제 어댑터(SPEC-0011 R17).
 *
 * **여기 남은 것은 본인만의 데이터뿐이다** — 좋아요·숨김·차단·DM. 콘텐츠는 소프트 삭제로
 * 옮겼다([MemberContentSoftDeleteAdapter]).
 *
 * 벌크 삭제 쿼리는 영속성 컨텍스트를 우회한다. 이 트랜잭션에서 같은 엔티티를 다시 읽는
 * 코드가 없으므로 안전하며, DB의 ON DELETE CASCADE는 그대로 동작한다.
 */
@Component
class MemberDataErasePersistenceAdapter(
    private val likeRepository: PostLikeJpaRepository,
    private val hideRepository: PostHideJpaRepository,
    private val blockRepository: BlockJpaRepository,
    private val messageRepository: MessageJpaRepository,
    private val answerRepository: SpotAnswerJpaRepository,
) : MemberDataErasePort {

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
     * **소프트 삭제 전에 불러야 한다** — 칠하고 나면 어느 질문의 수를 내려야 하는지 알 수
     * 없다. 숨겨진 답변은 이미 수에서 빠져 있어 제외한다.
     */
    @Transactional(readOnly = true)
    override fun findQuestionIdsToDecrease(memberId: Long): List<Long> =
        answerRepository.findByAuthorId(memberId)
            .filterNot { it.hidden }
            // 이미 지워진 답변은 수에서 빠진 뒤다 — 두 번 내리면 실제보다 작아진다.
            .filter { it.deletedAt == null }
            .map { it.questionId }
}
