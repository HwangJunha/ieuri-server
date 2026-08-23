package org.around.ieuri.community.application.service

import org.around.ieuri.community.application.port.`in`.command.MemberDataEraseUseCase
import org.around.ieuri.community.application.port.out.persistence.MemberDataErasePort
import org.around.ieuri.community.application.port.out.persistence.SpotQuestionCounterPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * 탈퇴 회원의 커뮤니티 데이터 삭제(SPEC-0011 R17).
 *
 * **순서가 중요하다.** 글을 먼저 지우면 그 글에 달린 남의 댓글·좋아요가 cascade로 함께
 * 사라지고, 그 다음 남는 것이 이 회원이 *남의 글에* 남긴 흔적이다. 반대로 하면 자기 글의
 * 댓글을 두 번 지우려 든다(무해하지만 의미 없는 쿼리다).
 *
 * 이 도메인의 저장소만 만진다 — 다른 도메인의 삭제는 각자의 핸들러가 한다.
 */
@Service
class MemberDataEraseService(
    private val erasePort: MemberDataErasePort,
    private val questionCounterPort: SpotQuestionCounterPort,
) : MemberDataEraseUseCase {

    /**
     * **REQUIRES_NEW여야 한다.** 이 유스케이스는 `AFTER_COMMIT` 리스너가 부르는데, 그 시점의
     * 원본 트랜잭션은 이미 커밋됐으면서도 스레드에 **묶여 있다.** 기본 전파(REQUIRED)로 두면
     * 커밋할 수 없는 그 트랜잭션에 조용히 합류해 삭제가 흘러가지 않는다 —
     * 예외도 나지 않아 성공한 것처럼 보인다(실제로 탈퇴 후 글·댓글이 남았다).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun eraseMemberData(memberId: Long) {
        val posts = erasePort.deletePostsByAuthor(memberId)
        val comments = erasePort.deleteCommentsByAuthor(memberId)
        val likes = erasePort.deleteLikesByMember(memberId)
        val hides = erasePort.deleteHidesByMember(memberId)
        val blocks = erasePort.deleteBlocksInvolving(memberId)
        // 대화는 남기고 메시지만 지운다(SPEC-0017 R17) — 상대의 남은 메시지는 정상
        // 표시되어야 하고, 조회 자체가 실패하면 안 된다(AC17-2).
        val messages = erasePort.deleteMessagesBySender(memberId)

        // 장소 질문/답변도 같은 순서 규율을 따른다(SPEC-0026 R17) — 질문을 먼저 지우면
        // 그 질문에 달린 남의 답변·답글이 cascade로 함께 사라지고(AC-17.2), 그 다음 남는
        // 것이 이 회원이 *남의 질문에* 남긴 흔적이다.
        val questions = erasePort.deleteSpotQuestionsByAuthor(memberId)
        val answerErase = erasePort.deleteSpotAnswersByAuthor(memberId)
        val replies = erasePort.deleteSpotAnswerRepliesByAuthor(memberId)

        // **삭제 뒤에 내린다** — 먼저 내리고 삭제가 실패하면 수만 줄어든 상태가 남는다.
        // 지우기만 하면 답변 3개라고 표시된 질문에 2개만 보여, 유저가 세어 보고 버그라고
        // 판단한다. 같은 질문에 여러 건이면 그 수만큼 내린다(id가 중복으로 담겨 온다).
        answerErase.affectedQuestionIds.forEach(questionCounterPort::decreaseAnswerCount)
        val answers = answerErase.deletedCount

        // 개인정보를 담지 않는다(POL-013) — 식별자와 건수만 남긴다.
        // DM 본문·수신자는 애초에 기록 대상이 아니다.
        log.info(
            "community data erased for withdrawn member {}: posts={} comments={} likes={} hides={} " +
                "blocks={} messages={} spotQuestions={} spotAnswers={} spotAnswerReplies={}",
            memberId, posts, comments, likes, hides, blocks, messages, questions, answers, replies,
        )
    }

    private companion object {
        private val log = LoggerFactory.getLogger(MemberDataEraseService::class.java)
    }
}
