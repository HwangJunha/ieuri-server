package org.around.ieuri.community.domain.post

import org.around.ieuri.community.domain.shared.CommunityUserException

class PostTitleLengthInvalidException(message: String = "제목은 50자를 넘을 수 없습니다.") : CommunityUserException("POST_TITLE_LENGTH_INVALID", message)
