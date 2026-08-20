package org.around.ieuri.community.application.port.`in`.command

import org.around.ieuri.community.application.command.MarkConversationReadCommand
import org.around.ieuri.community.application.command.NotifyTypingCommand
import org.around.ieuri.community.application.command.ReportMessagesCommand
import org.around.ieuri.community.application.command.SendMessageCommand
import org.around.ieuri.community.application.command.StartConversationCommand
import org.around.ieuri.community.domain.vo.ContentLanguage

/**
 * 대화·메시지 쓰기(R7·R9·R10).
 *
 * 대화 시작과 첫 메시지 전송은 **별개 연산이다** — 빈 대화가 존재할 수 있다.
 */
interface ConversationCommandUseCase {
    /**
     * 상대 회원을 지목해 대화를 시작한다(SPEC-0021 R2). 같은 상대와 대화가 이미 있으면
     * **새로 만들지 않고 기존 대화의 id를 돌려준다**(AC2.2).
     */
    fun startConversation(command: StartConversationCommand, memberId: Long): Long

    /**
     * 생성된 메시지의 id. 대화의 최근 활동 시각이 함께 갱신된다(AC9-1).
     *
     * [language]는 **인용 스냅샷의 장소명을 해소하는 언어다**(SPEC-0029 AC-25.4) — 스냅샷을
     * 만드는 쪽, 곧 이 메시지를 쓰는 회원의 활성 언어다. 그 언어에서 장소가 해소되지 않으면
     * 인용 없이 전송된다(기존 AC-13.4와 같은 처리이며 오류가 아니다).
     */
    fun sendMessage(
        command: SendMessageCommand,
        memberId: Long,
        language: ContentLanguage,
    ): Long

    /**
     * 타이핑 알림. **수신자가 구독 중이 아니어도 성공한다**(AC10-2) — 받을 사람이 없는 것은
     * 실패가 아니다. 영속화하지 않으며 전달 보장이 없다.
     */
    fun notifyTyping(command: NotifyTypingCommand, memberId: Long)

    /**
     * 대화를 [messageId]까지 읽은 것으로 표시하고, 남은 안 읽은 수를 돌려준다.
     *
     * **읽음 시점은 클라이언트가 정한다** — 채팅방 진입·스크롤 하단 도달·포그라운드 복귀 등
     * 어디서 부를지는 UX 판단이라 서버가 조회를 읽음으로 간주하지 않는다.
     *
     * [messageId]가 null이면 **그 대화의 마지막 메시지까지** 읽은 것으로 본다(모두 읽음).
     * 읽음 지점은 뒤로 가지 않으므로 같은 요청을 여러 번 보내도 안전하다.
     */
    fun markConversationRead(command: MarkConversationReadCommand, memberId: Long): Int
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
