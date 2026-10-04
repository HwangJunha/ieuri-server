package org.around.ieuri.community.application.report.required

import org.around.ieuri.community.domain.report.Report
import org.around.ieuri.community.domain.report.ReportTargetType

interface ReportRepository {
    /** 새로 기록했으면 true, 같은 대상을 이미 신고했으면 false. */
    fun add(report: Report): Boolean

    /** 대상을 신고한 서로 다른 신고자 수. */
    fun countDistinctReporters(targetType: ReportTargetType, targetId: Long): Int
}

/** 신고 대상(회원 제외)의 존재 확인과 신고 누적 숨김. 대상 애그리게이트를 복원하지 않는다. */
interface ReportTargetRepository {
    /** 삭제되지 않은 대상이 있는지. */
    fun exists(targetType: ReportTargetType, targetId: Long): Boolean

    /**
     * 보이는 대상을 숨긴다. 이번 호출로 숨겨졌으면 true.
     * - 숨겨진 댓글·답변은 수에서 빠진다: 같은 트랜잭션에서 글의 댓글 수·질문의 답변 수를 내린다.
     */
    fun hide(targetType: ReportTargetType, targetId: Long): Boolean
}
