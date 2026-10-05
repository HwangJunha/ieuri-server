package org.around.ieuri.community.application.shared.exception

import org.around.ieuri.community.domain.shared.CommunityUserException

/** 확정되지 않았거나 이미 다른 글에 실린 DM 사진 키. 두 경우를 같은 코드로 다룬다. */
class PhotoNotConfirmedException(message: String = "업로드가 확정되지 않은 사진입니다.") : CommunityUserException("PHOTO_NOT_CONFIRMED", message)
