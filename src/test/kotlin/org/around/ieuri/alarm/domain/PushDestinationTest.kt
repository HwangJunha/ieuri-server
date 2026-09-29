package org.around.ieuri.alarm.domain

import org.around.ieuri.alarm.domain.vo.NotificationCategory
import org.around.ieuri.alarm.domain.vo.NotificationType
import org.around.ieuri.alarm.domain.vo.PushDestination
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * FCM 데이터 페이로드(SPEC-0028 R11).
 * - 값마다 다른 수를 넣어 spotId·questionId가 뒤바뀌면 깨지게 한다.
 */
class PushDestinationTest {

    /** AC11.1 */
    @Test
    fun `DM은 type과 conversationId를 싣는다`() {
        val data = PushDestination.Dm(conversationId = 71L).asData()

        assertEquals("DM", data["type"])
        assertEquals("71", data["conversationId"])
        assertEquals(setOf("type", "conversationId"), data.keys)
    }

    /** AC11.2 — 질문 상세 라우트가 spotId·questionId를 둘 다 요구한다. */
    @Test
    fun `답변은 type과 spotId 그리고 questionId를 싣는다`() {
        val data = PushDestination.SpotQaAnswer(spotId = 11L, questionId = 22L).asData()

        assertEquals("SPOT_QA_ANSWER", data["type"])
        assertEquals("11", data["spotId"])
        assertEquals("22", data["questionId"])
        assertEquals(setOf("type", "spotId", "questionId"), data.keys)
    }

    /** AC11.3 — 목적지는 답변과 같은 질문 상세지만 type은 구분된다. */
    @Test
    fun `답글은 type과 spotId 그리고 questionId를 싣는다`() {
        val data = PushDestination.SpotQaReply(spotId = 33L, questionId = 44L).asData()

        assertEquals("SPOT_QA_REPLY", data["type"])
        assertEquals("33", data["spotId"])
        assertEquals("44", data["questionId"])
        assertEquals(setOf("type", "spotId", "questionId"), data.keys)
    }

    @Test
    fun `DM은 MESSAGE 카테고리다`() {
        assertEquals(NotificationCategory.MESSAGE, PushDestination.Dm(1L).category)
    }

    /** 답변과 답글은 한 카테고리로 묶인다. */
    @Test
    fun `답변과 답글은 SPOT_QA 카테고리다`() {
        assertEquals(NotificationCategory.SPOT_QA, PushDestination.SpotQaAnswer(1L, 2L).category)
        assertEquals(NotificationCategory.SPOT_QA, PushDestination.SpotQaReply(1L, 2L).category)
    }

    /** 카테고리는 같아도 종류(문구 조회 키)는 갈린다. */
    @Test
    fun `답변과 답글은 종류가 다르다`() {
        assertEquals(NotificationType.SPOT_QA_ANSWER, PushDestination.SpotQaAnswer(1L, 2L).type)
        assertEquals(NotificationType.SPOT_QA_REPLY, PushDestination.SpotQaReply(1L, 2L).type)
        assertEquals(NotificationType.DM, PushDestination.Dm(1L).type)
    }

    /** FCM 데이터의 `type` 값이 곧 종류의 이름이다. */
    @Test
    fun `데이터의 type이 종류 이름과 같다`() {
        listOf<PushDestination>(
            PushDestination.Dm(1L),
            PushDestination.SpotQaAnswer(1L, 2L),
            PushDestination.SpotQaReply(1L, 2L),
        ).forEach { destination ->
            assertEquals(destination.type.name, destination.asData()["type"])
        }
    }
}
