package org.around.ieuri.community.domain

import org.around.ieuri.community.domain.aggregate.Report
import org.around.ieuri.community.domain.vo.ReportReason
import org.around.ieuri.community.domain.vo.ReportTargetType
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 신고 대상·사유의 분기 규칙.
 * - 사용자 신고는 자동 조치가 없다(R22).
 */
class ReportTargetTest {

    @Test
    fun `글과 댓글은 자동 숨김 대상이다`() {
        assertTrue(ReportTargetType.POST.autoHideable)
        assertTrue(ReportTargetType.COMMENT.autoHideable)
    }

    /** AC12-4 — DM 메시지는 신고 대상 종류에 없다(POL-049, MessageReport가 맡는다). */
    @Test
    fun `DM 메시지는 이 열거형의 대상이 아니다`() {
        assertFalse(ReportTargetType.entries.any { it.name == "MESSAGE" })
    }

    @Test
    fun `사용자는 자동 숨김 대상이 아니다`() {
        assertFalse(ReportTargetType.MEMBER.autoHideable)
    }

    @Test
    fun `OTHER만 상세 사유가 필수다`() {
        assertTrue(ReportReason.OTHER.requiresDetail)
        assertFalse(ReportReason.SPAM.requiresDetail)
        assertFalse(ReportReason.ABUSE.requiresDetail)
        assertFalse(ReportReason.INAPPROPRIATE_CONTENT.requiresDetail)
    }

    @Test
    fun `자동 숨김 임계값은 서로 다른 신고자 5명이다`() {
        assertFalse(Report.reachesAutoHideThreshold(ReportTargetType.POST, 4))
        assertTrue(Report.reachesAutoHideThreshold(ReportTargetType.POST, 5))
        // 임계값을 넘어도 여전히 참이다.
        assertTrue(Report.reachesAutoHideThreshold(ReportTargetType.POST, 6))
    }

    @Test
    fun `사용자는 몇 명이 신고해도 임계값에 도달하지 않는다`() {
        assertFalse(Report.reachesAutoHideThreshold(ReportTargetType.MEMBER, 5))
        assertFalse(Report.reachesAutoHideThreshold(ReportTargetType.MEMBER, 100))
    }
}
