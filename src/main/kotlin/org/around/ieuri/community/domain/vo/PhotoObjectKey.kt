package org.around.ieuri.community.domain.vo

import java.util.UUID

/**
 * 사진 오브젝트 키. 서버가 형태를 정하고 클라이언트는 지정할 수 없다.
 *
 * 키에 memberId 구간을 넣는 것이 소유권 판정의 근거다 — 저장소를 왕복하지 않고 문자열만으로
 * "이 키가 호출자에게 발급된 것인가"를 답할 수 있다. 저장소를 먼저 물으면 타인의 키가
 * 존재하는지를 응답 시간으로 알아낼 수 있다.
 *
 * 형태는 `{상태}/community/{memberId}/{uuid}`다.
 *
 * 두 단계 접두어를 쓴다. `pending/`은 발급 직후 클라이언트가 PUT하는 자리이고, 검증을 통과한
 * 오브젝트만 `images/`로 옮겨진다. 글에 붙일 수 있는 것은 `images/` 키뿐이다.
 *
 * `community` 구간은 **버킷을 도메인끼리 나눠 쓰기 위한 것**이다. 나중에 다른 도메인이 같은
 * 버킷을 쓰더라도 경로가 섞이지 않고, lifecycle rule도 도메인별로 건다.
 *
 * **글 id가 아니라 회원 id로 나눈다.** 발급 시점에는 글이 아직 없어(글 생성은 업로드 확인
 * 뒤다) 글 id를 알 수 없고, 회원 id는 분포가 넓어 오브젝트 스토리지의 파티션 분산에도
 * 유리하다. 글 단위 관리는 community_post_photo.post_id가 맡는다.
 */
data class PhotoObjectKey private constructor(
    val value: String,
    val memberId: Long,
    val confirmed: Boolean,
) {
    companion object {
        const val PENDING_PREFIX = "pending"
        const val CONFIRMED_PREFIX = "images"

        /** 도메인 구간. 한 버킷을 여러 도메인이 나눠 쓸 때 경로가 섞이지 않게 한다. */
        const val DOMAIN_SEGMENT = "community"

        /** 발급 시점의 새 임시 키. [uuid]는 호출자가 주입해 테스트에서 고정할 수 있게 한다. */
        fun issue(memberId: Long, uuid: UUID): PhotoObjectKey {
            require(memberId > 0) { "memberId는 양수여야 합니다." }
            return PhotoObjectKey(
                value = "$PENDING_PREFIX/$DOMAIN_SEGMENT/$memberId/$uuid",
                memberId = memberId,
                confirmed = false,
            )
        }

        /**
         * 클라이언트가 되돌려준 키 문자열을 파싱한다. 형식이 어긋나거나 알 수 없는 접두어면 null —
         * 예외가 아니라 부재로 다룬다. 호출자는 이를 소유권 실패와 같은 코드로 응답해야 한다.
         */
        fun parse(raw: String): PhotoObjectKey? {
            val segments = raw.split('/')
            if (segments.size != 4) return null
            val (prefix, domain, memberSegment, name) = segments
            val confirmed = when (prefix) {
                CONFIRMED_PREFIX -> true
                PENDING_PREFIX -> false
                else -> return null
            }
            // 다른 도메인의 키를 커뮤니티 글에 붙이지 못하게 한다.
            if (domain != DOMAIN_SEGMENT) return null
            val memberId = memberSegment.toLongOrNull()?.takeIf { it > 0 } ?: return null
            // uuid 형태까지 확인한다. 임의 문자열을 붙여 넣은 키를 여기서 걸러 내면
            // 저장소 조회로 넘어가지 않는다.
            if (runCatching { UUID.fromString(name) }.isFailure) return null
            return PhotoObjectKey(value = raw, memberId = memberId, confirmed = confirmed)
        }
    }

    /** 이 키가 [callerId]에게 발급된 것인지. 포트 호출 없이 문자열만으로 판정한다. */
    fun ownedBy(callerId: Long): Boolean = memberId == callerId

    /** 검증을 통과한 키의 확정 위치. `pending/` 구간만 바뀌고 나머지는 그대로다. */
    fun toConfirmed(): PhotoObjectKey =
        if (confirmed) {
            this
        } else {
            // copy()가 아니라 생성자를 부른다 — 접두어와 confirmed는 함께 움직여야 하는 값이라
            // 한 자리에서 전부 지정하는 편이 어긋날 여지가 없다.
            PhotoObjectKey(
                value = value.replaceFirst("$PENDING_PREFIX/", "$CONFIRMED_PREFIX/"),
                memberId = memberId,
                confirmed = true,
            )
        }
}
