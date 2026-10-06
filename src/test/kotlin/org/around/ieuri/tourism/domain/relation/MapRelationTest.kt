package org.around.ieuri.tourism.domain.relation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MapRelationTest {

    @Test
    fun `자기 자신과는 관계를 맺을 수 없다`() {
        assertFailsWith<CannotRelateSelfException> { MapRelation(ownerId = 10L, counterpartId = 10L) }
    }

    @Test
    fun `반대 방향은 주인과 상대를 맞바꾼다`() {
        val reversed = MapRelation(ownerId = 10L, counterpartId = 20L).reversed()

        assertEquals(20L, reversed.ownerId)
        assertEquals(10L, reversed.counterpartId)
    }
}
