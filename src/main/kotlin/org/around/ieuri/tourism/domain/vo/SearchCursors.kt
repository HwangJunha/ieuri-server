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

/** 거리순 커서(진행 중 축제). 거리 오름차순이므로 다음 페이지는 이 값보다 **먼** 행이다. */
data class DistanceCursor(val distanceMeters: Double, val spotId: Long) {
    fun encode(): String = encodeCursor(PREFIX, distanceMeters.toString(), spotId)

    companion object {
        private const val PREFIX = "d"

        fun decode(cursor: String): DistanceCursor {
            val (key, spotId) = decodeCursor(PREFIX, cursor, "거리순")
            return DistanceCursor(distanceMeters = key.toDouble(), spotId = spotId)
        }
    }
}

/** 좋아요 수 커서(인기 spot). 내림차순이므로 다음 페이지는 이 값보다 **적은** 행이다. */
data class LikeCountCursor(val likeCount: Long, val spotId: Long) {
    fun encode(): String = encodeCursor(PREFIX, likeCount.toString(), spotId)

    companion object {
        private const val PREFIX = "l"

        fun decode(cursor: String): LikeCountCursor {
            val (key, spotId) = decodeCursor(PREFIX, cursor, "인기순")
            return LikeCountCursor(likeCount = key.toLong(), spotId = spotId)
        }
    }
}

private fun encodeCursor(prefix: String, sortKey: String, spotId: Long): String = Base64.getUrlEncoder().withoutPadding()
    .encodeToString("$prefix:$sortKey|$spotId".toByteArray(StandardCharsets.UTF_8))

/**
 * 커서를 (정렬 키 문자열, spotId)로 되돌린다. 조작·구버전·다른 종류의 커서는 모두
 * [IllegalArgumentException]이다 — 조용히 첫 페이지로 되돌리면 클라이언트가 무한 루프에 빠진다.
 */
private fun decodeCursor(prefix: String, cursor: String, label: String): Pair<String, Long> = try {
    val decoded = String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8)
    require(decoded.startsWith("$prefix:")) { "$label 커서가 아닙니다." }
    val (key, spotId) = decoded.removePrefix("$prefix:").split("|", limit = 2)
    key to spotId.toLong()
} catch (e: RuntimeException) {
    throw IllegalArgumentException("유효하지 않은 커서입니다.", e)
}

/**
 * 관계 목록 커서(SPEC-0030 확장). 등록 시각 내림차순이므로 다음 페이지는 이 값보다 **이전**이다.
 *
 * **겹침 수를 정렬 키로 쓸 수 없다** — 조회할 때마다 재계산되는 값이라 페이지를 넘기는 사이
 * 순위가 바뀌면 항목이 중복되거나 누락된다. 그래서 불변인 등록 시각을 쓴다.
 */
data class RelationCursor(val createdAt: java.time.Instant, val counterpartId: Long) {
    fun encode(): String = encodeCursor(PREFIX, createdAt.toString(), counterpartId)

    companion object {
        private const val PREFIX = "r"

        fun decode(cursor: String): RelationCursor {
            val (key, id) = decodeCursor(PREFIX, cursor, "관계 목록")
            return RelationCursor(createdAt = java.time.Instant.parse(key), counterpartId = id)
        }
    }
}
