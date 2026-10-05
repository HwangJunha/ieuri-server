package org.around.ieuri.community.adapter.event.listener

import org.around.ieuri.community.application.report.provided.ReportAutoHideUseCase
import org.around.ieuri.community.domain.report.ReportFiled
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/** 신고가 커밋된 뒤 자동 숨김을 판정한다. 같은 요청 스레드에서 응답 전에 돈다. */
@Component
class ReportAutoHideListener(
    private val reportAutoHideUseCase: ReportAutoHideUseCase,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: ReportFiled) {
        reportAutoHideUseCase.applyAutoHide(event.targetType, event.targetId)
    }
}
