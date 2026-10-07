package org.around.ieuri.tourism.domain.region

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** SPEC-0015 — 시도가 후보 집합이 되는 최소 시군구 수. 경계값을 포함한다. */
class ProvinceEligibilityTest {

    private val eligibility = ProvinceEligibility(minDistricts = 3)

    @Test
    fun `시군구 수가 최소와 같으면 후보가 된다`() {
        assertTrue(eligibility.isMetBy(3))
    }

    @Test
    fun `최소보다 하나 적으면 후보가 아니다`() {
        assertFalse(eligibility.isMetBy(2))
    }
}
