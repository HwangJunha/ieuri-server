package org.around.ieuri.community.adapter.event.listener

import org.around.ieuri.auth.domain.event.MemberWithdrawn
import org.around.ieuri.community.application.block.provided.BlockCommandUseCase
import org.around.ieuri.community.application.comment.provided.CommentCommandUseCase
import org.around.ieuri.community.application.conversation.provided.ConversationCommandUseCase
import org.around.ieuri.community.application.post.provided.PostCommandUseCase
import org.around.ieuri.community.application.question.provided.SpotQuestionCommandUseCase
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 탈퇴 이벤트(커밋 후)를 받아 애그리게이트마다 자기 데이터를 지우게 한다. 메서드 하나가 애그리게이트 하나다.
 * - 삭제 시각은 이벤트의 `occurredAt` — 한 번의 탈퇴는 모든 표에 같은 시각을 남긴다.
 * - 각 메서드가 예외를 삼키고 로그만 남긴다 — 한 애그리게이트의 실패가 나머지를 막지 않게. 재시도하지 않는다.
 * - 클래스명에 도메인 접두어를 유지한다 — 빈 이름이 겹치면 기동이 실패한다.
 */
@Component
class CommunityMemberWithdrawnListener(
    private val postCommandUseCase: PostCommandUseCase,
    private val commentCommandUseCase: CommentCommandUseCase,
    private val questionCommandUseCase: SpotQuestionCommandUseCase,
    private val conversationCommandUseCase: ConversationCommandUseCase,
    private val blockCommandUseCase: BlockCommandUseCase,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun erasePosts(event: MemberWithdrawn) = erase(event, "posts") { postCommandUseCase.eraseMemberContent(event.memberId, event.occurredAt) }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun eraseComments(event: MemberWithdrawn) = erase(event, "comments") { commentCommandUseCase.eraseMemberContent(event.memberId, event.occurredAt) }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun eraseSpotQuestions(event: MemberWithdrawn) = erase(event, "spotQuestions") { questionCommandUseCase.eraseMemberContent(event.memberId, event.occurredAt) }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun eraseMessages(event: MemberWithdrawn) = erase(event, "messages") { conversationCommandUseCase.eraseMemberContent(event.memberId, event.occurredAt) }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun eraseBlocks(event: MemberWithdrawn) = erase(event, "blocks") { blockCommandUseCase.eraseMemberContent(event.memberId, event.occurredAt) }

    /** 식별자와 건수만 남긴다(POL-013). */
    private fun erase(event: MemberWithdrawn, target: String, step: () -> Int) {
        runCatching(step)
            .onSuccess { log.info("community {} cleared for withdrawn member {}: rows={}", target, event.memberId, it) }
            .onFailure { log.error("failed to clear community {} for withdrawn member {}", target, event.memberId, it) }
    }

    private companion object {
        private val log = LoggerFactory.getLogger(CommunityMemberWithdrawnListener::class.java)
    }
}
