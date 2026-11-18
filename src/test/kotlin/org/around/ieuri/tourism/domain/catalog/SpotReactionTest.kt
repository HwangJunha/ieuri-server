package org.around.ieuri.tourism.domain.catalog

import org.around.ieuri.tourism.domain.shared.DomainLocale
import kotlin.test.Test
import kotlin.test.assertFailsWith

class SpotReactionTest {

    @Test
    fun `회원·카탈로그 id는 양수여야 한다`() {
        assertFailsWith<IllegalArgumentException> { SpotReaction(0L, 2L, DomainLocale.KO, ReactionType.LIKE) }
        assertFailsWith<IllegalArgumentException> { SpotReaction(1L, 0L, DomainLocale.KO, ReactionType.LIKE) }
    }
}
