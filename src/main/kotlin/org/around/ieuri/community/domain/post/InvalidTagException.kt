package org.around.ieuri.community.domain.post

import org.around.ieuri.community.domain.shared.CommunityUserException

class InvalidTagException(message: String = "태그는 공백 없이 30자 이내여야 합니다.") : CommunityUserException("INVALID_TAG", message)
