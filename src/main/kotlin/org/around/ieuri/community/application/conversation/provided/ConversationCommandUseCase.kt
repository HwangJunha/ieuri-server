package org.around.ieuri.community.application.conversation.provided

import org.around.ieuri.community.application.conversation.provided.dto.MarkConversationReadCommand
import org.around.ieuri.community.application.conversation.provided.dto.NotifyTypingCommand
import org.around.ieuri.community.application.conversation.provided.dto.SendMessageCommand
import org.around.ieuri.community.application.conversation.provided.dto.StartConversationCommand
import org.around.ieuri.community.domain.shared.ContentLanguage
import java.time.Instant

/** 대화·메시지 쓰기. 대화 시작과 첫 메시지 전송은 별개라 빈 대화가 존재할 수 있다. */
interface ConversationCommandUseCase {
    /** 같은 상대와 대화가 이미 있으면 그 id를 돌려준다. 탈퇴·없는 회원은 MEMBER_NOT_FOUND. */
    fun startConversation(command: StartConversationCommand, memberId: Long): Long

    /** 생성된 메시지 id. [language]는 인용 스냅샷의 장소명 언어이며, 해소되지 않으면 오류 없이 인용 없이 보낸다. */
    fun sendMessage(command: SendMessageCommand, memberId: Long, language: ContentLanguage): Long

    /** 타이핑 알림. 수신자가 구독 중이 아니어도 성공한다. 영속화하지 않으며 전달 보장이 없다. */
    fun notifyTyping(command: NotifyTypingCommand, memberId: Long)

    /** 남은 안 읽은 수. 조회는 읽음이 아니고 이 호출로만 읽는다. 읽음 지점은 뒤로 가지 않는다(멱등). */
    fun markConversationRead(command: MarkConversationReadCommand, memberId: Long): Int

    /** 탈퇴 정리. 탈퇴 커밋 뒤 리스너가 부르며 새 트랜잭션이다. 한 번의 탈퇴는 모든 애그리게이트에 같은 [deletedAt]을 남긴다. 바뀐 행 수(로그용). */
    fun eraseMemberContent(memberId: Long, deletedAt: Instant): Int
}
