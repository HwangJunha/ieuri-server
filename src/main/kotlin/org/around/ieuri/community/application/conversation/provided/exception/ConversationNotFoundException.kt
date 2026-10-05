package org.around.ieuri.community.application.conversation.provided.exception

import org.around.ieuri.community.domain.shared.CommunityUserException

class ConversationNotFoundException(message: String = "해당 대화를 찾을 수 없습니다.") : CommunityUserException("CONVERSATION_NOT_FOUND", message)
