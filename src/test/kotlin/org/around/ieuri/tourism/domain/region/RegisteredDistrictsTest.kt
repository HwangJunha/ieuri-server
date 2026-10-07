package org.around.ieuri.tourism.domain.region

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 지표를 받을 수 있는 시군구 — 우리 표에 있는 코드만. */
class RegisteredDistrictsTest {

    private val registered = RegisteredDistricts(setOf("11110", "26110"))

    @Test
    fun `등록된 코드는 받는다`() {
        assertTrue(registered.accepts("26110"))
    }

    @Test
    fun `모르는 코드는 받지 않는다`() {
        assertFalse(registered.accepts("99999"))
    }

    @Test
    fun `등록된 시군구가 없으면 아무것도 받지 않는다`() {
        assertFalse(RegisteredDistricts(emptySet()).accepts("11110"))
    }
}
