package org.around.ieuri.community.application.shared.exception

import org.around.ieuri.community.domain.shared.CommunityUserException

/** 카탈로그 자체가 없다. 어떤 언어로 물어도 없는 id다. */
class SpotNotFoundException(message: String = "해당 장소를 찾을 수 없습니다.") : CommunityUserException("SPOT_NOT_FOUND", message)
