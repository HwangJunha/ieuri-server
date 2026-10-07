package org.around.ieuri.tourism.domain.catalog

import org.around.ieuri.tourism.domain.shared.DomainLocale
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 번역 노출 규칙 — 그 언어의 원본이 붙어 있으면 노출한다. */
class TranslationVisibilityTest {

    @Test
    fun `그 언어의 원본이 있으면 노출한다`() {
        assertTrue(TranslationVisibility.isSatisfiedBy(DomainLocale.JA, originLanguages = setOf(DomainLocale.KO, DomainLocale.JA)))
    }

    @Test
    fun `다른 언어 원본만 있으면 감춘다`() {
        assertFalse(TranslationVisibility.isSatisfiedBy(DomainLocale.JA, originLanguages = setOf(DomainLocale.KO, DomainLocale.EN)))
    }

    @Test
    fun `원본이 하나도 없으면 감춘다`() {
        assertFalse(TranslationVisibility.isSatisfiedBy(DomainLocale.KO, originLanguages = emptySet()))
    }
}
