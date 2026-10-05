package org.around.ieuri.community.domain.block

import org.around.ieuri.community.domain.shared.CommunityUserException

class CannotBlockSelfException(message: String = "자기 자신은 차단할 수 없습니다.") : CommunityUserException("CANNOT_BLOCK_SELF", message)
