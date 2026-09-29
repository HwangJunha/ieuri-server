package org.around.ieuri.auth.domain.vo

import java.util.UUID

/**
 * 프로필 이미지 오브젝트 키 `{상태}/profile/{memberId}/{uuid}`. 서버가 형태를 정한다.
 * - 커뮤니티 사진과 도메인 구간이 달라 서로의 키를 붙이면 파싱에서 걸린다.
 * - 소유권은 memberId 구간으로 저장소 왕복 없이 문자열만으로 판정한다.
 */
data class ProfileImageObjectKey(
    val value: String,
    val memberId: Long,
    val confirmed: Boolean,
) {
    /** 이 키가 [callerId]에게 발급된 것인지. 포트 호출 없이 문자열만으로 판정한다. */
    fun ownedBy(callerId: Long): Boolean = memberId == callerId

    /** 검증을 통과한 키의 확정 위치. `pending/` 구간만 바뀌고 나머지는 그대로다. */
    fun toConfirmed(): ProfileImageObjectKey = if (confirmed) {
        this
    } else {
        // 접두어(value)와 confirmed는 함께 움직여야 하는 값이라 한 자리에서 전부 지정한다.
        ProfileImageObjectKey(
            value = value.replaceFirst("$PENDING_PREFIX/", "$CONFIRMED_PREFIX/"),
            memberId = memberId,
            confirmed = true,
        )
    }

    companion object {
        const val PENDING_PREFIX = "pending"
        const val CONFIRMED_PREFIX = "images"

        /** 도메인 구간. 커뮤니티 사진과 경로가 섞이지 않게 한다. */
        const val DOMAIN_SEGMENT = "profile"

        /** 발급 시점의 새 임시 키. [uuid]는 호출자가 주입해 테스트에서 고정할 수 있게 한다. */
        fun issue(memberId: Long, uuid: UUID): ProfileImageObjectKey {
            require(memberId > 0) { "memberId는 양수여야 합니다." }
            return ProfileImageObjectKey(
                value = "$PENDING_PREFIX/$DOMAIN_SEGMENT/$memberId/$uuid",
                memberId = memberId,
                confirmed = false,
            )
        }

        /** 클라이언트가 되돌려준 키를 파싱한다. 형식이 어긋나면 예외가 아니라 null이며, 호출자는 소유권 실패와 같은 코드로 응답한다. */
        fun parse(raw: String): ProfileImageObjectKey? {
            val segments = raw.split('/')
            if (segments.size != 4) return null
            val (prefix, domain, memberSegment, name) = segments
            val confirmed = when (prefix) {
                CONFIRMED_PREFIX -> true
                PENDING_PREFIX -> false
                else -> return null
            }
            // 커뮤니티 키를 프로필에 붙이지 못하게 한다.
            if (domain != DOMAIN_SEGMENT) return null
            val memberId = memberSegment.toLongOrNull()?.takeIf { it > 0 } ?: return null
            if (runCatching { UUID.fromString(name) }.isFailure) return null
            return ProfileImageObjectKey(value = raw, memberId = memberId, confirmed = confirmed)
        }
    }
}
