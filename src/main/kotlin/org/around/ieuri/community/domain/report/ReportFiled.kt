package org.around.ieuri.community.domain.report

/** 자동 숨김될 수 있는 대상에 신고가 기록됐다. 커밋 뒤 신고자 수를 다시 세어 숨김 여부를 정한다. */
data class ReportFiled(val targetType: ReportTargetType, val targetId: Long)
