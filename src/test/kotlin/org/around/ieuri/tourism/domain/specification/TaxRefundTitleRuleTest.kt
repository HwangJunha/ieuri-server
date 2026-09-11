package org.around.ieuri.tourism.domain.specification

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** SPEC-0035 R11 — 제목 패턴 판정. */
class TaxRefundTitleRuleTest {

    private val rule = TaxRefundTitleRule(listOf("[Tax Refund Shop]", "[事後免税店]", "[事後免稅店]", "[면세점(TAX REFUND SHOP)]"))

    @Test
    fun `패턴이 제목에 들어 있으면 태그 장소다 - AC-11_1`() {
        assertTrue(rule.matches("[Tax Refund Shop] 나이키 가산점"))
        assertTrue(rule.matches("[事後免税店] ナイキ"))
        assertTrue(rule.matches("[事後免稅店] 耐吉"))
        assertTrue(rule.matches("[면세점(TAX REFUND SHOP)] 나이키"))
    }

    @Test
    fun `대소문자는 가리지 않는다`() {
        assertTrue(rule.matches("[TAX REFUND SHOP] 나이키"))
    }

    @Test
    fun `패턴이 없는 제목은 태그 장소가 아니다`() {
        assertFalse(rule.matches("경복궁"))
    }

    @Test
    fun `패턴 목록이 비면 항상 거짓이다 - AC-11_4`() {
        assertFalse(TaxRefundTitleRule(emptyList()).matches("[Tax Refund Shop] 나이키"))
        assertFalse(TaxRefundTitleRule(listOf("  ", "")).matches("[Tax Refund Shop] 나이키"))
    }
}
