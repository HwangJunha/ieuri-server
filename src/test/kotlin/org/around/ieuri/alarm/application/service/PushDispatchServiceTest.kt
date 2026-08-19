package org.around.ieuri.alarm.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.around.ieuri.alarm.application.port.`in`.command.PushDispatchRequest
import org.around.ieuri.alarm.application.port.out.client.BlockLookupPort
import org.around.ieuri.alarm.application.port.out.client.MemberNicknameLookupPort
import org.around.ieuri.alarm.application.port.out.push.PushSendResult
import org.around.ieuri.alarm.application.port.out.push.PushSenderPort
import org.around.ieuri.alarm.application.port.out.push.PushTarget
import org.around.ieuri.alarm.domain.aggregate.DeviceToken
import org.around.ieuri.alarm.domain.vo.AlarmLocale
import org.around.ieuri.alarm.domain.vo.DevicePlatform
import org.around.ieuri.alarm.domain.vo.NotificationCategory
import org.around.ieuri.alarm.domain.vo.PushDestination
import org.around.ieuri.alarm.domain.vo.PushMessages
import org.around.ieuri.alarm.domain.vo.PushNotification

/** 발송 판정과 페이로드 구성(SPEC-0028 R5~R13). */
class PushDispatchServiceTest {

    private val store = mockk<PushDispatchStore>(relaxed = true)
    private val blockLookup = mockk<BlockLookupPort>()
    private val nicknameLookup = mockk<MemberNicknameLookupPort>()
    private val pushSender = mockk<PushSenderPort>()

    private val service = PushDispatchService(store, blockLookup, nicknameLookup, pushSender)

    private val recipientId = 1L
    private val actorId = 2L

    @BeforeTest
    fun setUp() {
        every { store.categoryEnabled(any(), any()) } returns true
        every { blockLookup.isBlockedBetween(any(), any()) } returns false
        every { nicknameLookup.findNickname(any()) } returns "보낸사람"
        every { pushSender.send(any(), any()) } returns PushSendResult.NONE
    }

    private fun token(
        deviceId: String,
        fcmToken: String = "token-$deviceId",
        language: AlarmLocale = AlarmLocale.KO,
    ) = DeviceToken.restore(
        id = 1L,
        deviceId = deviceId,
        memberId = recipientId,
        fcmToken = fcmToken,
        language = language,
        platform = DevicePlatform.ANDROID,
        createdAt = Instant.parse("2026-08-19T00:00:00Z"),
    )

    private fun request(
        recipient: Long = recipientId,
        actor: Long = actorId,
        destination: PushDestination = PushDestination.Dm(50L),
        body: String = "본문",
    ) = PushDispatchRequest(recipient, actor, destination, body)

    private fun givenTokens(vararg tokens: DeviceToken) {
        every { store.findTokens(recipientId) } returns tokens.toList()
    }

    /** AC5.1 */
    @Test
    fun `토큰이 있으면 발송한다`() {
        givenTokens(token("d1"))

        service.dispatch(request())

        verify(exactly = 1) { pushSender.send(any(), any()) }
    }

    /** AC5.2·AC6.3·AC3.2 — 토큰이 없으면 발송하지 않으며 오류도 아니다. */
    @Test
    fun `토큰이 없으면 발송하지 않는다`() {
        givenTokens()

        service.dispatch(request())

        verify(exactly = 0) { pushSender.send(any(), any()) }
    }

    /** AC5.3 — 기기 2대. 같은 언어면 한 번의 발송에 대상 둘이 실린다. */
    @Test
    fun `같은 언어의 기기 둘은 한 번에 보낸다`() {
        givenTokens(token("d1"), token("d2"))
        val targets = slot<List<PushTarget>>()

        service.dispatch(request())

        verify { pushSender.send(any(), capture(targets)) }
        assertEquals(listOf("d1", "d2"), targets.captured.map { it.deviceId })
    }

    /** §5 — 기기마다 언어가 다르면 제목이 갈리므로 발송을 나눈다. */
    @Test
    fun `언어가 다른 기기는 나눠 보낸다`() {
        givenTokens(token("d1", language = AlarmLocale.KO), token("d2", language = AlarmLocale.JA))

        service.dispatch(request())

        verify(exactly = 2) { pushSender.send(any(), any()) }
    }

    /** AC8.1·AC8.2·AC8.3 — 자기 행위로는 자신에게 가지 않는다. */
    @Test
    fun `자기 행위로는 발송하지 않는다`() {
        givenTokens(token("d1"))

        service.dispatch(request(recipient = 5L, actor = 5L))

        verify(exactly = 0) { pushSender.send(any(), any()) }
        // 걸러졌으면 토큰 조회조차 하지 않는다 — 판정은 싼 것부터다.
        verify(exactly = 0) { store.findTokens(any()) }
    }

    /** AC9.1·AC9.2 — 차단은 양방향이다(POL-041). */
    @Test
    fun `차단 관계면 발송하지 않는다`() {
        givenTokens(token("d1"))
        every { blockLookup.isBlockedBetween(recipientId, actorId) } returns true

        service.dispatch(request())

        verify(exactly = 0) { pushSender.send(any(), any()) }
    }

    /** AC9.3 — 차단이 풀리면 다시 발송된다. */
    @Test
    fun `차단이 없으면 발송한다`() {
        givenTokens(token("d1"))
        every { blockLookup.isBlockedBetween(recipientId, actorId) } returns false

        service.dispatch(request())

        verify(exactly = 1) { pushSender.send(any(), any()) }
    }

    /** AC10.1 — 카테고리를 끈 회원. */
    @Test
    fun `카테고리를 끈 회원에게는 발송하지 않는다`() {
        givenTokens(token("d1"))
        every { store.categoryEnabled(recipientId, NotificationCategory.MESSAGE) } returns false

        service.dispatch(request(destination = PushDestination.Dm(50L)))

        verify(exactly = 0) { pushSender.send(any(), any()) }
    }

    /** AC10.2 — 카테고리는 독립이다. MESSAGE를 꺼도 SPOT_QA는 나간다. */
    @Test
    fun `끈 카테고리와 다른 카테고리는 발송한다`() {
        givenTokens(token("d1"))
        every { store.categoryEnabled(recipientId, NotificationCategory.MESSAGE) } returns false
        every { store.categoryEnabled(recipientId, NotificationCategory.SPOT_QA) } returns true

        service.dispatch(request(destination = PushDestination.SpotQaAnswer(11L, 22L)))

        verify(exactly = 1) { pushSender.send(any(), any()) }
    }

    /** AC10.3 — 설정을 바꾼 적 없는 회원은 켜짐이다. */
    @Test
    fun `설정 행이 없으면 발송한다`() {
        givenTokens(token("d1"))
        every { store.categoryEnabled(any(), any()) } returns true

        service.dispatch(request())

        verify(exactly = 1) { pushSender.send(any(), any()) }
    }

    /** AC11.1 — 페이로드에 목적지가 실린다. */
    @Test
    fun `발송 페이로드에 목적지가 실린다`() {
        givenTokens(token("d1"))
        val notification = slot<PushNotification>()

        service.dispatch(request(destination = PushDestination.SpotQaAnswer(11L, 22L)))

        verify { pushSender.send(capture(notification), any()) }
        assertEquals("11", notification.captured.destination.asData()["spotId"])
        assertEquals("22", notification.captured.destination.asData()["questionId"])
    }

    /** AC13.1 — 본문은 원문 그대로. 수신자 언어가 EN이어도 번역하지 않는다. */
    @Test
    fun `본문은 수신자 언어와 무관하게 원문이다`() {
        givenTokens(token("d1", language = AlarmLocale.EN))
        val notification = slot<PushNotification>()

        service.dispatch(request(body = "한국어 본문입니다"))

        verify { pushSender.send(capture(notification), any()) }
        assertEquals("한국어 본문입니다", notification.captured.body)
    }

    /** AC13.2 — 긴 본문은 잘려서 나간다. */
    @Test
    fun `긴 본문은 잘려서 발송된다`() {
        givenTokens(token("d1"))
        val notification = slot<PushNotification>()

        service.dispatch(request(body = "가".repeat(PushMessages.BODY_MAX_LENGTH + 100)))

        verify { pushSender.send(capture(notification), any()) }
        assertEquals(PushMessages.BODY_MAX_LENGTH, notification.captured.body.length)
    }

    /** 제목은 수신 기기 언어로 번역된다(R13 — 서버가 조립하는 문구). */
    @Test
    fun `제목은 기기 언어로 번역된다`() {
        givenTokens(token("d1", language = AlarmLocale.JA))
        val notification = slot<PushNotification>()

        service.dispatch(request(destination = PushDestination.SpotQaAnswer(11L, 22L)))

        verify { pushSender.send(capture(notification), any()) }
        assertEquals(PushMessages.answerTitle(AlarmLocale.JA), notification.captured.title)
    }

    /** DM 제목에는 보낸 사람 닉네임이 들어간다. */
    @Test
    fun `DM 제목에 보낸 사람 닉네임이 들어간다`() {
        givenTokens(token("d1"))
        every { nicknameLookup.findNickname(actorId) } returns "홍길동"
        val notification = slot<PushNotification>()

        service.dispatch(request(destination = PushDestination.Dm(50L)))

        verify { pushSender.send(capture(notification), any()) }
        assertTrue(notification.captured.title.contains("홍길동"))
    }

    /** 닉네임을 못 읽어도 발송한다 — 알림을 거르는 것보다 낫다. */
    @Test
    fun `닉네임이 없어도 DM은 발송된다`() {
        givenTokens(token("d1"))
        every { nicknameLookup.findNickname(actorId) } returns null
        val notification = slot<PushNotification>()

        service.dispatch(request(destination = PushDestination.Dm(50L)))

        verify { pushSender.send(capture(notification), any()) }
        assertEquals(PushMessages.dmTitleWithoutSender(AlarmLocale.KO), notification.captured.title)
    }

    /** Q&A 알림은 닉네임을 읽지 않는다 — 제목에 쓰지 않으므로 조회가 붙으면 안 된다. */
    @Test
    fun `답변 알림은 닉네임을 조회하지 않는다`() {
        givenTokens(token("d1"))

        service.dispatch(request(destination = PushDestination.SpotQaAnswer(11L, 22L)))

        verify(exactly = 0) { nicknameLookup.findNickname(any()) }
    }

    /** AC4.1·AC4.3 — 무효로 판정된 기기만 지운다. */
    @Test
    fun `무효 토큰만 삭제한다`() {
        givenTokens(token("d1"), token("d2"))
        every { pushSender.send(any(), any()) } returns
            PushSendResult(successCount = 1, invalidDeviceIds = listOf("d2"))

        service.dispatch(request())

        verify { store.removeInvalidTokens(listOf("d2")) }
    }

    /** AC4.4·AC4.5·AC4.6 — 어댑터가 무효로 판정하지 않으면 아무것도 지우지 않는다. */
    @Test
    fun `무효 판정이 없으면 삭제하지 않는다`() {
        givenTokens(token("d1"))
        every { pushSender.send(any(), any()) } returns
            PushSendResult(successCount = 0, invalidDeviceIds = emptyList())

        service.dispatch(request())

        verify { store.removeInvalidTokens(emptyList()) }
    }
}
