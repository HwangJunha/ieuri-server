package org.around.ieuri.tourism.application.catalog.provided.exception

import org.around.ieuri.tourism.domain.shared.ReactionUserException

/** 카탈로그 자체가 없다. 어떤 언어로 물어도 없는 id다. */
class SpotNotFoundException(message: String = "해당 관광지를 찾을 수 없습니다.") : ReactionUserException("SPOT_NOT_FOUND", message)
