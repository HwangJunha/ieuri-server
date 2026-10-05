package org.around.ieuri.community.domain.post

import org.around.ieuri.community.domain.shared.CommunityUserException

class TooManyTagsException(message: String = "태그는 최대 5개입니다.") : CommunityUserException("TOO_MANY_TAGS", message)
