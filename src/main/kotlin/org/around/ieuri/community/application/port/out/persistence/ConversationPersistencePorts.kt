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
 * 탈퇴(R17)는 이 경로가 아니라 소프트 삭제다
 * ([MemberContentSoftDeletePort.softDeleteMessagesBySender]) — 지우면 상대가 자기 말만 남은
 * 독백을 보게 되기 때문이다. **둘은 서로를 대체하지 않는다**: 탈퇴가 가린 행도 생성 시각이
 * 보관 기간을 넘기면 여기서 행째로 사라진다(선별이 `created_at`만 보므로 자동으로 그렇다).
 */
interface MessagePurgePort {
    /**
     * [cutoff] 이전에 만들어진 메시지 **행**을 최대 [limit]건 지운다.
     *
     * **첨부 오브젝트는 지우지 않는다** — 지워야 할 키를
     * [ExpiredMessagePurgeResult.photoObjectKeys]로 돌려주고, 스토리지 삭제는 호출자가 한다.
     * 원격 왕복을 이 트랜잭션 안에 두면 커넥션 보유 시간이 원격 레이턴시에 묶인다.
     */
    fun deleteCreatedBefore(cutoff: Instant, limit: Int): ExpiredMessagePurgeResult
}

/**
 * 만료 메시지 행 삭제 결과.
 *
 * @property deletedCount 지운 메시지 행 수.
 * @property photoObjectKeys 뒤이어 스토리지에서 지워야 할 첨부 오브젝트 키. 행이 이미 사라졌으므로
 *   여기 담기지 않은 키는 되찾을 방법이 없다.
 */
data class ExpiredMessagePurgeResult(
    val deletedCount: Int,
    val photoObjectKeys: List<String>,
)
