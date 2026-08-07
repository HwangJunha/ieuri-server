package org.around.ieuri.auth.domain.vo

import java.util.UUID

/**
 * 프로필 이미지 오브젝트 키. 서버가 형태를 정하고 클라이언트는 지정할 수 없다.
 *
 * 형태는 `{상태}/profile/{memberId}/{uuid}`다. 커뮤니티 사진과 **도메인 구간이 다르다** —
 * 회원 프로필을 커뮤니티와 별개로 보관·삭제할 수 있어야 하고, lifecycle rule도 따로 건다.
 * 구간이 다르므로 커뮤니티 키를 프로필에 붙이거나 그 반대도 파싱 단계에서 걸린다.
 *
 * 키에 memberId 구간을 넣는 것이 소유권 판정의 근거다 — 저장소를 왕복하지 않고 문자열만으로
 * 판정한다. 저장소를 먼저 물으면 타인의 키 존재 여부를 응답 시간으로 알아낼 수 있다.
 *
 * 글과 달리 프로필 이미지는 1장이라 순서·다중 발급 개념이 없다.
 */
data class ProfileImageObjectKey private constructor(
    val value: String,
    val memberId: Long,
    val confirmed: Boolean,
) {
    /** 이 키가 [callerId]에게 발급된 것인지. 포트 호출 없이 문자열만으로 판정한다. */
    fun ownedBy(callerId: Long): Boolean = memberId == callerId

    /** 검증을 통과한 키의 확정 위치. `pending/` 구간만 바뀌고 나머지는 그대로다. */
    fun toConfirmed(): ProfileImageObjectKey =
        if (confirmed) {
            this
        } else {
            copy(
                value = value.replaceFirst("$PENDING_PREFIX/", "$CONFIRMED_PREFIX/"),
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

        /**
         * 클라이언트가 되돌려준 키 문자열을 파싱한다. 형식이 어긋나거나 알 수 없는 접두어면
         * null — 예외가 아니라 부재로 다룬다. 호출자는 이를 소유권 실패와 같은 코드로 응답한다.
         */
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
