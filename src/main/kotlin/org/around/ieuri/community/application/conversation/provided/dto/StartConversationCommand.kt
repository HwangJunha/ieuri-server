package org.around.ieuri.community.application.conversation.provided.dto

/** 대화 시작. 상대를 회원으로 직접 지목한다. */
data class StartConversationCommand(val counterpartMemberId: Long) {
    init {
        require(counterpartMemberId > 0) { "counterpartMemberId는 양수여야 합니다." }
    }
}
