package org.around.ieuri.community.infrastructure

import org.around.ieuri.community.application.query.TypingEventView
import org.around.ieuri.community.infrastructure.adapter.out.event.mapper.NewMessagePayload
import org.around.ieuri.community.infrastructure.adapter.out.event.mapper.TypingEventPayload
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 브로커 페이로드 변환(SPEC-0017 R14).
 *
 * **이 테스트가 존재하는 이유가 곧 JSON으로 바꾼 이유다.** 이전 형식(`senderId|epochMillis`)은
 * 같은 타입 필드 둘이 위치로만 구분되어, 순서가 뒤바뀌어도 예외 없이 통과했다 —
 * 1786527431883번 회원이 1970년에 타이핑한 것으로 조용히 읽힌다.
 *
 * 그래서 **필드마다 다른 값을 준다.** 두 값이 같으면 뒤바뀌어도 테스트가 통과한다.
 */
class ConversationEventPayloadTest {

    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    private val conversationId = 300L
    private val senderId = 42L
    private val emittedAt = Instant.ofEpochMilli(1_786_527_431_883L)

    @Test
    fun `타이핑 이벤트가 페이로드로 변환된다`() {
        val payload = TypingEventPayload.from(
            TypingEventView(conversationId = conversationId, senderId = senderId, emittedAt = emittedAt),
        )

        assertEquals(senderId, payload.senderId)
        assertEquals(emittedAt.toEpochMilli(), payload.emittedAtEpochMilli)
    }

    /**
     * **conversationId는 페이로드에 없다** — 채널 이름이 이미 대화를 특정하므로 중복이다.
     * 복원할 때 구독자가 알고 있는 대화 id를 넣는다.
     */
    @Test
    fun `페이로드가 이벤트로 복원되며 대화 id는 구독자가 채운다`() {
        val view = TypingEventPayload(senderId = senderId, emittedAtEpochMilli = emittedAt.toEpochMilli())
            .toView(conversationId)

        assertEquals(conversationId, view.conversationId)
        assertEquals(senderId, view.senderId)
        assertEquals(emittedAt, view.emittedAt)
    }

    /** 직렬화→역직렬화 왕복에서 값이 보존되어야 한다. 실제로 브로커를 지나는 경로다. */
    @Test
    fun `타이핑 페이로드가 JSON 왕복에서 보존된다`() {
        val original = TypingEventPayload(senderId = senderId, emittedAtEpochMilli = emittedAt.toEpochMilli())

        val json = objectMapper.writeValueAsString(original)
        val restored = objectMapper.readValue(json, TypingEventPayload::class.java)

        assertEquals(original, restored)
    }

    /**
     * **필드 이름이 JSON에 실려야** 위치 뒤바뀜이 불가능해진다. 이름이 빠지면 이전 형식과
     * 같은 위험으로 돌아간다.
     */
    @Test
    fun `타이핑 페이로드 JSON에 필드 이름이 담긴다`() {
        val json = objectMapper.writeValueAsString(
            TypingEventPayload(senderId = senderId, emittedAtEpochMilli = emittedAt.toEpochMilli()),
        )

        assertEquals(true, json.contains("\"senderId\""), "senderId 이름이 없다: $json")
        assertEquals(true, json.contains("\"emittedAtEpochMilli\""), "emittedAtEpochMilli 이름이 없다: $json")
    }

    @Test
    fun `새 메시지 페이로드가 JSON 왕복에서 보존된다`() {
        val original = NewMessagePayload(messageId = 500L)

        val restored = objectMapper.readValue(
            objectMapper.writeValueAsString(original),
            NewMessagePayload::class.java,
        )

        assertEquals(original, restored)
    }
}
