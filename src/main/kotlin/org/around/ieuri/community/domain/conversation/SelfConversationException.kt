package org.around.ieuri.community.domain.conversation

import org.around.ieuri.community.domain.shared.CommunityUserException

class SelfConversationException(message: String = "자기 자신과는 대화할 수 없습니다.") : CommunityUserException("SELF_CONVERSATION", message)
