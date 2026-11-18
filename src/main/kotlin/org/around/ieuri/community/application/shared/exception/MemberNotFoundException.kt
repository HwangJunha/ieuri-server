package org.around.ieuri.community.application.shared.exception

import org.around.ieuri.community.domain.shared.CommunityUserException

class MemberNotFoundException(message: String = "해당 사용자를 찾을 수 없습니다.") : CommunityUserException("MEMBER_NOT_FOUND", message)
