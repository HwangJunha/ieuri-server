package org.around.ieuri.tourism.domain.shared

import kotlin.test.Test
import kotlin.test.assertEquals

/** 구역명 언어 선택과 폴백(SPEC-0014 R31). 폴백 순서는 활성 언어 → EN → KO다. */
class AreaNameTest {

    private val full = AreaName(ko = "강남역", en = "Gangnam station", ja = "江南駅", zh = "江南站")

    @Test
    fun `활성 언어의 이름을 돌려준다`() {
        // AC R31.
        assertEquals("강남역", full.resolve(DomainLocale.KO))
        assertEquals("Gangnam station", full.resolve(DomainLocale.EN))
        assertEquals("江南駅", full.resolve(DomainLocale.JA))
        assertEquals("江南站", full.resolve(DomainLocale.ZH))
    }

    @Test
    fun `활성 언어의 이름이 없으면 EN으로 폴백한다`() {
        // AC R31. 서울시가 구역을 추가하면 번역 없이 먼저 들어올 수 있다.
        val noTranslation = full.copy(ja = null, zh = null)

        assertEquals("Gangnam station", noTranslation.resolve(DomainLocale.JA))
        assertEquals("Gangnam station", noTranslation.resolve(DomainLocale.ZH))
    }

    @Test
    fun `활성 언어와 EN이 모두 없으면 KO로 폴백한다`() {
        // AC R31. ko·en은 적재 시 NOT NULL이라 실제로는 KO가 항상 남는다.
        val koOnly = full.copy(en = "", ja = null, zh = null)

        assertEquals("강남역", koOnly.resolve(DomainLocale.JA))
        assertEquals("강남역", koOnly.resolve(DomainLocale.EN))
    }

    @Test
    fun `공백뿐인 이름은 부재로 본다`() {
        // 빈 문자열도 폴백한다.
        val blankJa = full.copy(ja = "   ")

        assertEquals("Gangnam station", blankJa.resolve(DomainLocale.JA))
    }

    @Test
    fun `KO 요청인데 KO가 비면 EN을 쓴다`() {
        // 폴백 순서가 "활성 언어 → EN → KO"라 KO 요청도 EN을 거친다.
        val blankKo = full.copy(ko = "")

        assertEquals("Gangnam station", blankKo.resolve(DomainLocale.KO))
    }
}
