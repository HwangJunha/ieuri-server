package org.around.ieuri.tourism.application.catalog.provided.dto

import org.around.ieuri.tourism.domain.catalog.ReactionType

/** 좋아요/싫어요 기록 입력. 언어는 서버가 요청 언어(Accept-Language)에서 정한다. */
data class ReactToSpotCommand(
    /** 대상 장소(카탈로그 id). 어느 언어에서 눌러도 같은 기록에 모인다. */
    val catalogId: Long,
    val reaction: ReactionType,
) {
    init {
        require(catalogId > 0) { "catalogId는 양수여야 합니다." }
    }
}
