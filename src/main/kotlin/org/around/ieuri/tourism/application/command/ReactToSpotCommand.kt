package org.around.ieuri.tourism.application.command

import org.around.ieuri.tourism.domain.vo.ReactionType

/**
 * 좋아요/싫어요 기록 입력. language는 인자가 아니라 서버가 요청 언어(Accept-Language)와 결합한다.
 * 형식 검증(빈 값)만 여기서, 인증·resolve 판정은 서비스에서 한다.
 */
data class ReactToSpotCommand(
    val spotId: Long,
    val reaction: ReactionType,
) {
    init {
        require(spotId > 0) { "spotId는 양수여야 합니다." }
    }
}
