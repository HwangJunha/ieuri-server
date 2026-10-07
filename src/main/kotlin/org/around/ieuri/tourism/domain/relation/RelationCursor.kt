package org.around.ieuri.tourism.domain.relation

import org.around.ieuri.tourism.domain.shared.CursorCodec
import java.time.Instant

/** 관계 목록 커서(SPEC-0030). 등록 시각 내림차순이므로 다음 페이지는 이 값보다 **이전**이다. */
data class RelationCursor(val createdAt: Instant, val counterpartId: Long) {
    fun encode(): String = CursorCodec.encode(PREFIX, createdAt.toString(), counterpartId)

    companion object {
        private const val PREFIX = "r"

        fun decode(cursor: String): RelationCursor {
            val (key, id) = CursorCodec.decode(PREFIX, cursor, "관계 목록")
            return RelationCursor(createdAt = Instant.parse(key), counterpartId = id)
        }
    }
}
