package org.around.ieuri.community.domain.post

import org.around.ieuri.community.domain.shared.CommunityUserException

class PostBodyLengthInvalidException(message: String = "본문은 500자를 넘을 수 없습니다.") : CommunityUserException("POST_BODY_LENGTH_INVALID", message)
