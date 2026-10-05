package org.around.ieuri.community.domain.report

import org.around.ieuri.community.domain.shared.ReportDetailRequiredException
import org.around.ieuri.community.domain.shared.ReportReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReportTest {

    @Test
    fun `상세 사유는 트리밍하고 비면 없는 것으로 본다`() {
        assertEquals("욕설", Report.file(7, ReportTargetType.POST, 1, ReportReason.ABUSE, "  욕설 ").detail)
        assertNull(Report.file(7, ReportTargetType.POST, 1, ReportReason.SPAM, "   ").detail)
    }

    @Test
    fun `기타 사유는 상세 사유가 필수다`() {
        assertFailsWith<ReportDetailRequiredException> { Report.file(7, ReportTargetType.COMMENT, 1, ReportReason.OTHER, " ") }
    }

    @Test
    fun `서로 다른 신고자 5명부터 자동 숨김이고 회원 신고는 숨기지 않는다`() {
        assertFalse(Report.reachesAutoHideThreshold(ReportTargetType.POST, Report.AUTO_HIDE_THRESHOLD - 1))
        assertTrue(Report.reachesAutoHideThreshold(ReportTargetType.POST, Report.AUTO_HIDE_THRESHOLD))
        assertTrue(Report.reachesAutoHideThreshold(ReportTargetType.SPOT_ANSWER_REPLY, Report.AUTO_HIDE_THRESHOLD))
        assertFalse(Report.reachesAutoHideThreshold(ReportTargetType.MEMBER, 100))
    }
}
