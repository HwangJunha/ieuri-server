package org.around.ieuri.community.domain.photo

import org.around.ieuri.community.domain.shared.CommunityUserException

class TooManyPhotosException(message: String = "사진은 최대 5장입니다.") : CommunityUserException("TOO_MANY_PHOTOS", message)
