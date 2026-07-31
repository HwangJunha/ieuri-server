package org.around.ieuri.tourism.infrastructure.adapter.out.persistence.repository

import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.SpotReactionEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.SpotReactionId
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 좋아요/싫어요 Spring Data 레포지토리. 복합 PK(SpotReactionId)로 조회·저장한다.
 */
interface SpotReactionJpaRepository : JpaRepository<SpotReactionEntity, SpotReactionId>
