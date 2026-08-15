package org.around.ieuri.community.domain.aggregate

import org.around.ieuri.community.domain.vo.PhotoObjectKey
import java.time.Instant

/**
 * 피드 항목. 애그리게이트 루트.
 *
 * **[Post]와 별개 애그리게이트다.** 불변식이 정반대라 공통 상위 타입을 두지 않는다 —
 * 피드는 spot과 사진이 필수이고 글은 둘 다 선택이다. 공통 추상을 두면 어느 쪽 불변식도
 * 표현하지 못한다(SPEC-0017).
 *
 * spot 1곳과 사진 1~5장은 **생성 시점의 불변식**이라 [create]가 강제한다. 애플리케이션
 * 서비스나 리졸버에서 검사하지 않는다 — 도메인 밖에 두면 새 호출 경로가 생길 때마다
 * 검사가 빠진다.
 */
class FeedItem private constructor(
    val id: Long?,
    /** 연결된 장소. **필수다** — 피드에서 spot은 장식이 아니라 여행지로 들어가는 입구다. */
    val catalogId: Long,
    val authorId: Long,
    body: String,
    photoKeys: List<PhotoObjectKey>,
    likeCount: Int,
    hidden: Boolean,
    deletedAt: Instant?,
    val createdAt: Instant?,
) {
    var body: String = body
        private set

    /** 배열 순서가 표시 순서다. 1~5개이며 비어 있을 수 없다. */
    var photoKeys: List<PhotoObjectKey> = photoKeys
        private set

    var likeCount: Int = likeCount
        private set

    var hidden: Boolean = hidden
        private set

    var deletedAt: Instant? = deletedAt
        private set

    val deleted: Boolean get() = deletedAt != null

    /** 좋아요 반영. 멱등성은 저장소의 유일 제약이 판정하고, 여기서는 집계만 올린다. */
    fun increaseLikeCount() {
        likeCount += 1
    }

    /** 좋아요 취소 반영. 0 아래로 내려가지 않는다. */
    fun decreaseLikeCount() {
        if (likeCount > 0) likeCount -= 1
    }

    /** 신고 누적으로 숨긴다. 이미 숨겨진 피드에 다시 불려도 안전하다. */
    fun hide() {
        hidden = true
    }

    fun ownedBy(memberId: Long): Boolean = authorId == memberId

    /** 호출자에게 이 피드가 보이는지. 숨겨진 피드는 작성자 본인에게만 남는다. */
    fun visibleTo(memberId: Long): Boolean = !deleted && (!hidden || ownedBy(memberId))

    companion object {
        const val BODY_MAX_LENGTH = 500
        const val MIN_PHOTOS = 1
        const val MAX_PHOTOS = 5

        /**
         * 새 피드. 본문은 트리밍 후 판정하므로 공백만 있는 본문은 빈 문자열로 거부된다.
         *
         * **사진이 6장이면 앞 5장만 취하지 않고 거부한다**(AC2-4). 조용한 절삭은 사용자가
         * 올린 것을 말없이 버리는 것이다.
         *
         * 사진 키의 **소유권**만 여기서 본다 — 실제 업로드 여부와 재사용 여부는 저장소에
         * 물어야 하므로 애플리케이션 계층의 책임이다.
         */
        fun create(
            catalogId: Long,
            authorId: Long,
            body: String,
            photoKeys: List<PhotoObjectKey>,
        ): FeedItem {
            require(authorId > 0) { "authorId는 양수여야 합니다." }
            require(catalogId > 0) { "catalogId는 양수여야 합니다." }
            val trimmed = body.trim()
            require(trimmed.isNotEmpty()) { "본문은 비어 있을 수 없습니다." }
            require(trimmed.length <= BODY_MAX_LENGTH) { "본문은 ${BODY_MAX_LENGTH}자를 넘을 수 없습니다." }
            require(photoKeys.size >= MIN_PHOTOS) { "사진은 최소 ${MIN_PHOTOS}장이 필요합니다." }
            require(photoKeys.size <= MAX_PHOTOS) { "사진은 최대 ${MAX_PHOTOS}장입니다." }
            require(photoKeys.all { it.ownedBy(authorId) }) { "본인에게 발급된 사진 키가 아닙니다." }
            require(photoKeys.all { it.confirmed }) { "업로드 확인을 마치지 않은 사진 키입니다." }
            return FeedItem(
                id = null,
                catalogId = catalogId,
                authorId = authorId,
                body = trimmed,
                photoKeys = photoKeys,
                likeCount = 0,
                hidden = false,
                deletedAt = null,
                createdAt = null,
            )
        }

        /** 저장소에서 복원. 불변식은 생성 시점에 이미 통과했으므로 다시 검사하지 않는다. */
        fun restore(
            id: Long,
            catalogId: Long,
            authorId: Long,
            body: String,
            photoKeys: List<PhotoObjectKey>,
            likeCount: Int,
            hidden: Boolean,
            deletedAt: Instant?,
            createdAt: Instant,
        ): FeedItem = FeedItem(
            id = id,
            catalogId = catalogId,
            authorId = authorId,
            body = body,
            photoKeys = photoKeys,
            likeCount = likeCount,
            hidden = hidden,
            deletedAt = deletedAt,
            createdAt = createdAt,
        )
    }
}
