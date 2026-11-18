package org.around.ieuri.community.domain.post

import org.around.ieuri.community.domain.shared.CommunityUserException

class PostTitleRequiredException(message: String = "제목을 입력해 주세요.") : CommunityUserException("POST_TITLE_REQUIRED", message)
