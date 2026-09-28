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
 * 탈퇴 회원의 커뮤니티 데이터 정리(SPEC-0011 R17).
 *
 * **두 가지를 나눠 다룬다.**
 * - **남에게도 보이는 것은 소프트 삭제한다** — 글·댓글·질문·답변·답글·DM. 지우면 남의
 *   스레드에 구멍이 나고, 답변이 달린 질문이 통째로 사라지며, 대화 상대는 자기 말만 남은
 *   독백을 보게 된다. 행은 남기고 표시에서만 지운다(읽기 계층이 `deletedAt IS NULL`로 거른다).
 * - **본인만의 데이터는 물리 삭제한다** — 좋아요·숨김·차단은 남길 이유가 없다.
 *
 * **소프트 삭제가 영구 보관을 뜻하지 않는다** — DM은 보관 기간(180일)이 지나면 정리 배치가
 * 행째로 지우고, 그때 첨부 사진의 오브젝트도 함께 정리된다.
 *
 * 이 도메인의 저장소만 만진다 — 다른 도메인의 정리는 각자의 핸들러가 한다.
 */
@Service
class MemberDataEraseService(
    private val erasePort: MemberDataErasePort,
    private val softDeletePort: MemberContentSoftDeletePort,
    private val questionCounterPort: SpotQuestionCounterPort,
    private val clockPort: ClockPort,
) : MemberDataEraseUseCase {

    /**
     * **REQUIRES_NEW여야 한다.** 이 유스케이스는 `AFTER_COMMIT` 리스너가 부르는데, 그 시점의
     * 원본 트랜잭션은 이미 커밋됐으면서도 스레드에 **묶여 있다.** 기본 전파(REQUIRED)로 두면
     * 커밋할 수 없는 그 트랜잭션에 조용히 합류해 정리가 흘러가지 않는다 —
     * 예외도 나지 않아 성공한 것처럼 보인다(실제로 탈퇴 후 글·댓글이 남았다).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun eraseMemberData(memberId: Long) {
        // 한 번의 탈퇴가 남기는 시각은 하나여야 한다 — 표마다 갈리면 나중에 한 값으로 읽을 수 없다.
        val now = clockPort.now()

        // **칠하기 전에 세어 둔다.** 소프트 삭제 후에는 어느 질문의 수를 내려야 하는지 알 수 없다.
        val questionIdsToDecrease = softDeletePort.findQuestionIdsToDecrease(memberId)

        // ── 콘텐츠: 소프트 삭제 ──
        //
        // 질문을 칠하면 거기 달린 **남의 답변도 함께 사라진다**(AC-17.2) — 답변 조회가 부모
        // 질문을 먼저 읽어 거르기 때문이며, 자식을 훑지 않아도 성립한다.
        val posts = softDeletePort.softDeletePostsByAuthor(memberId, now)
        val comments = softDeletePort.softDeleteCommentsByAuthor(memberId, now)
        val questions = softDeletePort.softDeleteSpotQuestionsByAuthor(memberId, now)
        val answers = softDeletePort.softDeleteSpotAnswersByAuthor(memberId, now)
        val replies = softDeletePort.softDeleteSpotAnswerRepliesByAuthor(memberId, now)
        // 대화는 남기고 메시지만 가린다(SPEC-0017 AC17-2) — 지우면 상대가 자기 말만 남은
        // 독백을 보게 된다. 보관 기간이 지나면 정리 배치가 행째로 지운다.
        val messages = softDeletePort.softDeleteMessagesBySender(memberId, now)

        // **칠한 뒤에 내린다** — 먼저 내리고 삭제가 실패하면 수만 줄어든 상태가 남는다.
        // 같은 질문에 여러 건이면 그 수만큼 내린다(id가 중복으로 담겨 온다).
        questionIdsToDecrease.forEach(questionCounterPort::decreaseAnswerCount)

        // ── 본인만의 데이터: 물리 삭제 ──
        val likes = erasePort.deleteLikesByMember(memberId)
        val hides = erasePort.deleteHidesByMember(memberId)
        val blocks = erasePort.deleteBlocksInvolving(memberId)

        // 개인정보를 담지 않는다(POL-013) — 식별자와 건수만 남긴다.
        // DM 본문·수신자는 애초에 기록 대상이 아니다.
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
