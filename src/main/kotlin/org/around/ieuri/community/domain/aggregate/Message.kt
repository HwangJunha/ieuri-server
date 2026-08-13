package org.around.ieuri.community.domain.aggregate

import org.around.ieuri.community.domain.vo.PhotoObjectKey
import java.time.Duration
import java.time.Instant

/**
 * DM 메시지. 애그리게이트 루트.
 *
 * **본문은 평문으로 들고 있고, 암호화는 영속 어댑터가 한다**(R16). 도메인이 암호화를 알면
 * 길이 판정이 암호문 길이가 되어 버린다 — 1~1000자는 사용자가 입력한 글자 수다.
 *
 * 수정·삭제는 비범위다. 삭제는 보관 기간이 지났을 때의 물리 삭제뿐이다.
 */
class Message private constructor(
    val id: Long?,
    val conversationId: Long,
    val senderId: Long,
    val body: String,
    /**
     * 인용한 피드. **방이 아니라 메시지에 붙는다** — 같은 상대와의 방은 하나인데 피드는
     * 여러 개라, 방에 고정하면 새 피드로 말을 걸어도 첫 피드가 남아 틀린 맥락이 된다.
     * 피드가 지워지면 null이 되고 메시지는 남는다.
     */
    val feedItemId: Long?,
    /** 첨부 사진. 텍스트 없이 사진만 보낼 수 있다. */
    val photoKey: PhotoObjectKey?,
    val createdAt: Instant?,
) {
    fun sentBy(memberId: Long): Boolean = senderId == memberId

    /** 본문 없이 첨부만 있는 메시지인지. 클라이언트의 말풍선 모양이 갈린다. */
    val hasBody: Boolean get() = body.isNotEmpty()

    companion object {
        const val BODY_MAX_LENGTH = 1000

        /** 서버 보관 기간(POL-050). 기준은 **메시지 생성 시각**이며 대화의 활성 여부와 무관하다. */
        val RETENTION: Duration = Duration.ofDays(180)

        /**
         * 새 메시지. 본문은 트리밍 후 판정하며 상한을 넘으면 **잘라서 저장하지 않고
         * 거부한다**(AC9-3).
         *
         * **본문이 비어도 첨부가 있으면 성립한다** — 사진만, 또는 피드 인용만 보낼 수 있다.
         * 셋 다 비면 보낼 것이 없는 전송이라 거부한다(AC9-2).
         *
         * 사진 키의 **소유권**만 여기서 본다 — 실제 업로드 여부는 저장소에 물어야 하므로
         * 애플리케이션 계층의 책임이다(피드·글과 같은 층위 구분).
         */
        fun create(
            conversationId: Long,
            senderId: Long,
            body: String,
            feedItemId: Long? = null,
            photoKey: PhotoObjectKey? = null,
        ): Message {
            require(conversationId > 0) { "conversationId는 양수여야 합니다." }
            require(senderId > 0) { "senderId는 양수여야 합니다." }
            require(feedItemId == null || feedItemId > 0) { "feedItemId는 양수여야 합니다." }
            val trimmed = body.trim()
            require(trimmed.length <= BODY_MAX_LENGTH) { "메시지는 ${BODY_MAX_LENGTH}자를 넘을 수 없습니다." }
            require(trimmed.isNotEmpty() || feedItemId != null || photoKey != null) {
                "메시지를 입력해 주세요."
            }
            require(photoKey == null || photoKey.ownedBy(senderId)) {
                "본인에게 발급된 사진 키가 아닙니다."
            }
            require(photoKey == null || photoKey.confirmed) {
                "업로드 확인을 마치지 않은 사진 키입니다."
            }
            return Message(
                id = null,
                conversationId = conversationId,
                senderId = senderId,
                body = trimmed,
                feedItemId = feedItemId,
                photoKey = photoKey,
                createdAt = null,
            )
        }

        /** 저장소에서 복원. 본문은 어댑터가 복호화한 평문으로 들어온다. */
        fun restore(
            id: Long,
            conversationId: Long,
            senderId: Long,
            body: String,
            createdAt: Instant,
            feedItemId: Long? = null,
            photoKey: PhotoObjectKey? = null,
        ): Message = Message(
            id = id,
            conversationId = conversationId,
            senderId = senderId,
            body = body,
            feedItemId = feedItemId,
            photoKey = photoKey,
            createdAt = createdAt,
        )

        /**
         * 보관 기간이 지나 삭제 대상인 메시지의 생성 시각 상한. 이 시각보다 **이전에** 만들어진
         * 메시지가 지워진다(AC16-1·16-2).
         *
         * 대화가 활발해도 개별 메시지의 보관 기간은 늘어나지 않는다(AC16-3) — 기준이 대화의
         * 마지막 활동이 아니라 메시지 자신의 생성 시각이기 때문이다.
         */
        fun retentionCutoff(now: Instant): Instant = now.minus(RETENTION)

        /** [createdAt]이 보관 기간을 넘겼는지. 경계(정확히 6개월)는 아직 삭제 대상이 아니다. */
        fun isExpired(createdAt: Instant, now: Instant): Boolean =
            createdAt.isBefore(retentionCutoff(now))
    }
}
