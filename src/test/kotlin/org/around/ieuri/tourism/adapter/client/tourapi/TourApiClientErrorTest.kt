package org.around.ieuri.tourism.adapter.client.tourapi

import io.mockk.mockk
import org.around.ieuri.tourism.adapter.config.TourApiProperties
import org.around.ieuri.tourism.application.shared.exception.UpstreamRateLimitedException
import org.around.ieuri.tourism.application.shared.exception.UpstreamTooManyRequestsException
import org.around.ieuri.tourism.application.shared.exception.UpstreamUnavailableException
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import java.io.IOException
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/**
 * 업스트림 실패의 분류(SPEC-0037): 오늘은 그만둔다 · 잠시 뒤 다시 한다 · 넘어간다.
 * - 잘못 분류되면 FR-008·AC-018이 조용히 깨진다.
 */
class TourApiClientErrorTest {

    private val client = TourApiClient(TourApiProperties(), mockk(), mockk())

    // ---- 응답 코드 판정(verify) ----

    @Test
    fun `정상 코드는 통과한다`() {
        client.verify(header("0000"))
    }

    @Test
    fun `빈 코드도 통과한다`() {
        // 헤더 없는 응답(빈 코드)은 성공이다.
        client.verify(header(""))
    }

    @Test
    fun `22는 일일 한도다 - 그날이 끝났으므로 재시도 대상이 아니다`() {
        assertFailsWith<UpstreamRateLimitedException> { client.verify(header("22")) }
    }

    @Test
    fun `23은 초당 한도다 - 잠시 뒤 풀리므로 일일 한도와 반드시 구분한다`() {
        // 초당 한도와 일일 한도를 뭉치면 초당 한도 한 번에 그날 수집이 끝난다.
        assertFailsWith<UpstreamTooManyRequestsException> { client.verify(header("23")) }
    }

    @Test
    fun `그 밖의 오류 코드는 전부 장애다`() {
        // 그 밖의 코드는 배치에서 결론이 같다 — 넘어간다.
        listOf("01", "04", "10", "12", "20", "29", "30", "31").forEach { code ->
            assertFailsWith<UpstreamUnavailableException>("code=$code") { client.verify(header(code)) }
        }
    }

    // ---- 전송 계층 예외 변환(translate) ----

    @Test
    fun `연결 실패·타임아웃은 장애다 - 재시도 대상이 되어야 한다`() {
        // 변환하지 않으면 재시도 대상으로 분류되지 않는다(FR-008).
        val translated = client.translate(ResourceAccessException("timeout", IOException("read timed out")))

        assertIs<UpstreamUnavailableException>(translated)
    }

    @Test
    fun `HTTP 429는 초당 한도다`() {
        val translated = client.translate(HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS))

        assertIs<UpstreamTooManyRequestsException>(translated)
    }

    @Test
    fun `5xx는 장애다`() {
        val translated = client.translate(HttpServerErrorException(HttpStatus.BAD_GATEWAY))

        assertIs<UpstreamUnavailableException>(translated)
    }

    @Test
    fun `4xx도 장애로 묶는다 - 배치에서는 결론이 같다`() {
        val translated = client.translate(HttpClientErrorException(HttpStatus.BAD_REQUEST))

        assertIs<UpstreamUnavailableException>(translated)
    }

    private fun header(code: String) = TourApiResponse.Header(resultCode = code, resultMsg = "msg")
}
