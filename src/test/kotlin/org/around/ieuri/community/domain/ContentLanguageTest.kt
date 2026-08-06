package org.around.ieuri.community.domain

import org.around.ieuri.community.domain.vo.ContentLanguage
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 요청 언어 정규화. 상단 칩 구성이 언어마다 다르므로 이 판정이 틀리면 엉뚱한 칩이 뜬다
 * (여행코스는 국문 전용, 교통은 외국어 전용).
 *
 * **어떤 입력에도 예외를 던지지 않는다** — Accept-Language는 클라이언트가 보내는 값이라
 * 무엇이 올지 통제할 수 없고, 헤더 하나 때문에 조회가 실패하면 안 된다.
 */
class ContentLanguageTest {

    @Test
    fun `지원 언어를 그대로 알아본다`() {
        assertEquals(ContentLanguage.EN, ContentLanguage.fromLanguageTag("en"))
        assertEquals(ContentLanguage.KO, ContentLanguage.fromLanguageTag("ko"))
        assertEquals(ContentLanguage.JA, ContentLanguage.fromLanguageTag("ja"))
        assertEquals(ContentLanguage.ZH, ContentLanguage.fromLanguageTag("zh"))
    }

    @Test
    fun `지역 서브태그는 떼고 본다`() {
        // 실제로 오는 값은 "ko-KR" 같은 형태다. 이걸 못 잘라 내면 전부 폴백이 된다.
        assertEquals(ContentLanguage.KO, ContentLanguage.fromLanguageTag("ko-KR"))
        assertEquals(ContentLanguage.ZH, ContentLanguage.fromLanguageTag("zh-Hant"))
        assertEquals(ContentLanguage.EN, ContentLanguage.fromLanguageTag("en-US"))
    }

    @Test
    fun `대소문자와 앞뒤 공백을 가리지 않는다`() {
        assertEquals(ContentLanguage.KO, ContentLanguage.fromLanguageTag("KO"))
        assertEquals(ContentLanguage.JA, ContentLanguage.fromLanguageTag("  Ja-JP  "))
    }

    @Test
    fun `헤더가 없으면 폴백이다`() {
        assertEquals(ContentLanguage.FALLBACK, ContentLanguage.fromLanguageTag(null))
    }

    @Test
    fun `모르는 언어도 오류가 아니라 폴백이다`() {
        // 헤더 하나 때문에 조회가 실패하면 안 된다.
        assertEquals(ContentLanguage.FALLBACK, ContentLanguage.fromLanguageTag("fr"))
        assertEquals(ContentLanguage.FALLBACK, ContentLanguage.fromLanguageTag(""))
        assertEquals(ContentLanguage.FALLBACK, ContentLanguage.fromLanguageTag("   "))
        assertEquals(ContentLanguage.FALLBACK, ContentLanguage.fromLanguageTag("아무거나"))
    }

    @Test
    fun `폴백은 외국어다 - 외국인 방문객이 기본 대상이다`() {
        assertEquals(ContentLanguage.EN, ContentLanguage.FALLBACK)
    }
}
