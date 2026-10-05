package org.around.ieuri.community.domain.post

import org.around.ieuri.community.domain.shared.CommunityUserException

class PostBodyRequiredException(message: String = "본문을 입력해 주세요.") : CommunityUserException("POST_BODY_REQUIRED", message)
