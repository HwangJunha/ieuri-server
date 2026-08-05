package org.around.ieuri.community.domain

import org.around.ieuri.community.domain.aggregate.Block
import org.around.ieuri.community.domain.aggregate.Comment
import org.around.ieuri.community.domain.aggregate.Report
import org.around.ieuri.community.domain.vo.ReportReason
import org.around.ieuri.community.domain.vo.ReportTargetType
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 댓글 불변식(R13). */
class CommentTest {

    private val authorId = 3L

    private fun create(body: String = "댓글") =
        Comment.create(postId = 1L, authorId = authorId, body = body)

    @Test
    fun `본문 경계 - 1자와 500자는 허용되고 501자는 거부된다`() {
        assertEquals(1, create("가").body.length)
        assertEquals(500, create("가".repeat(500)).body.length)
        assertFailsWith<IllegalArgumentException> { create("가".repeat(501)) }
    }

    @Test
    fun `공백만 있는 본문은 거부된다`() {
        assertFailsWith<IllegalArgumentException> { create("   ") }
    }

    @Test
    fun `부모 댓글을 가리키는 필드가 존재하지 않는다 - 1단계 댓글임을 구조로 고정한다`() {
        val fieldNames = Comment::class.members.map { it.name }
        assertFalse(fieldNames.any { it.contains("parent", ignoreCase = true) })
    }

    @Test
    fun `타인은 댓글을 삭제할 수 없다`() {
        val comment = create()
        assertFalse(comment.deleteBy(authorId + 1, Instant.EPOCH))
        assertFalse(comment.deleted)
    }

    @Test
    fun `숨겨진 댓글은 작성자 본인에게만 보인다`() {
        val comment = create()
        comment.hide()
        assertTrue(comment.visibleTo(authorId))
        assertFalse(comment.visibleTo(authorId + 1))
    }
}

/** 차단 불변식(R15). */
class BlockTest {

    @Test
    fun `자기 자신은 차단할 수 없다`() {
        assertFailsWith<IllegalArgumentException> { Block.create(blockerId = 1L, blockedId = 1L) }
    }

    @Test
    fun `다른 사용자는 차단할 수 있다`() {
        val block = Block.create(blockerId = 1L, blockedId = 2L)
        assertEquals(1L, block.blockerId)
        assertEquals(2L, block.blockedId)
    }
}

/** 신고 불변식과 자동 숨김 임계값(R16, R20, R22). */
class ReportTest {

    private fun create(reason: ReportReason, detail: String?) = Report.create(
        reporterId = 1L,
        targetType = ReportTargetType.POST,
        targetId = 2L,
        reason = reason,
        detail = detail,
    )

    @Test
    fun `OTHER 사유는 상세 사유가 없으면 거부된다`() {
        assertFailsWith<IllegalArgumentException> { create(ReportReason.OTHER, null) }
        assertFailsWith<IllegalArgumentException> { create(ReportReason.OTHER, "   ") }
    }

    @Test
    fun `OTHER 외의 사유는 상세 사유가 없어도 된다`() {
        assertNull(create(ReportReason.SPAM, null).detail)
    }

    @Test
    fun `상세 사유의 앞뒤 공백은 제거된다`() {
        assertEquals("욕설", create(ReportReason.OTHER, "  욕설  ").detail)
    }

    @Test
    fun `글은 서로 다른 신고자 5명에서 숨김 임계값에 도달한다`() {
        assertFalse(Report.reachesAutoHideThreshold(ReportTargetType.POST, 4))
        assertTrue(Report.reachesAutoHideThreshold(ReportTargetType.POST, 5))
    }

    @Test
    fun `댓글도 같은 임계값을 쓴다`() {
        assertFalse(Report.reachesAutoHideThreshold(ReportTargetType.COMMENT, 4))
        assertTrue(Report.reachesAutoHideThreshold(ReportTargetType.COMMENT, 5))
    }

    @Test
    fun `사용자 신고는 임계값에 도달해도 자동 조치가 없다`() {
        assertFalse(Report.reachesAutoHideThreshold(ReportTargetType.MEMBER, 5))
        assertFalse(Report.reachesAutoHideThreshold(ReportTargetType.MEMBER, 100))
        assertFalse(ReportTargetType.MEMBER.autoHideable)
    }
}
