package org.around.ieuri.community.application.port.`in`.command

import org.around.ieuri.community.application.command.MarkConversationReadCommand
import org.around.ieuri.community.application.command.NotifyTypingCommand
import org.around.ieuri.community.application.command.ReportMessagesCommand
import org.around.ieuri.community.application.command.SendMessageCommand
import org.around.ieuri.community.application.command.StartConversationCommand
import org.around.ieuri.community.domain.vo.ContentLanguage

/** 대화·메시지 쓰기. 대화 시작과 첫 메시지 전송은 별개라 빈 대화가 존재할 수 있다. */
interface ConversationCommandUseCase {
    /** 상대 회원과 대화를 시작한다(SPEC-0021). 같은 상대와 대화가 이미 있으면 그 id를 돌려준다. */
    fun startConversation(command: StartConversationCommand, memberId: Long): Long

    /**
     * 메시지를 보내고 생성된 id를 돌려준다. 대화의 최근 활동 시각이 함께 갱신된다.
     * - [language]는 인용 스냅샷의 장소명을 해소하는 언어다. 해소되지 않으면 오류 없이 인용 없이 보낸다.
     */
    fun sendMessage(
        command: SendMessageCommand,
        memberId: Long,
        language: ContentLanguage,
    ): Long

    /** 타이핑 알림. 수신자가 구독 중이 아니어도 성공한다. 영속화하지 않으며 전달 보장이 없다. */
    fun notifyTyping(command: NotifyTypingCommand, memberId: Long)

    /**
     * 대화를 [messageId]까지 읽은 것으로 표시하고, 남은 안 읽은 수를 돌려준다.
     * - 서버는 조회를 읽음으로 간주하지 않는다 — 읽음 시점은 클라이언트가 정한다.
     * - [messageId]가 null이면 마지막 메시지까지 읽은 것으로 본다. 읽음 지점은 뒤로 가지 않는다(멱등).
     */
    fun markConversationRead(command: MarkConversationReadCommand, memberId: Long): Int
}

/** DM 메시지 신고(R15). 여러 건을 한 신고로 접수하며 누적 자동 숨김은 없다(POL-049). */
interface MessageReportCommandUseCase {
    fun reportMessages(command: ReportMessagesCommand, memberId: Long)
}

/** 보관 기간이 지난 메시지 삭제(R16). 배치가 회차당 상한을 두고 반복해 부른다. */
interface MessageRetentionUseCase {
    /** 한 회차를 지우고 삭제 건수를 돌려준다. 상한보다 적게 지웠으면 더 지울 것이 없다는 뜻이다. */
    fun purgeExpiredBatch(limit: Int): Int
}
