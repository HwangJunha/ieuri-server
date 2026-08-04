package org.around.ieuri.tourism.infrastructure.mapper

import org.around.ieuri.tourism.domain.aggregate.SpotReaction
import org.around.ieuri.tourism.domain.vo.ReactionType
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.SpotReactionEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.mapper.toDomain
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.mapper.toNewEntity
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** SpotReactionMapper 필드별 매핑 검증 — 순수 단위테스트(Spring/DB 없음). */
class SpotReactionMapperTest {

    private fun entity(
        id: Long? = 31L,
        createdAt: Instant? = Instant.parse("2026-03-01T00:00:00Z"),
    ) = SpotReactionEntity(
        id = id,
        memberId = 41L,
        spotId = 52L,
        reaction = ReactionType.DISLIKE,
        createdAt = createdAt,
        updatedAt = Instant.parse("2026-03-02T00:00:00Z"),
    )

    @Test
    fun `모든 필드가 채워진 엔티티를 도메인으로 옮긴다`() {
        val domain = entity().toDomain()

        assertEquals(41L, domain.memberId)
        assertEquals(52L, domain.spotId)
        assertEquals(ReactionType.DISLIKE, domain.reaction)
    }

    @Test
    fun `nullable 필드가 모두 null이어도 예외 없이 옮긴다`() {
        // id·createdAt은 도메인 SpotReaction이 갖지 않으므로 null이어도 매핑에 영향이 없다.
        val domain = entity(id = null, createdAt = null).toDomain()

        assertEquals(41L, domain.memberId)
        assertEquals(52L, domain.spotId)
        assertEquals(ReactionType.DISLIKE, domain.reaction)
    }

    @Test
    fun `도메인 반응을 새 엔티티로 옮긴다`() {
        val entity = SpotReaction.record(
            memberId = 41L,
            spotId = 52L,
            reaction = ReactionType.LIKE,
        ).toNewEntity()

        assertNull(entity.id) // id는 DB가 채운다.
        assertEquals(41L, entity.memberId)
        assertEquals(52L, entity.spotId)
        assertEquals(ReactionType.LIKE, entity.reaction)
        assertNull(entity.createdAt)
    }

    @Test
    fun `재평가된 도메인 반응은 바뀐 값으로 옮겨진다`() {
        val reaction = SpotReaction.record(memberId = 41L, spotId = 52L, reaction = ReactionType.LIKE)
        reaction.reevaluate(ReactionType.DISLIKE)

        assertEquals(ReactionType.DISLIKE, reaction.toNewEntity().reaction)
    }
}
