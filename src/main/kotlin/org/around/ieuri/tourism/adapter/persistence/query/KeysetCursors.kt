package org.around.ieuri.tourism.adapter.persistence.query

import org.around.ieuri.common.pagination.CursorToken
import org.around.ieuri.tourism.application.shared.exception.InvalidCursorException
import java.time.Instant

// keyset 커서 — 정렬 키를 담는다. 정렬을 아는 쿼리 어댑터만 만들고 읽는다(SPEC-0039 D-96).
// 형식은 클라이언트가 들고 있는 값이라 바꾸지 않는다. 읽지 못할 때의 오류 응답도 dev와 같게 둔다 — 거리순·인기순은 업무 예외가 아니다.

/** 진행 중 축제 — 거리 오름차순이므로 다음 페이지는 이 값보다 **먼** 행이다. */
internal data class DistanceCursor(val distanceMeters: Double, val spotId: Long) {
    fun encode(): String = encodeKeyset(PREFIX, distanceMeters.toString(), spotId)

    companion object {
        private const val PREFIX = "d"

        fun decode(cursor: String): DistanceCursor {
            val (key, spotId) = decodeSpotKeyset(PREFIX, cursor, "거리순")
            return DistanceCursor(key.toDouble(), spotId)
        }
    }
}

/** 인기 spot — 좋아요 내림차순이므로 다음 페이지는 이 값보다 **적은** 행이다. */
internal data class LikeCountCursor(val likeCount: Long, val spotId: Long) {
    fun encode(): String = encodeKeyset(PREFIX, likeCount.toString(), spotId)

    companion object {
        private const val PREFIX = "l"

        fun decode(cursor: String): LikeCountCursor {
            val (key, spotId) = decodeSpotKeyset(PREFIX, cursor, "인기순")
            return LikeCountCursor(key.toLong(), spotId)
        }
    }
}

/** 지도 관계 — 등록 시각 내림차순이므로 다음 페이지는 이 값보다 **이전**이다. */
internal data class RelationCursor(val createdAt: Instant, val counterpartId: Long) {
    fun encode(): String = encodeKeyset(PREFIX, createdAt.toString(), counterpartId)

    companion object {
        private const val PREFIX = "r"

        fun decode(cursor: String): RelationCursor = decodeKeyset(PREFIX, cursor) { key, id -> RelationCursor(Instant.parse(key), id) }
    }
}

/**
 * 좋아요 목록 — 정렬 키는 `(updated_at, catalog_id)`다(SPEC-0019 R-10). 시각은 나노초까지 보존한다.
 * - `v2` 접두사로 `spot.id` 시절의 구 커서를 거부한다 — 형식이 같아 파싱은 성공하기 때문이다.
 */
internal data class LikedSpotCursor(val updatedAt: Instant, val catalogId: Long) {
    fun encode(): String = CursorToken.wrap("$PREFIX|$updatedAt|$catalogId")

    companion object {
        private const val PREFIX = "v2"

        fun decode(cursor: String): LikedSpotCursor = readOrReject {
            val parts = CursorToken.unwrap(cursor).split("|")
            require(parts.size == 3 && parts[0] == PREFIX) { "구버전 커서입니다." }
            LikedSpotCursor(updatedAt = Instant.parse(parts[1]), catalogId = parts[2].toLong())
        }
    }
}

/**
 * 거리순·인기순 커서를 (정렬 키 문자열, spotId)로 되돌린다. 읽지 못하면 [IllegalArgumentException]이다 — INVALID_CURSOR가 아니다(dev 응답 유지).
 * - 정렬 키의 숫자 변환은 호출부에서 한다. 이 try 밖이라 다른 예외가 올라가는 것까지 dev와 같다.
 */
private fun decodeSpotKeyset(prefix: String, cursor: String, label: String): Pair<String, Long> = try {
    val decoded = CursorToken.unwrap(cursor)
    require(decoded.startsWith("$prefix:")) { "$label 커서가 아닙니다." }
    val (key, spotId) = decoded.removePrefix("$prefix:").split("|", limit = 2)
    key to spotId.toLong()
} catch (e: RuntimeException) {
    throw IllegalArgumentException("유효하지 않은 커서입니다.", e)
}

/** `접두사:정렬키|id` 형식. 접두사로 종류를 구분해 다른 목록의 커서를 거부한다. */
private fun encodeKeyset(prefix: String, sortKey: String, id: Long): String = CursorToken.wrap("$prefix:$sortKey|$id")

private fun <T> decodeKeyset(prefix: String, cursor: String, build: (String, Long) -> T): T = readOrReject {
    val decoded = CursorToken.unwrap(cursor)
    require(decoded.startsWith("$prefix:")) { "다른 목록의 커서입니다." }
    val (key, id) = decoded.removePrefix("$prefix:").split("|", limit = 2)
    build(key, id.toLong())
}

/** 조작·구버전·다른 종류를 모두 INVALID_CURSOR로 — 조용히 첫 페이지로 되돌리면 클라이언트가 무한 루프에 빠진다. */
private fun <T> readOrReject(read: () -> T): T = try {
    read()
} catch (e: RuntimeException) {
    throw InvalidCursorException(cause = e)
}
