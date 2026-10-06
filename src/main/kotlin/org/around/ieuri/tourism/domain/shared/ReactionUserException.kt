package org.around.ieuri.tourism.domain.shared

/** reactToSpot의 사용자 노출 실패. code는 머신 판독용 안정 식별자. 인증 실패는 여기에 속하지 않는다. */
abstract class ReactionUserException(val code: String, message: String) : RuntimeException(message)
