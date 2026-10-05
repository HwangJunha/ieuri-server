package org.around.ieuri.community.domain.conversation

import org.around.ieuri.community.domain.vo.PhotoObjectKey
import org.around.ieuri.community.domain.vo.SpotQaOrigin
import java.time.Duration
import java.time.Instant

/**
 * 대화 애그리게이트의 DM 메시지 한 행. [Conversation.send]로만 만든다.
 * - 본문은 평문으로 들고 있고 암호화는 영속 어댑터가 한다. 저장된 과거 메시지는 비어 있어도 그대로 읽는다.
 */
data class Message(
    val id: Long?,
    val conversationId: Long,
    val senderId: Long,
    val body: String,
    /** 첨부 사진. 텍스트 없이 사진만 보낼 수 있다. */
    val photoKey: PhotoObjectKey?,
    /** 인용한 장소 질문/답변의 스냅샷. 인용이 없으면 null. */
    val spotQaOrigin: SpotQaOrigin?,
    val createdAt: Instant?,
) {
    fun sentBy(memberId: Long): Boolean = senderId == memberId

    companion object {
        const val BODY_MAX_LENGTH = 1000

        /** 서버 보관 기간(POL-050). 기준은 메시지 생성 시각이며 대화의 활성 여부와 무관하다. */
        val RETENTION: Duration = Duration.ofDays(180)

        /** 이 시각보다 이전에 만들어진 메시지가 지워진다. 경계(정확히 6개월)는 아직 대상이 아니다. */
        fun retentionCutoff(now: Instant): Instant = now.minus(RETENTION)
    }
}
