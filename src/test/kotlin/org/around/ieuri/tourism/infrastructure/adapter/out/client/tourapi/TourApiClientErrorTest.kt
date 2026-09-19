package org.around.ieuri.tourism.infrastructure.adapter.out.client.tourapi

import io.mockk.mockk
import org.around.ieuri.tourism.application.exception.UpstreamRateLimitedException
import org.around.ieuri.tourism.application.exception.UpstreamTooManyRequestsException
import org.around.ieuri.tourism.application.exception.UpstreamUnavailableException
import org.around.ieuri.tourism.infrastructure.config.TourApiProperties
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import java.io.IOException
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/**
 * 업스트림 실패의 분류를 고정한다(SPEC-0037). 배치가 구분해야 하는 결론은 셋뿐이다 —
 * **오늘은 그만둔다 · 잠시 뒤 다시 한다 · 넘어간다**. 잘못 분류되면 FR-008·AC-018이 조용히 깨진다.
 *
 * 두 함수 모두 HTTP·역직렬화와 분리된 순수 함수라 컨텍스트 없이 부를 수 있다.
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
        // DTO 기본값이 빈 문자열이라, 헤더 없는 응답을 실패로 만들면 정상 조회가 장애로 둔갑한다.
        client.verify(header(""))
    }

    @Test
    fun `22는 일일 한도다 - 그날이 끝났으므로 재시도 대상이 아니다`() {
        assertFailsWith<UpstreamRateLimitedException> { client.verify(header("22")) }
    }

    @Test
    fun `23은 초당 한도다 - 잠시 뒤 풀리므로 일일 한도와 반드시 구분한다`() {
        // 둘을 같은 타입으로 뭉치면 초당 한도 한 번에 그날 수집이 통째로 끝난다.
        assertFailsWith<UpstreamTooManyRequestsException> { client.verify(header("23")) }
    }

    @Test
    fun `그 밖의 오류 코드는 전부 장애다`() {
        // 01(내부 오류)·30(미등록 키)·31(기한 만료) 등은 배치에서 결론이 같다 — 넘어간다.
        listOf("01", "04", "10", "12", "20", "29", "30", "31").forEach { code ->
            assertFailsWith<UpstreamUnavailableException>("code=$code") { client.verify(header(code)) }
        }
    }

    // ---- 전송 계층 예외 변환(translate) ----

    @Test
    fun `연결 실패·타임아웃은 장애다 - 재시도 대상이 되어야 한다`() {
        // 변환하지 않으면 RestClientException이 그대로 새어 재시도 0회가 된다.
        // FR-008이 "네트워크 오류는 재시도한다"고 지정한 것이 정확히 이 경우다.
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
