package org.around.ieuri.community.infrastructure

import io.mockk.every
import io.mockk.mockk
import org.around.ieuri.community.infrastructure.adapter.out.event.ValkeyConversationEventAdapter
import org.around.ieuri.community.infrastructure.adapter.out.event.mapper.NewMessagePayload
import org.around.ieuri.community.infrastructure.adapter.out.event.mapper.TypingEventPayload
import org.junit.jupiter.api.Test
import org.springframework.data.redis.connection.ReactiveSubscription
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer
import reactor.core.publisher.Flux
import reactor.core.scheduler.Schedulers
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.Instant
import kotlin.test.assertEquals

/**
 * 브로커 구독 배선(SPEC-0017 R10·R14).
 * - 발행한 것이 하나도 빠지지 않고 순서대로 변환되어 나온다.
 */
class ConversationEventAdapterTest {

    private val redis = mockk<StringRedisTemplate>(relaxed = true)
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
    private val container = mockk<ReactiveRedisMessageListenerContainer>()

    /** 컨테이너가 그 채널로 흘려보낼 페이로드를 미리 정해 둔다. */
    private fun givenChannel(channel: String, vararg payloads: String) {
        every { container.receive(ChannelTopic(channel)) } returns
            Flux.fromIterable(payloads.map { message(channel, it) })
    }

    private fun message(channel: String, payload: String): ReactiveSubscription.Message<String, String> = object : ReactiveSubscription.Message<String, String> {
        override fun getChannel() = channel
        override fun getMessage() = payload
    }

    // 테스트는 스레드를 바꾸지 않는다.
    private fun adapter() = ValkeyConversationEventAdapter(redis, container, objectMapper, Schedulers.immediate())

    /** 새 메시지는 유실 없이 전부 도착한다. */
    @Test
    fun `연속으로 발행한 새 메시지 이벤트가 모두 순서대로 도착한다`() {
        givenChannel(
            "ieuri:community:message:1",
            *(1L..5L).map { objectMapper.writeValueAsString(NewMessagePayload(it)) }.toTypedArray(),
        )

        val received = adapter().subscribeNewMessage(1L).collectList().block()

        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), received)
    }

    /** 대화마다 채널이 다르다. */
    @Test
    fun `대화마다 서로 다른 채널을 구독한다`() {
        givenChannel("ieuri:community:message:1", objectMapper.writeValueAsString(NewMessagePayload(11L)))
        givenChannel("ieuri:community:message:2", objectMapper.writeValueAsString(NewMessagePayload(22L)))

        val adapter = adapter()

        assertEquals(listOf(11L), adapter.subscribeNewMessage(1L).collectList().block())
        assertEquals(listOf(22L), adapter.subscribeNewMessage(2L).collectList().block())
    }

    /** 깨진 페이로드는 조용히 버려지고 스트림은 살아 있다. */
    @Test
    fun `깨진 페이로드는 흘려보내고 이후 이벤트는 계속 도착한다`() {
        givenChannel(
            "ieuri:community:message:1",
            "not json",
            objectMapper.writeValueAsString(NewMessagePayload(7L)),
        )

        val received = adapter().subscribeNewMessage(1L).collectList().block()

        assertEquals(listOf(7L), received)
    }

    /** 타이핑 이벤트의 대화 id는 구독자가 채운다. */
    @Test
    fun `타이핑 이벤트에 구독한 대화 id가 채워진다`() {
        val payload = TypingEventPayload(senderId = 42L, emittedAtEpochMilli = 1_786_527_431_883L)
        givenChannel("ieuri:community:typing:300", objectMapper.writeValueAsString(payload))

        val received = adapter().subscribeTyping(300L).collectList().block()!!.single()

        assertEquals(300L, received.conversationId)
        assertEquals(42L, received.senderId)
        assertEquals(Instant.ofEpochMilli(1_786_527_431_883L), received.emittedAt)
    }
}
