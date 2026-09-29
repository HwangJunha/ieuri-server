package org.around.ieuri.tourism.infrastructure.adapter.out.persistence.mapper

import org.around.ieuri.tourism.application.port.out.persistence.MapShareLinkRecord
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.MapShareLinkEntity

/** 공유 링크 엔티티 → 애플리케이션 레코드(POL-033). 조회 경로는 유효한 링크만 다뤄 `revokedAt`을 넘기지 않는다. */
fun MapShareLinkEntity.toRecord(): MapShareLinkRecord = MapShareLinkRecord(
    memberId = memberId,
    token = token,
    createdAt = requireNonNullCreatedAt(),
)

/** `@PrePersist`가 채우므로 조회된 행에서는 null일 수 없다. */
private fun MapShareLinkEntity.requireNonNullCreatedAt() = createdAt ?: error("저장된 공유 링크에는 createdAt이 있어야 한다.")
