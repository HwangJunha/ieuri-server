package org.around.ieuri.community.application.conversation.provided.exception

import org.around.ieuri.community.domain.shared.CommunityUserException

class BlockedException(message: String = "차단된 상대와는 대화할 수 없습니다.") : CommunityUserException("BLOCKED", message)
