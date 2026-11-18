package org.around.ieuri.community.application.report.provided

import org.around.ieuri.community.domain.report.ReportTargetType

/** 신고 누적 자동 숨김. 신고가 커밋된 뒤 따로 돈다. */
interface ReportAutoHideUseCase {
    /** 서로 다른 신고자가 임계값에 닿았으면 숨긴다. 이미 숨겨졌으면 아무것도 하지 않는다. */
    fun applyAutoHide(targetType: ReportTargetType, targetId: Long)
}
