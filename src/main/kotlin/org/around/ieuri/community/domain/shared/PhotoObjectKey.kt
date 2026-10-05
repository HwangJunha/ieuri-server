package org.around.ieuri.community.domain.shared

import java.util.UUID

/**
 * 사진 오브젝트 키. 형태는 서버가 정하며 `{상태}/community/{memberId}/{uuid}`다.
 * - 소유권은 memberId 구간 문자열만으로 판정한다(저장소를 묻지 않는다).
 * - `pending/`은 발급 직후 클라이언트가 PUT하는 자리, 검증을 통과하면 `images/`로 옮겨진다.
 * - 글에 붙일 수 있는 것은 `images/` 키뿐이다.
 */
data class PhotoObjectKey(
    val value: String,
    val memberId: Long,
    val confirmed: Boolean,
) {
    companion object {
        const val PENDING_PREFIX = "pending"
        const val CONFIRMED_PREFIX = "images"

        /** 도메인 구간. 한 버킷을 여러 도메인이 나눠 쓴다. */
        const val DOMAIN_SEGMENT = "community"

        /** 발급 시점의 새 임시(`pending/`) 키. */
        fun issue(memberId: Long, uuid: UUID): PhotoObjectKey {
            require(memberId > 0) { "memberId는 양수여야 합니다." }
            return PhotoObjectKey(
                value = "$PENDING_PREFIX/$DOMAIN_SEGMENT/$memberId/$uuid",
                memberId = memberId,
                confirmed = false,
            )
        }

        /**
         * 클라이언트가 되돌려준 키 문자열을 파싱한다. 형식이 어긋나면 예외가 아니라 null.
         * - 호출자는 null을 소유권 실패와 같은 코드로 응답해야 한다.
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
            if (runCatching { UUID.fromString(name) }.isFailure) return null
            return PhotoObjectKey(value = raw, memberId = memberId, confirmed = confirmed)
        }
    }

    /** 이 키가 [callerId]에게 발급된 것인지. 포트 호출 없이 문자열만으로 판정한다. */
    fun ownedBy(callerId: Long): Boolean = memberId == callerId

    /** 검증을 통과한 키의 확정 위치. `pending/` 구간만 바뀌고 나머지는 그대로다. */
    fun toConfirmed(): PhotoObjectKey = if (confirmed) {
        this
    } else {
        PhotoObjectKey(
            value = value.replaceFirst("$PENDING_PREFIX/", "$CONFIRMED_PREFIX/"),
            memberId = memberId,
            confirmed = true,
        )
    }
}
