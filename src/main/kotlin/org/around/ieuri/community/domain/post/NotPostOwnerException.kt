package org.around.ieuri.community.domain.post

import org.around.ieuri.community.domain.shared.CommunityUserException

class NotPostOwnerException(message: String = "본인의 글만 삭제할 수 있습니다.") : CommunityUserException("NOT_POST_OWNER", message)
