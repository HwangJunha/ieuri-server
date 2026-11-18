package org.around.ieuri.tourism.adapter.client.kto.dto

import tools.jackson.core.type.TypeReference
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 관광통계 응답 역직렬화(SPEC-0015 R8). 결과 없음의 `"items": ""`를 흡수해야 부재가 장애로 둔갑하지 않는다. */
class KtoBigDataResponseTest {

    // TourismConfig.ktoBigDataRestClient와 같은 설정이어야 의미가 있다.
    private val mapper = JsonMapper.builder()
        .addModule(kotlinModule())
        .enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
        .build()

    private fun parse(json: String): KtoBigDataResponse<KtoVisitorCountItem> = mapper.readValue(json, object : TypeReference<KtoBigDataResponse<KtoVisitorCountItem>>() {})

    @Test
    fun `결과가 없으면 items가 빈 문자열로 오며 빈 목록으로 흡수된다`() {
        // 결과 없는 날짜를 요청하면 이 형태로 온다.
        val json = """
            {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},
             "body":{"items":"","numOfRows":0,"pageNo":1,"totalCount":0}}}
        """.trimIndent()

        val response = parse(json)

        assertTrue(response.items.isEmpty())
        assertEquals(0, response.totalCount)
        assertTrue(response.isSuccess)
    }

    @Test
    fun `정상 응답은 항목을 그대로 읽는다`() {
        val json = """
            {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},
             "body":{"items":{"item":[
               {"signguCode":"11110","signguNm":"종로구","touDivCd":"1","touDivNm":"현지인(a)",
                "touNum":"186126.5","baseYmd":"20260701"}]},
              "numOfRows":1,"pageNo":1,"totalCount":807}}}
        """.trimIndent()

        val response = parse(json)

        assertEquals(1, response.items.size)
        assertEquals("11110", response.items[0].signguCode)
        assertEquals("186126.5", response.items[0].touNum)
        assertEquals("20260701", response.items[0].baseYmd)
        assertEquals(807, response.totalCount)
    }
}
