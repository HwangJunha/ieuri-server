package org.around.ieuri.alarm.application.push

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.around.ieuri.alarm.application.device.provided.DeviceTokenCommandUseCase
import org.around.ieuri.alarm.application.device.provided.DeviceTokenQueryUseCase
import org.around.ieuri.alarm.application.device.provided.dto.DeviceTokenView
import org.around.ieuri.alarm.application.preference.provided.NotificationPreferenceQueryUseCase
import org.around.ieuri.alarm.application.push.provided.NotificationMessageQueryUseCase
import org.around.ieuri.alarm.application.push.provided.dto.PushDispatchRequest
import org.around.ieuri.alarm.application.push.required.BlockLookupPort
import org.around.ieuri.alarm.application.push.required.PushSenderPort
import org.around.ieuri.alarm.application.push.required.dto.PushSendResult
import org.around.ieuri.alarm.application.push.required.dto.PushTarget
import org.around.ieuri.alarm.domain.device.AlarmLocale
import org.around.ieuri.alarm.domain.device.DevicePlatform
import org.around.ieuri.alarm.domain.preference.NotificationCategory
import org.around.ieuri.alarm.domain.push.NotificationMessage
import org.around.ieuri.alarm.domain.push.NotificationType
import org.around.ieuri.alarm.domain.push.PushDestination
import org.around.ieuri.alarm.domain.push.PushNotification
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** 발송 판정과 페이로드 구성(SPEC-0028 R5~R13). */
class PushDispatchServiceTest {

    private val preferences = mockk<NotificationPreferenceQueryUseCase>()
    private val tokenQuery = mockk<DeviceTokenQueryUseCase>()
    private val tokenCommand = mockk<DeviceTokenCommandUseCase>(relaxed = true)
    private val messageQuery = mockk<NotificationMessageQueryUseCase>()
    private val blockLookup = mockk<BlockLookupPort>()
    private val pushSender = mockk<PushSenderPort>()

    private val service = PushDispatchService(preferences, tokenQuery, tokenCommand, messageQuery, blockLookup, pushSender)

    private val recipientId = 1L
    private val actorId = 2L

    /** 언어마다 다른 값을 줘, 잘못된 언어의 문구가 나가면 깨지게 한다. */
    private val messages = AlarmLocale.entries.associateWith { locale ->
        NotificationMessage.of(
            type = NotificationType.SPOT_QA_ANSWER,
            locale = locale,
            title = "${locale.name}제목",
            body = "${locale.name}본문",
        )
    }

    @BeforeTest
    fun setUp() {
        every { preferences.isEnabled(any(), any()) } returns true
        every { messageQuery.messagesOf(any()) } returns messages
        every { blockLookup.isBlockedBetween(any(), any()) } returns false
        every { pushSender.send(any(), any()) } returns PushSendResult.NONE
    }

    private fun token(
        deviceId: String,
        fcmToken: String = "token-$deviceId",
        language: AlarmLocale = AlarmLocale.KO,
    ) = DeviceTokenView(
        deviceId = deviceId,
        fcmToken = fcmToken,
        language = language,
        platform = DevicePlatform.ANDROID,
    )

    private fun request(
        recipient: Long = recipientId,
        actor: Long = actorId,
        destination: PushDestination = PushDestination.Dm(50L),
    ) = PushDispatchRequest(recipient, actor, destination)

    private fun givenTokens(vararg tokens: DeviceTokenView) {
        every { tokenQuery.tokensOf(recipientId) } returns tokens.toList()
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
        // 걸러졌으면 토큰 조회조차 하지 않는다.
        verify(exactly = 0) { tokenQuery.tokensOf(any()) }
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
        every { preferences.isEnabled(recipientId, NotificationCategory.MESSAGE) } returns false

        service.dispatch(request(destination = PushDestination.Dm(50L)))

        verify(exactly = 0) { pushSender.send(any(), any()) }
    }

    /** AC10.2 — 카테고리는 독립이다. MESSAGE를 꺼도 SPOT_QA는 나간다. */
    @Test
    fun `끈 카테고리와 다른 카테고리는 발송한다`() {
        givenTokens(token("d1"))
        every { preferences.isEnabled(recipientId, NotificationCategory.MESSAGE) } returns false
        every { preferences.isEnabled(recipientId, NotificationCategory.SPOT_QA) } returns true

        service.dispatch(request(destination = PushDestination.SpotQaAnswer(11L, 22L)))

        verify(exactly = 1) { pushSender.send(any(), any()) }
    }

    /** AC10.3 — 설정을 바꾼 적 없는 회원은 켜짐이다. */
    @Test
    fun `설정 행이 없으면 발송한다`() {
        givenTokens(token("d1"))
        every { preferences.isEnabled(any(), any()) } returns true

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

    /** 제목은 수신 기기 언어의 문구다. */
    @Test
    fun `제목은 기기 언어의 문구다`() {
        givenTokens(token("d1", language = AlarmLocale.JA))
        val notification = slot<PushNotification>()

        service.dispatch(request())

        verify { pushSender.send(capture(notification), any()) }
        assertEquals("JA제목", notification.captured.title)
    }

    /** 본문이 반드시 실린다 — 비면 iOS가 알림을 조용히 버린다. */
    @Test
    fun `본문이 비지 않는다`() {
        givenTokens(token("d1", language = AlarmLocale.JA))
        val notification = slot<PushNotification>()

        service.dispatch(request())

        verify { pushSender.send(capture(notification), any()) }
        assertEquals("JA본문", notification.captured.body)
    }

    /** 알림 종류마다 다른 문구를 읽는다(답변과 답글은 한 카테고리지만 문구가 갈린다). */
    @Test
    fun `알림 종류로 문구를 찾는다`() {
        givenTokens(token("d1"))

        service.dispatch(request(destination = PushDestination.SpotQaReply(11L, 22L)))

        verify { messageQuery.messagesOf(NotificationType.SPOT_QA_REPLY) }
    }

    /** POL-039 폴백 — 그 언어 문구가 없으면 EN으로 떨어뜨린다. */
    @Test
    fun `문구가 없는 언어는 EN으로 떨어진다`() {
        givenTokens(token("d1", language = AlarmLocale.ZH))
        every { messageQuery.messagesOf(any()) } returns mapOf(AlarmLocale.EN to messages.getValue(AlarmLocale.EN))
        val notification = slot<PushNotification>()

        service.dispatch(request())

        verify { pushSender.send(capture(notification), any()) }
        assertEquals("EN제목", notification.captured.title)
    }

    /** 문구가 아예 없으면 발송하지 않는다. */
    @Test
    fun `문구가 하나도 없으면 발송하지 않는다`() {
        givenTokens(token("d1"))
        every { messageQuery.messagesOf(any()) } returns emptyMap()

        service.dispatch(request())

        verify(exactly = 0) { pushSender.send(any(), any()) }
    }

    /** AC4.1·AC4.3 — 무효로 판정된 기기만 지운다. */
    @Test
    fun `무효 토큰만 삭제한다`() {
        givenTokens(token("d1"), token("d2"))
        every { pushSender.send(any(), any()) } returns
            PushSendResult(successCount = 1, invalidDeviceIds = listOf("d2"))

        service.dispatch(request())

        verify { tokenCommand.removeInvalid(listOf("d2")) }
    }

    /** AC4.4·AC4.5·AC4.6 — 어댑터가 무효로 판정하지 않으면 아무것도 지우지 않는다. */
    @Test
    fun `무효 판정이 없으면 삭제하지 않는다`() {
        givenTokens(token("d1"))
        every { pushSender.send(any(), any()) } returns
            PushSendResult(successCount = 0, invalidDeviceIds = emptyList())

        service.dispatch(request())

        verify { tokenCommand.removeInvalid(emptyList()) }
    }
}
