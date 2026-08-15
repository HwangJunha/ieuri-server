package org.around.ieuri.tourism.domain.vo

import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * SPEC-0013 커서 커넥션 2종(진행 중 축제·인기)의 keyset 커서.
 *
 * **검색은 여기 없다.** BM25 관련도는 검색 엔진이 계산하는 값이라 "이 점수보다 뒤"를 조건절로
 * 표현할 수 없어(`pdb.score()`는 SELECT 전용) 오프셋 페이지네이션을 쓴다.
 *
 * 둘 다 **(정렬 키, spot id)** 쌍이다. 정렬 키만으로는 동점이 흔해 순서가 불안정하므로 id를
 * 최종 tie-breaker로 두어 전순서를 만든다 — 그래야 페이지를 넘길 때 항목이 중복·누락되지 않는다(R7).
 *
 * 접두사로 종류를 구분한다. 정렬 순서가 다른 커넥션에 커서를 잘못 넘기면 조용히 엉뚱한 위치로
 * 뛰는 대신 거부된다. 커뮤니티의 거리순 커서가 같은 방식을 쓴다.
 *
 * 커서는 불투명하다 — 감싸는 목적은 값을 숨기는 것이 아니라 클라이언트가 커서를 조립하지
 * 못하게 막는 것이다. 형식이 드러나면 정렬 키를 바꾸는 순간 파괴적 변경이 된다.
 */

/**
 * 거리순 커서(진행 중 축제). 거리 오름차순이므로 다음 페이지는 이 값보다 **먼** 행이다.
 *
 * **[hasOrigin]이 1순위 정렬 키다**(SPEC-0019) — 그 언어의 원문이 있는 장소를 먼저 보여주고,
 * 번역으로 채운 장소는 그것이 소진된 뒤에 나온다. 커서에 실리지 않으면 두 구간의 경계에서
 * 페이지가 어긋난다.
 *
 * 접두사가 `d2`인 것은 정렬 키가 바뀌었기 때문이다 — 구버전(`d`) 커서는 거부된다.
 */
data class DistanceCursor(val hasOrigin: Boolean, val distanceMeters: Double, val spotId: Long) {
    fun encode(): String = encodeCursor(PREFIX, hasOrigin, distanceMeters.toString(), spotId)

    companion object {
        private const val PREFIX = "d2"

        fun decode(cursor: String): DistanceCursor {
            val (hasOrigin, key, spotId) = decodeCursor(PREFIX, cursor, "거리순")
            return DistanceCursor(hasOrigin = hasOrigin, distanceMeters = key.toDouble(), spotId = spotId)
        }
    }
}

/**
 * 좋아요 수 커서(인기 spot). 내림차순이므로 다음 페이지는 이 값보다 **적은** 행이다.
 *
 * [hasOrigin]이 1순위다([DistanceCursor] 참조). 접두사 `l2`가 구버전을 거부한다.
 */
data class LikeCountCursor(val hasOrigin: Boolean, val likeCount: Long, val spotId: Long) {
    fun encode(): String = encodeCursor(PREFIX, hasOrigin, likeCount.toString(), spotId)

    companion object {
        private const val PREFIX = "l2"

        fun decode(cursor: String): LikeCountCursor {
            val (hasOrigin, key, spotId) = decodeCursor(PREFIX, cursor, "인기순")
            return LikeCountCursor(hasOrigin = hasOrigin, likeCount = key.toLong(), spotId = spotId)
        }
    }
}

private fun encodeCursor(prefix: String, hasOrigin: Boolean, sortKey: String, spotId: Long): String =
    Base64.getUrlEncoder().withoutPadding()
        .encodeToString("$prefix:$hasOrigin|$sortKey|$spotId".toByteArray(StandardCharsets.UTF_8))

/**
 * 커서를 (원문 보유, 정렬 키 문자열, spotId)로 되돌린다. 조작·구버전·다른 종류의 커서는 모두
 * [IllegalArgumentException]이다 — 조용히 첫 페이지로 되돌리면 클라이언트가 무한 루프에 빠진다.
 */
private fun decodeCursor(prefix: String, cursor: String, label: String): Triple<Boolean, String, Long> =
    try {
        val decoded = String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8)
        require(decoded.startsWith("$prefix:")) { "$label 커서가 아닙니다." }
        val (hasOrigin, key, spotId) = decoded.removePrefix("$prefix:").split("|", limit = 3)
        Triple(hasOrigin.toBooleanStrict(), key, spotId.toLong())
    } catch (e: RuntimeException) {
        throw IllegalArgumentException("유효하지 않은 커서입니다.", e)
    }
