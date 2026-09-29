package org.around.ieuri.community.application.service

import org.around.ieuri.community.application.port.`in`.command.MemberDataEraseUseCase
import org.around.ieuri.community.application.port.out.client.ClockPort
import org.around.ieuri.community.application.port.out.persistence.MemberContentSoftDeletePort
import org.around.ieuri.community.application.port.out.persistence.MemberDataErasePort
import org.around.ieuri.community.application.port.out.persistence.SpotQuestionCounterPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * 탈퇴 회원의 커뮤니티 데이터 정리(SPEC-0011).
 * - 남에게 보이는 글·댓글·질문·답변·답글·DM은 소프트 삭제한다(읽기 계층이 `deletedAt IS NULL`로 거른다).
 * - 본인만의 좋아요·숨김·차단은 물리 삭제한다.
 * - DM은 보관 기간이 지나면 정리 배치가 행째로 지운다.
 */
@Service
class MemberDataEraseService(
    private val erasePort: MemberDataErasePort,
    private val softDeletePort: MemberContentSoftDeletePort,
    private val questionCounterPort: SpotQuestionCounterPort,
    private val clockPort: ClockPort,
) : MemberDataEraseUseCase {

    /**
     * `AFTER_COMMIT` 리스너가 부르므로 REQUIRES_NEW여야 한다.
     * - REQUIRED면 이미 커밋된 원본 트랜잭션에 합류해 예외 없이 아무것도 반영되지 않는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun eraseMemberData(memberId: Long) {
        // 한 번의 탈퇴는 모든 표에 같은 시각을 남긴다.
        val now = clockPort.now()

        // 소프트 삭제 전에 세어 둔다 — 삭제 후에는 내릴 질문을 알 수 없다.
        val questionIdsToDecrease = softDeletePort.findQuestionIdsToDecrease(memberId)

        // ── 콘텐츠: 소프트 삭제 ──
        // 질문이 삭제되면 거기 달린 남의 답변도 조회에서 함께 사라진다.
        val posts = softDeletePort.softDeletePostsByAuthor(memberId, now)
        val comments = softDeletePort.softDeleteCommentsByAuthor(memberId, now)
        val questions = softDeletePort.softDeleteSpotQuestionsByAuthor(memberId, now)
        val answers = softDeletePort.softDeleteSpotAnswersByAuthor(memberId, now)
        val replies = softDeletePort.softDeleteSpotAnswerRepliesByAuthor(memberId, now)
        // 대화는 남기고 메시지만 가린다.
        val messages = softDeletePort.softDeleteMessagesBySender(memberId, now)

        // 삭제한 뒤에 내린다. 같은 질문의 id는 건수만큼 중복으로 담겨 온다.
        questionIdsToDecrease.forEach(questionCounterPort::decreaseAnswerCount)

        // ── 본인만의 데이터: 물리 삭제 ──
        val likes = erasePort.deleteLikesByMember(memberId)
        val hides = erasePort.deleteHidesByMember(memberId)
        val blocks = erasePort.deleteBlocksInvolving(memberId)

        // 식별자와 건수만 남긴다(POL-013).
        log.info(
            "community data cleared for withdrawn member {}: softDeleted(posts={} comments={} " +
                "spotQuestions={} spotAnswers={} spotAnswerReplies={} messages={}) " +
                "deleted(likes={} hides={} blocks={})",
            memberId, posts, comments, questions, answers, replies, messages, likes, hides, blocks,
        )
    }

    private companion object {
        private val log = LoggerFactory.getLogger(MemberDataEraseService::class.java)
    }
}
