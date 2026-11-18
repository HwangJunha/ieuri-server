package org.around.ieuri.community.domain.conversation

import org.around.ieuri.community.domain.shared.CommunityUserException

class NotConversationParticipantException(message: String = "대화의 참여자가 아닙니다.") : CommunityUserException("NOT_CONVERSATION_PARTICIPANT", message)
