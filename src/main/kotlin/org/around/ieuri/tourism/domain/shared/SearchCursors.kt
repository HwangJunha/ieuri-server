package org.around.ieuri.tourism.domain.shared

import java.nio.charset.StandardCharsets
import java.util.Base64

/*
 * 커서 커넥션의 keyset 커서. 모두 (정렬 키, id) 쌍의 불투명 문자열이다.
 * - 접두사로 종류를 구분하며, 다른 종류의 커서는 거부된다.
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

/** 관계 목록 커서(SPEC-0030). 등록 시각 내림차순이므로 다음 페이지는 이 값보다 **이전**이다. */
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
