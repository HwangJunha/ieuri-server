package org.around.ieuri.community.infrastructure.mapper

import org.around.ieuri.common.error.EmptyCategorySetException
import org.around.ieuri.community.adapter.graphql.mapper.toTargetCategories
import org.around.ieuri.community.domain.shared.TargetCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.around.ieuri.common.graphql.generated.types.TargetCategory as GqlTargetCategory

/** 커뮤니티 카테고리 인자 변환(SPEC-0020). 관광지 쪽과 같은 규칙을 지킨다. */
class CategoryArgumentMapperTest {

    @Test
    fun `생성 타입이 도메인 값으로 옮겨진다`() {
        val categories = toTargetCategories(
            listOf(GqlTargetCategory.RESTAURANT, GqlTargetCategory.LODGING),
        )

        assertEquals(setOf(TargetCategory.RESTAURANT, TargetCategory.LODGING), categories)
    }

    @Test
    fun `값이 하나여도 집합이 만들어진다`() {
        assertEquals(setOf(TargetCategory.SHOPPING), toTargetCategories(listOf(GqlTargetCategory.SHOPPING)))
    }

    @Test
    fun `생략하면 필터가 없다`() {
        assertTrue(toTargetCategories(null).isEmpty())
    }

    @Test
    fun `중복은 한 번만 남는다`() {
        val categories = toTargetCategories(
            listOf(GqlTargetCategory.RESTAURANT, GqlTargetCategory.RESTAURANT),
        )

        assertEquals(setOf(TargetCategory.RESTAURANT), categories)
    }

    /** 빈 배열은 관광지 쪽과 같은 코드로 거절된다. */
    @Test
    fun `빈 배열은 EMPTY_CATEGORY_SET으로 거절된다`() {
        val thrown = assertFailsWith<EmptyCategorySetException> { toTargetCategories(emptyList()) }

        assertEquals("EMPTY_CATEGORY_SET", thrown.code)
    }
}
