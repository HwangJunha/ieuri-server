package org.around.ieuri.community.domain.shared

/** 입력 형식·규칙 위반. 메시지가 곧 사용자에게 보이는 사유다. */
class InvalidInputException(message: String = "잘못된 입력입니다.") : CommunityUserException("INVALID_INPUT", message)
