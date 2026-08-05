package org.around.ieuri.community.domain.vo

import java.util.UUID

/**
 * 사진 오브젝트 키. 서버가 형태를 정하고 클라이언트는 지정할 수 없다.
 *
 * 키에 memberId 구간을 넣는 것이 소유권 판정의 근거다 — 저장소를 왕복하지 않고 문자열만으로
 * "이 키가 호출자에게 발급된 것인가"를 답할 수 있다. 저장소를 먼저 물으면 타인의 키가
 * 존재하는지를 응답 시간으로 알아낼 수 있다.
 *
 * 두 단계 접두어를 쓴다. `pending/`은 발급 직후 클라이언트가 PUT하는 자리이고, 검증을 통과한
 * 오브젝트만 `images/`로 옮겨진다. 글에 붙일 수 있는 것은 `images/` 키뿐이다.
 */
data class PhotoObjectKey private constructor(
    val value: String,
    val memberId: Long,
    val confirmed: Boolean,
) {
    companion object {
        const val PENDING_PREFIX = "pending"
        const val CONFIRMED_PREFIX = "images"

        /** 발급 시점의 새 임시 키. [uuid]는 호출자가 주입해 테스트에서 고정할 수 있게 한다. */
        fun issue(memberId: Long, uuid: UUID): PhotoObjectKey {
            require(memberId > 0) { "memberId는 양수여야 합니다." }
            return PhotoObjectKey(
                value = "$PENDING_PREFIX/$memberId/$uuid",
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
            if (segments.size != 3) return null
            val (prefix, memberSegment, name) = segments
            val confirmed = when (prefix) {
                CONFIRMED_PREFIX -> true
                PENDING_PREFIX -> false
                else -> return null
            }
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
            copy(
                value = value.replaceFirst("$PENDING_PREFIX/", "$CONFIRMED_PREFIX/"),
                confirmed = true,
            )
        }
}
