package org.around.ieuri.tourism.domain

import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** `Accept-Language` 파싱(SPEC-0005 AC-11). */
class DomainLocaleAcceptLanguageTest {

    /** q-값 협상 대신 첫 항목만 본다. */
    @Test
    fun `헤더의 첫 언어를 쓴다`() {
        assertEquals(DomainLocale.KO, DomainLocale.fromAcceptLanguage("ko-KR,ko;q=0.9,en;q=0.8"))
        assertEquals(DomainLocale.EN, DomainLocale.fromAcceptLanguage("en-US,ko;q=0.9"))
        assertEquals(DomainLocale.JA, DomainLocale.fromAcceptLanguage("ja"))
    }

    /** 중국어는 번체만 지원하므로 zh 계열이 ZH로 수렴한다. */
    @Test
    fun `중국어 계열은 ZH로 수렴한다`() {
        assertEquals(DomainLocale.ZH, DomainLocale.fromAcceptLanguage("zh-Hant"))
        assertEquals(DomainLocale.ZH, DomainLocale.fromAcceptLanguage("zh-TW;q=1.0"))
    }

    /** 헤더 부재·미지원 언어는 폴백(EN)이다 — 오류가 아니다. */
    @Test
    fun `헤더가 없거나 미지원이면 폴백이다`() {
        assertEquals(DomainLocale.FALLBACK, DomainLocale.fromAcceptLanguage(null))
        assertEquals(DomainLocale.FALLBACK, DomainLocale.fromAcceptLanguage(""))
        assertEquals(DomainLocale.FALLBACK, DomainLocale.fromAcceptLanguage("fr-FR,de;q=0.9"))
    }

    /** 단일 태그 정규화(AC-11). `fromAcceptLanguage`가 이 함수 위에 얹혀 있다. */
    @Test
    fun `Accept-Language 태그를 도메인 언어로 정규화하고 미지원은 EN 폴백`() {
        assertEquals(DomainLocale.KO, DomainLocale.fromLanguageTag("ko-KR"))
        assertEquals(DomainLocale.JA, DomainLocale.fromLanguageTag("ja"))
        assertEquals(DomainLocale.ZH, DomainLocale.fromLanguageTag("zh-Hant"))
        assertEquals(DomainLocale.EN, DomainLocale.fromLanguageTag("en-US"))
        assertEquals(DomainLocale.EN, DomainLocale.fromLanguageTag(null))
        assertEquals(DomainLocale.EN, DomainLocale.fromLanguageTag("fr"))
    }
}
