package org.around.ieuri.community.domain

import org.around.ieuri.community.domain.aggregate.Conversation
import org.around.ieuri.community.domain.aggregate.Message
import org.around.ieuri.community.domain.aggregate.MessageReport
import org.around.ieuri.community.domain.shared.ReportReason
import org.around.ieuri.community.domain.vo.PhotoObjectKey
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 대화·메시지·DM 신고의 도메인 불변식(SPEC-0017 R9·R15·R16, SPEC-0021 R3·R4). */
class ConversationAndMessageTest {

    private val now = Instant.parse("2026-08-12T00:00:00Z")

    private fun confirmedKey(owner: Long) = requireNotNull(
        PhotoObjectKey.parse("images/community/$owner/${java.util.UUID.randomUUID()}"),
    )

    // ── Conversation ────────────────────────────────────────────────

    /** AC7-2 — 인자 순서와 무관하게 참여자 쌍이 정렬되어 저장된다. */
    @Test
    fun `참여자 쌍은 인자 순서와 무관하게 정렬되어 저장된다`() {
        val one = Conversation.create(memberId = 9L, counterpartId = 3L, now = now)
        val other = Conversation.create(memberId = 3L, counterpartId = 9L, now = now)

        assertEquals(3L, one.memberLow)
        assertEquals(9L, one.memberHigh)
        assertEquals(one.memberLow, other.memberLow)
        assertEquals(one.memberHigh, other.memberHigh)
    }

    /** AC7-4 — 자기 자신과의 대화는 만들지 않는다. */
    @Test
    fun `자기 자신과는 대화를 만들 수 없다`() {
        assertFailsWith<IllegalArgumentException> {
            Conversation.create(memberId = 3L, counterpartId = 3L, now = now)
        }
    }

    @Test
    fun `참여자 판정과 상대방 조회`() {
        val conversation = Conversation.create(3L, 9L, now = now)

        assertTrue(conversation.hasParticipant(3L))
        assertTrue(conversation.hasParticipant(9L))
        assertFalse(conversation.hasParticipant(99L))
        assertEquals(9L, conversation.counterpartOf(3L))
        assertEquals(3L, conversation.counterpartOf(9L))
    }

    @Test
    fun `참여자가 아닌 회원의 상대방은 조회할 수 없다`() {
        val conversation = Conversation.create(3L, 9L, now = now)

        assertFailsWith<IllegalArgumentException> { conversation.counterpartOf(99L) }
    }

    /** 빈 대화가 존재할 수 있다. */
    @Test
    fun `메시지가 오가면 최근 활동 시각이 갱신된다`() {
        val conversation = Conversation.create(3L, 9L, now = now)
        val later = now.plusSeconds(60)

        conversation.touch(later)

        assertEquals(later, conversation.lastMessageAt)
    }

    // ── Message ─────────────────────────────────────────────────────

    @Test
    fun `메시지가 생성되며 본문의 앞뒤 공백이 제거된다`() {
        val message = Message.create(conversationId = 1L, senderId = 3L, body = "  안녕  ")

        assertEquals("안녕", message.body)
        assertEquals(1L, message.conversationId)
        assertEquals(3L, message.senderId)
    }

    /** AC9-2·SPEC-0021 AC3.5 — 본문도 사진도 없으면 거부된다. */
    @Test
    fun `본문도 사진도 없으면 전송되지 않는다`() {
        assertFailsWith<IllegalArgumentException> {
            Message.create(conversationId = 1L, senderId = 3L, body = "   ")
        }
    }

    /** SPEC-0021 AC3.4 — 사진만 있는 메시지가 성립한다(본문은 빈 문자열). */
    @Test
    fun `본문 없이 사진만 보낼 수 있다`() {
        val key = confirmedKey(owner = 3L)

        val message = Message.create(1L, 3L, body = "  ", photoKey = key)

        assertEquals("", message.body)
        assertFalse(message.hasBody)
        assertEquals(key.value, message.photoKey?.value)
    }

    @Test
    fun `본문과 사진을 함께 보낼 수 있다`() {
        val key = confirmedKey(owner = 3L)

        val message = Message.create(1L, 3L, "이 사진 보세요", photoKey = key)

        assertEquals("이 사진 보세요", message.body)
        assertEquals(key.value, message.photoKey?.value)
    }

    /** SPEC-0021 AC4.1 — 복원은 빈 메시지를 거부하지 않는다. */
    @Test
    fun `본문과 사진이 모두 빈 과거 메시지도 복원된다`() {
        val restored = Message.restore(
            id = 500L,
            conversationId = 1L,
            senderId = 3L,
            body = "",
            createdAt = now,
        )

        assertEquals(500L, restored.id)
        assertEquals("", restored.body)
        assertFalse(restored.hasBody)
        assertEquals(null, restored.photoKey)
    }

    /** SPEC-0021 AC4.3 — 복원은 통과해도 새 빈 메시지 생성은 거부된다. */
    @Test
    fun `과거 빈 메시지를 허용해도 새 빈 메시지는 거부된다`() {
        Message.restore(id = 500L, conversationId = 1L, senderId = 3L, body = "", createdAt = now)

        assertFailsWith<IllegalArgumentException> {
            Message.create(1L, 3L, body = "")
        }
    }

    @Test
    fun `타인에게 발급된 사진 키로는 전송할 수 없다`() {
        assertFailsWith<IllegalArgumentException> {
            Message.create(1L, 3L, "안녕", photoKey = confirmedKey(owner = 999L))
        }
    }

    @Test
    fun `확정되지 않은 사진 키로는 전송할 수 없다`() {
        val pending = requireNotNull(
            PhotoObjectKey.parse("pending/community/3/${java.util.UUID.randomUUID()}"),
        )

        assertFailsWith<IllegalArgumentException> {
            Message.create(1L, 3L, "안녕", photoKey = pending)
        }
    }

    /** AC9-3 — **잘라서 저장하지 않는다.** */
    @Test
    fun `본문이 상한을 넘으면 잘리지 않고 거부된다`() {
        assertFailsWith<IllegalArgumentException> {
            Message.create(1L, 3L, body = "가".repeat(Message.BODY_MAX_LENGTH + 1))
        }
    }

    @Test
    fun `본문이 정확히 상한이면 전송된다`() {
        val body = "가".repeat(Message.BODY_MAX_LENGTH)

        assertEquals(body, Message.create(1L, 3L, body).body)
    }

    @Test
    fun `발신자 판정`() {
        val message = Message.create(1L, 3L, "안녕")

        assertTrue(message.sentBy(3L))
        assertFalse(message.sentBy(9L))
    }

    // ── 보관 기간(R16) ───────────────────────────────────────────────

    /** AC16-1 — 6개월이 지난 메시지는 삭제 대상이다. */
    @Test
    fun `6개월이 지난 메시지는 삭제 대상이다`() {
        val createdAt = now.minus(Message.RETENTION).minus(Duration.ofDays(1))

        assertTrue(Message.isExpired(createdAt, now))
    }

    /** AC16-2 — 경계는 생성 시각 기준이며, 아직 6개월이 되지 않았으면 남는다. */
    @Test
    fun `6개월이 되지 않은 메시지는 삭제 대상이 아니다`() {
        val createdAt = now.minus(Message.RETENTION).plus(Duration.ofDays(1))

        assertFalse(Message.isExpired(createdAt, now))
    }

    /** 경계값 자체는 아직 지나지 않은 것으로 본다. */
    @Test
    fun `정확히 6개월 경과한 메시지는 아직 삭제 대상이 아니다`() {
        val createdAt = now.minus(Message.RETENTION)

        assertFalse(Message.isExpired(createdAt, now))
    }

    /** AC16-3 — 보관 기간은 대화 활동이 아니라 메시지 자신의 생성 시각 기준이다. */
    @Test
    fun `대화의 활성 여부가 개별 메시지의 보관 기간을 늘리지 않는다`() {
        val oldMessage = now.minus(Message.RETENTION).minus(Duration.ofDays(30))
        val recentMessage = now.minusSeconds(60)

        // 같은 대화에 둘 다 있어도 판정은 각자의 생성 시각으로만 갈린다.
        assertTrue(Message.isExpired(oldMessage, now))
        assertFalse(Message.isExpired(recentMessage, now))
    }

    @Test
    fun `삭제 기준 시각은 지금으로부터 보관 기간 이전이다`() {
        assertEquals(now.minus(Message.RETENTION), Message.retentionCutoff(now))
    }

    // ── MessageReport(R15) ──────────────────────────────────────────

    /** AC15-1 — 3건이 **하나의 신고 건**으로 접수된다. */
    @Test
    fun `여러 메시지가 한 건의 신고로 접수된다`() {
        val report = MessageReport.create(
            reporterId = 5L,
            conversationId = 1L,
            messageIds = listOf(10L, 11L, 12L),
            reason = ReportReason.ABUSE,
            detail = null,
        )

        assertEquals(listOf(10L, 11L, 12L), report.messageIds)
        assertEquals(1L, report.conversationId)
    }

    /** AC15-2 — 다건은 허용이지 강제가 아니다. */
    @Test
    fun `메시지 1건만으로도 신고된다`() {
        val report = MessageReport.create(5L, 1L, listOf(10L), ReportReason.SPAM, null)

        assertEquals(listOf(10L), report.messageIds)
    }

    /** AC15-3 — 신고할 것이 없는 신고는 없다. */
    @Test
    fun `대상이 비어 있으면 신고되지 않는다`() {
        assertFailsWith<IllegalArgumentException> {
            MessageReport.create(5L, 1L, emptyList(), ReportReason.SPAM, null)
        }
    }

    @Test
    fun `같은 메시지를 두 번 담아도 하나로 본다`() {
        val report = MessageReport.create(5L, 1L, listOf(10L, 10L, 11L), ReportReason.SPAM, null)

        assertEquals(listOf(10L, 11L), report.messageIds)
    }

    @Test
    fun `OTHER 사유는 상세 사유가 필요하다`() {
        assertFailsWith<IllegalArgumentException> {
            MessageReport.create(5L, 1L, listOf(10L), ReportReason.OTHER, detail = "  ")
        }
    }

    @Test
    fun `상세 사유는 앞뒤 공백이 제거되어 저장된다`() {
        val report = MessageReport.create(5L, 1L, listOf(10L), ReportReason.OTHER, detail = "  욕설  ")

        assertEquals("욕설", report.detail)
    }
}
