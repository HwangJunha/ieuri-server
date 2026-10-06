package org.around.ieuri.tourism.application.reaction.provided.exception

import org.around.ieuri.tourism.domain.shared.ReactionUserException

/**
 * 카탈로그는 있으나 활성 언어에 원본이 없다(SPEC-0029 R-12). 뮤테이션 경로의 userError다.
 * - [SpotNotFoundException]과 구분한다 — 언어를 바꾸면 볼 수 있는 장소다(AC-12.3).
 */
class SpotNotFoundInLocaleException(message: String = "이 언어에서는 볼 수 없는 장소입니다.") : ReactionUserException("SPOT_NOT_FOUND_IN_LOCALE", message)
