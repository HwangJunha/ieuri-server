package org.around.ieuri.community.application.conversation.provided

/** 보관 기간이 지난 메시지 삭제(POL-050). 배치가 회차당 상한을 두고 반복해 부른다. */
interface MessageRetentionUseCase {
    /** 한 회차를 지우고 삭제 건수를 돌려준다. 상한보다 적게 지웠으면 더 지울 것이 없다는 뜻이다. */
    fun purgeExpiredBatch(limit: Int): Int
}
