package org.around.ieuri.community.application.exception

import org.around.ieuri.community.domain.shared.CommunityUserException

/** 카탈로그 자체가 없다. 어떤 언어로 물어도 없는 id다. */
class SpotNotFoundException(message: String = "해당 장소를 찾을 수 없습니다.") : CommunityUserException("SPOT_NOT_FOUND", message)

/**
 * 카탈로그는 있으나 활성 언어에 원본이 없다(SPEC-0029). [SpotNotFoundException]과 코드를 구분한다.
 * - 뮤테이션에서는 userErrors로, `spotQuestions` 조회에서는 최상위 errors[]로 나간다.
 */
class SpotNotFoundInLocaleException(message: String = "이 언어에서는 볼 수 없는 장소입니다.") : CommunityUserException("SPOT_NOT_FOUND_IN_LOCALE", message)

class UnsupportedContentTypeException(message: String = "지원하지 않는 이미지 형식입니다.") : CommunityUserException("UNSUPPORTED_CONTENT_TYPE", message)

class MemberNotFoundException(message: String = "해당 사용자를 찾을 수 없습니다.") : CommunityUserException("MEMBER_NOT_FOUND", message)

// ── DM(SPEC-0017·SPEC-0021) ──────────────────────────────────────────

/** 확정되지 않았거나 이미 다른 글에 실린 DM 사진 키. 두 경우를 같은 코드로 다룬다. */
class PhotoNotConfirmedException(message: String = "업로드가 확정되지 않은 사진입니다.") : CommunityUserException("PHOTO_NOT_CONFIRMED", message)
