package org.around.ieuri.community.application.port.out.persistence

import org.around.ieuri.community.domain.aggregate.Conversation
import org.around.ieuri.community.domain.aggregate.Message
import org.around.ieuri.community.domain.aggregate.MessageReport
import java.time.Instant

/** 대화 적재·저장. */
interface ConversationRepositoryPort {
    fun findById(conversationId: Long): Conversation?

    /** 이미 있으면 그것을, 없으면 새로 만들어 돌려준다. 경합에서도 중복 행이 생기지 않는다. */
    fun saveIfAbsent(conversation: Conversation): Conversation

    /** 최근 활동 시각 갱신. 메시지 전송이 목록의 정렬을 바꾼다(AC9-1). */
    fun touch(conversationId: Long, at: Instant)

    /** 읽음 지점을 저장한다. 트랜잭션 안에서 관리 엔티티에 적용한다. */
    fun updateReadState(conversation: Conversation)
}

/** 메시지 적재·저장. 본문 암호화는 어댑터가 한다 — 도메인은 평문을 들고 있다. */
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
 * 보관 기간 만료 메시지 물리 삭제(R16).
 * - 탈퇴로 소프트 삭제된 행도 `created_at`이 보관 기간을 넘기면 여기서 지워진다.
 */
interface MessagePurgePort {
    /**
     * [cutoff] 이전에 만들어진 메시지 행을 최대 [limit]건 지운다.
     * - 첨부 오브젝트는 지우지 않는다 — 키를 돌려주고 스토리지 삭제는 호출자가 트랜잭션 밖에서 한다.
     */
    fun deleteCreatedBefore(cutoff: Instant, limit: Int): ExpiredMessagePurgeResult
}

/**
 * 만료 메시지 행 삭제 결과.
 * - [photoObjectKeys]는 스토리지에서 지워야 할 키다. 행이 이미 사라져 여기 없는 키는 되찾을 수 없다.
 */
data class ExpiredMessagePurgeResult(
    val deletedCount: Int,
    val photoObjectKeys: List<String>,
)
