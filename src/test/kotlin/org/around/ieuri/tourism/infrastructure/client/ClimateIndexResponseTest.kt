package org.around.ieuri.tourism.infrastructure.client

import org.around.ieuri.tourism.infrastructure.adapter.out.client.climateindex.dto.ClimateIndexResponse
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 관광기후지수 응답 역직렬화(SPEC-0015 · V49 · R8).
 *
 * 두 가지가 여기서만 잡힌다: **성공 코드가 `"00"`이라는 것**(데이터포털 공통 `"0000"`이 아니다)과
 * **`TCI_GRADE`만 대문자 스네이크라는 것**. 둘 다 틀려도 컴파일은 통과한다.
 */
class ClimateIndexResponseTest {

    // tourApiRestClient가 쓰는 기본 설정 + 빈 문자열 흡수.
    private val mapper = JsonMapper.builder()
        .addModule(kotlinModule())
        .enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
        .build()

    private fun parse(json: String): ClimateIndexResponse =
        mapper.readValue(json, ClimateIndexResponse::class.java)

    /** 실측 응답(2026-08-11). 필드마다 값을 다르게 둬 자리가 뒤바뀌면 드러난다. */
    @Test
    fun `실측 응답의 모든 필드가 각각의 자리로 읽힌다`() {
        val json = """
            {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},
             "body":{"dataType":"JSON","items":{"item":[
               {"tm":"2026-08-12 00:00","totalCityName":"제주 서귀포시","doName":"제주",
                "cityName":"서귀포시","cityAreaId":"5013000000","kmaTci":0.06,"TCI_GRADE":"보통"}
             ]},"pageNo":1,"numOfRows":1000,"totalCount":472}}}
        """.trimIndent()

        val response = parse(json)

        assertTrue(response.hasHeader)
        assertTrue(response.isSuccess)
        assertFalse(response.isRateLimited)
        assertEquals("00", response.resultCode)
        assertEquals("NORMAL_SERVICE", response.resultMessage)
        assertEquals(472, response.totalCount)

        val item = response.items.single()
        assertEquals("2026-08-12 00:00", item.tm)
        assertEquals("5013000000", item.cityAreaId)
        assertEquals("제주 서귀포시", item.totalCityName)
        assertEquals("제주", item.doName)
        assertEquals("서귀포시", item.cityName)
        assertEquals(BigDecimal("0.06"), item.kmaTci)
        // 이 필드만 대문자 스네이크다 — 이름을 맞추지 않으면 조용히 null이 된다.
        assertEquals("보통", item.tciGrade)
    }

    /** `kmaTci`는 JSON **숫자**다(관광통계 4종의 소수 문자열과 다르다). 음수도 온다. */
    @Test
    fun `지수는 숫자로 오며 음수도 읽힌다`() {
        val json = """
            {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},
             "body":{"items":{"item":[
               {"tm":"2026-08-12 00:00","cityAreaId":"1111000000","kmaTci":-0.66,"TCI_GRADE":"나쁨"}
             ]},"totalCount":1}}}
        """.trimIndent()

        assertEquals(BigDecimal("-0.66"), parse(json).items.single().kmaTci)
    }

    /** 결과가 없는 응답. 빈 문자열 items를 흡수하고 장애가 아니다(R8). */
    @Test
    fun `결과가 없어도 성공이며 빈 목록이 된다`() {
        val json = """
            {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},
             "body":{"items":"","pageNo":1,"numOfRows":1000,"totalCount":0}}}
        """.trimIndent()

        val response = parse(json)

        assertTrue(response.isSuccess)
        assertTrue(response.items.isEmpty())
        assertEquals(0, response.totalCount)
    }

    /**
     * 인증 실패 등에서는 다른 봉투(`OpenAPI_ServiceResponse`)가 온다. header가 없어
     * [ClimateIndexResponse.hasHeader]가 거짓이며, 클라이언트가 이것을 장애로 바꾼다 —
     * 성공으로 통과시키면 0건이 "데이터 없음"으로 조용히 흡수된다.
     */
    @Test
    fun `오류 봉투는 header가 없어 정상 응답과 구분된다`() {
        val json = """
            {"OpenAPI_ServiceResponse":{"cmmMsgHeader":{
              "errMsg":"SERVICE ERROR","returnAuthMsg":"SERVICE_KEY_IS_NOT_REGISTERED_ERROR",
              "returnReasonCode":"30"}}}
        """.trimIndent()

        val response = parse(json)

        assertFalse(response.hasHeader)
        assertTrue(response.items.isEmpty())
    }

    /** 한도 초과는 장애와 구분해 표면화한다(R8). */
    @Test
    fun `한도 초과 코드는 rate limited로 판정된다`() {
        val json = """
            {"response":{"header":{"resultCode":"22",
             "resultMsg":"LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR"},"body":{}}}
        """.trimIndent()

        val response = parse(json)

        assertTrue(response.hasHeader)
        assertTrue(response.isRateLimited)
        assertFalse(response.isSuccess)
    }

    /** 모르는 필드가 늘어도 역직렬화가 깨지지 않는다. */
    @Test
    fun `모르는 필드는 무시된다`() {
        val json = """
            {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},
             "body":{"dataType":"JSON","items":{"item":[
               {"tm":"2026-08-12 00:00","cityAreaId":"5013000000","kmaTci":0.06,
                "newFieldNobodyKnows":"x"}
             ]},"totalCount":1}}}
        """.trimIndent()

        val item = parse(json).items.single()

        assertEquals("5013000000", item.cityAreaId)
        assertNull(item.tciGrade)
    }
}
