package org.around.ieuri.community.application.port.out.persistence

import org.around.ieuri.community.domain.aggregate.Conversation
import org.around.ieuri.community.domain.aggregate.Message
import org.around.ieuri.community.domain.aggregate.MessageReport
import java.time.Instant

/**
 * 대화 적재·저장.
 *
 * [saveIfAbsent]가 **경합을 흡수한다** — 두 요청이 동시에 같은 쌍의 대화를 만들면 하나는
 * 유일 제약에 걸리는데, 그때 새로 만드는 대신 이미 만들어진 것을 돌려준다(AC7-2).
 */
interface ConversationRepositoryPort {
    fun findById(conversationId: Long): Conversation?

    /** 이미 있으면 그것을, 없으면 새로 만들어 돌려준다. 경합에서도 중복 행이 생기지 않는다. */
    fun saveIfAbsent(conversation: Conversation): Conversation

    /** 최근 활동 시각 갱신. 메시지 전송이 목록의 정렬을 바꾼다(AC9-1). */
    fun touch(conversationId: Long, at: Instant)

    /**
     * 읽음 지점을 저장한다. 트랜잭션 안에서 관리 엔티티에 적용하고 더티 체킹에 맡긴다 —
     * 분리된 엔티티를 새로 만들어 저장하면 다른 컬럼을 덮어쓴다.
     */
    fun updateReadState(conversation: Conversation)
}

/**
 * 메시지 적재·저장. **본문 암호화는 어댑터가 한다**(R16) — 도메인은 평문을 들고 있다.
 */
interface MessageRepositoryPort {
    fun save(message: Message): Message

    /** 신고 검증이 쓰는 조회. 없는 id는 결과에 없다 — 호출자가 개수로 부재를 판정한다. */
    fun findAllByIds(messageIds: List<Long>): List<Message>
}

/** DM 신고 저장. 중복 신고는 유일 제약이 판정한다(AC15-6). */
interface MessageReportRepositoryPort {
    /** 신고자가 이미 신고한 메시지가 하나라도 있으면 false. */
    fun save(report: MessageReport): Boolean
}

/**
 * 보관 기간 만료 메시지 삭제(R16).
 *
 * **물리 삭제다.** 소프트 삭제로 두면 보관 기간을 정한 의미가 없다 — 지웠다고 하면서
 * 데이터는 남아 있게 된다.
 *
 * 탈퇴 회원의 메시지 삭제(R17)는 [MemberDataErasePort.deleteMessagesBySender]가 맡는다 —
 * 같은 삭제를 두 포트가 각각 제공하면 한쪽만 고쳐졌을 때 두 경로가 조용히 갈린다.
 */
interface MessagePurgePort {
    /** [cutoff] 이전에 만들어진 메시지를 최대 [limit]건 지우고 삭제 건수를 돌려준다. */
    fun deleteCreatedBefore(cutoff: Instant, limit: Int): Int
}
