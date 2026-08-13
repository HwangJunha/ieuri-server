package org.around.ieuri.community.application.port.out.persistence

import org.around.ieuri.community.domain.aggregate.Conversation
import org.around.ieuri.community.domain.aggregate.FeedItem
import org.around.ieuri.community.domain.aggregate.Message
import org.around.ieuri.community.domain.aggregate.MessageReport
import java.time.Instant

/**
 * 피드 애그리게이트 적재·저장(SPEC-0017).
 *
 * [save]는 **사진 키의 재사용을 저장소의 유일 제약으로 판정한다** — 이미 다른 피드에 실린
 * 키면 [PhotoKeyAlreadyUsed]를 던진다. 미리 조회해 확인하면 경합에서 두 피드가 같은 키를
 * 나눠 갖는다.
 */
interface FeedItemRepositoryPort {
    /** 삭제되지 않은 피드만. 없으면 null. */
    fun findById(feedItemId: Long): FeedItem?

    fun save(feedItem: FeedItem): FeedItem
}

/** 사진 키가 이미 다른 피드에 실려 있을 때. 어댑터가 유일 제약 위반을 이 예외로 옮긴다. */
class PhotoKeyAlreadyUsed : RuntimeException("이미 사용된 사진 키입니다.")

/**
 * 피드의 파생 계수 증감. 애그리게이트를 거치지 않는다 — 이유는 [PostCounterPort]와 같다
 * (읽고 고쳐 쓰면 동시 요청이 서로의 증가를 덮어쓴다).
 */
interface FeedItemCounterPort {
    fun increaseLikeCount(feedItemId: Long)

    /** 0 아래로 내려가지 않는다 — DB의 CHECK 제약과 조건절이 함께 막는다. */
    fun decreaseLikeCount(feedItemId: Long)
}

/** 피드 존재 확인과 신고 누적 자동 숨김. 이유는 [PostModerationPort]와 같다. */
interface FeedItemModerationPort {
    fun exists(feedItemId: Long): Boolean

    /** 이미 숨겨져 있으면 아무것도 하지 않고 false. */
    fun hide(feedItemId: Long): Boolean
}

/** 피드 좋아요 유일성. 멱등 판정을 저장소가 맡는다(이유는 [PostLikeRepositoryPort]와 같다). */
interface FeedItemLikeRepositoryPort {
    /** 새로 저장했으면 true, 이미 있었으면 false(멱등 no-op). */
    fun save(feedItemId: Long, memberId: Long): Boolean

    /** 실제로 지웠으면 true, 원래 없었으면 false(멱등 no-op). */
    fun delete(feedItemId: Long, memberId: Long): Boolean
}

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
