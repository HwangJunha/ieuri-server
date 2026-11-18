package org.around.ieuri.community.application.shared.exception

import org.around.ieuri.community.domain.shared.CommunityUserException

/**
 * 카탈로그는 있으나 활성 언어에 원본이 없다(SPEC-0029). 카탈로그 자체가 없을 때(SPOT_NOT_FOUND)와 코드를 구분한다.
 * - 뮤테이션에서는 userErrors로, `spotQuestions` 조회에서는 최상위 errors[]로 나간다.
 */
class SpotNotFoundInLocaleException(message: String = "이 언어에서는 볼 수 없는 장소입니다.") : CommunityUserException("SPOT_NOT_FOUND_IN_LOCALE", message)
