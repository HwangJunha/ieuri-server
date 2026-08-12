package org.around.ieuri.community.application.port.`in`.command

import org.around.ieuri.community.application.command.CreateFeedItemCommand
import org.around.ieuri.community.application.command.LikeFeedItemCommand
import org.around.ieuri.community.application.command.NotifyTypingCommand
import org.around.ieuri.community.application.command.ReportMessagesCommand
import org.around.ieuri.community.application.command.SendMessageCommand
import org.around.ieuri.community.application.command.StartConversationCommand

/**
 * 피드 쓰기 유스케이스(SPEC-0017). [memberId]는 인증된 회원이며 리졸버가 확립한다.
 */
interface FeedItemCommandUseCase {
    /** 생성된 피드의 id. 이유는 PostCommandUseCase.createPost와 같다. */
    fun createFeedItem(command: CreateFeedItemCommand, memberId: Long): Long
}

/** 피드 좋아요. 양방향 모두 멱등이며 연산 후의 좋아요 수를 돌려준다(R6). */
interface FeedItemLikeCommandUseCase {
    fun like(command: LikeFeedItemCommand, memberId: Long): Int

    fun unlike(command: LikeFeedItemCommand, memberId: Long): Int
}

/**
 * 대화·메시지 쓰기(R7·R9·R10).
 *
 * 대화 시작과 첫 메시지 전송은 **별개 연산이다** — 빈 대화가 존재할 수 있다.
 */
interface ConversationCommandUseCase {
    /**
     * 피드 작성자와의 대화를 시작한다. 같은 상대와 대화가 이미 있으면 **새로 만들지 않고
     * 기존 대화의 id를 돌려준다**(AC7-2).
     */
    fun startConversation(command: StartConversationCommand, memberId: Long): Long

    /** 생성된 메시지의 id. 대화의 최근 활동 시각이 함께 갱신된다(AC9-1). */
    fun sendMessage(command: SendMessageCommand, memberId: Long): Long

    /**
     * 타이핑 알림. **수신자가 구독 중이 아니어도 성공한다**(AC10-2) — 받을 사람이 없는 것은
     * 실패가 아니다. 영속화하지 않으며 전달 보장이 없다.
     */
    fun notifyTyping(command: NotifyTypingCommand, memberId: Long)
}

/**
 * DM 메시지 신고(R15). 여러 건을 **한 신고로** 접수한다.
 *
 * 누적 자동 숨김을 적용하지 않는다(POL-049) — 기록만 하고 조치는 사람이 판단한다.
 */
interface MessageReportCommandUseCase {
    fun reportMessages(command: ReportMessagesCommand, memberId: Long)
}

/**
 * 보관 기간이 지난 메시지 삭제(R16). 배치가 부른다.
 *
 * **회차당 상한을 두고 반복한다** — 6개월치가 한 번에 쌓인 상태에서 단일 트랜잭션으로 지우면
 * 락과 WAL이 함께 커진다.
 */
interface MessageRetentionUseCase {
    /** 한 회차를 지우고 삭제 건수를 돌려준다. 상한보다 적게 지웠으면 더 지울 것이 없다는 뜻이다. */
    fun purgeExpiredBatch(limit: Int): Int
}
