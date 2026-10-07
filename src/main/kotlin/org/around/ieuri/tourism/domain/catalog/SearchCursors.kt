package org.around.ieuri.tourism.domain.catalog

import org.around.ieuri.tourism.domain.shared.CursorCodec

/** 거리순 커서(진행 중 축제). 거리 오름차순이므로 다음 페이지는 이 값보다 **먼** 행이다. */
data class DistanceCursor(val distanceMeters: Double, val spotId: Long) {
    fun encode(): String = CursorCodec.encode(PREFIX, distanceMeters.toString(), spotId)

    companion object {
        private const val PREFIX = "d"

        fun decode(cursor: String): DistanceCursor {
            val (key, spotId) = CursorCodec.decode(PREFIX, cursor, "거리순")
            return DistanceCursor(distanceMeters = key.toDouble(), spotId = spotId)
        }
    }
}

/** 좋아요 수 커서(인기 spot). 내림차순이므로 다음 페이지는 이 값보다 **적은** 행이다. */
data class LikeCountCursor(val likeCount: Long, val spotId: Long) {
    fun encode(): String = CursorCodec.encode(PREFIX, likeCount.toString(), spotId)

    companion object {
        private const val PREFIX = "l"

        fun decode(cursor: String): LikeCountCursor {
            val (key, spotId) = CursorCodec.decode(PREFIX, cursor, "인기순")
            return LikeCountCursor(likeCount = key.toLong(), spotId = spotId)
        }
    }
}
