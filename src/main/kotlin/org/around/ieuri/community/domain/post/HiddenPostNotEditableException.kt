package org.around.ieuri.community.domain.post

import org.around.ieuri.community.domain.shared.CommunityUserException

class HiddenPostNotEditableException(message: String = "숨겨진 글은 수정할 수 없습니다.") : CommunityUserException("POST_NOT_EDITABLE", message)
