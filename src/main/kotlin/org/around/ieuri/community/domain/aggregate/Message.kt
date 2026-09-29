package org.around.ieuri.community.domain.aggregate

import org.around.ieuri.community.domain.vo.PhotoObjectKey
import org.around.ieuri.community.domain.vo.SpotQaOrigin
import java.time.Duration
import java.time.Instant

/**
 * DM 메시지. 애그리게이트 루트.
 * - 본문은 평문으로 들고 있고, 암호화는 영속 어댑터가 한다(R16).
 * - 행은 보관 기간이 지났을 때만 사라진다. 탈퇴 표시(`deleted_at`)는 엔티티에만 있다.
 */
class Message private constructor(
    val id: Long?,
    val conversationId: Long,
    val senderId: Long,
    val body: String,
    /** 첨부 사진. 텍스트 없이 사진만 보낼 수 있다. */
    val photoKey: PhotoObjectKey?,
    /** 이 메시지가 인용한 장소 질문/답변의 스냅샷(SPEC-0026). 인용이 없으면 null. */
    val spotQaOrigin: SpotQaOrigin?,
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
         * 새 메시지. 본문은 트리밍 후 판정한다.
         * - 상한을 넘으면 잘라 저장하지 않고 거부한다(AC9-3).
         * - 본문·사진·인용이 모두 비면 거부한다. 하나라도 있으면 성립한다.
         * - 사진 키는 소유권만 본다. 실제 업로드 여부는 애플리케이션 계층이 확인한다.
         */
        fun create(
            conversationId: Long,
            senderId: Long,
            body: String,
            photoKey: PhotoObjectKey? = null,
            spotQaOrigin: SpotQaOrigin? = null,
        ): Message {
            require(conversationId > 0) { "conversationId는 양수여야 합니다." }
            require(senderId > 0) { "senderId는 양수여야 합니다." }
            val trimmed = body.trim()
            require(trimmed.length <= BODY_MAX_LENGTH) { "메시지는 ${BODY_MAX_LENGTH}자를 넘을 수 없습니다." }
            require(trimmed.isNotEmpty() || photoKey != null || spotQaOrigin != null) {
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
                photoKey = photoKey,
                spotQaOrigin = spotQaOrigin,
                createdAt = null,
            )
        }

        /**
         * 저장소에서 복원. 본문은 어댑터가 복호화한 평문으로 들어온다.
         * - 빈 메시지를 거부하지 않는다(SPEC-0021 AC4.1). 막으면 과거 메시지가 든 대화 조회가 실패한다.
         */
        fun restore(
            id: Long,
            conversationId: Long,
            senderId: Long,
            body: String,
            createdAt: Instant,
            photoKey: PhotoObjectKey? = null,
            spotQaOrigin: SpotQaOrigin? = null,
        ): Message = Message(
            id = id,
            conversationId = conversationId,
            senderId = senderId,
            body = body,
            photoKey = photoKey,
            spotQaOrigin = spotQaOrigin,
            createdAt = createdAt,
        )

        /** 삭제 기준 시각. 이 시각보다 이전에 만들어진 메시지가 지워진다(AC16-1~16-3). */
        fun retentionCutoff(now: Instant): Instant = now.minus(RETENTION)

        /** [createdAt]이 보관 기간을 넘겼는지. 경계(정확히 6개월)는 아직 삭제 대상이 아니다. */
        fun isExpired(createdAt: Instant, now: Instant): Boolean = createdAt.isBefore(retentionCutoff(now))
    }
}
