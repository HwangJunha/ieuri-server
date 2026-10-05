package org.around.ieuri.community.application.conversation.provided

import org.around.ieuri.community.application.conversation.provided.dto.ExpiredMessages

/** 보관 기간이 지난 메시지 행 삭제 한 회차. 첨부 오브젝트는 지우지 않는다 — 커밋 뒤 호출자가 지운다. */
interface ExpiredMessageDeleteUseCase {
    fun deleteExpiredMessages(limit: Int): ExpiredMessages
}
