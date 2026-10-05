package org.around.ieuri.community.application.post.provided.exception

import org.around.ieuri.community.domain.shared.CommunityUserException

class PostNotFoundException(message: String = "해당 글을 찾을 수 없습니다.") : CommunityUserException("POST_NOT_FOUND", message)
