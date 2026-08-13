package org.around.ieuri.tourism.infrastructure.adapter.out.persistence.mapper

import org.around.ieuri.tourism.domain.aggregate.SpotReaction
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.SpotReactionEntity

/** 반응 엔티티를 도메인 [SpotReaction]으로 옮긴다. */
fun SpotReactionEntity.toDomain(): SpotReaction =
    SpotReaction.record(
        memberId = memberId,
        catalogId = catalogId,
        spotId = spotId,
        reaction = reaction,
    )

/** 도메인 [SpotReaction]을 새 엔티티로 옮긴다. id는 DB가 채운다. */
fun SpotReaction.toNewEntity(): SpotReactionEntity =
    SpotReactionEntity(
        memberId = memberId,
        catalogId = catalogId,
        spotId = spotId,
        reaction = reaction,
    )
