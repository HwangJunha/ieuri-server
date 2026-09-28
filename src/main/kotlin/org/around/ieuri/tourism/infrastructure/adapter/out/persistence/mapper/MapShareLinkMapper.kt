package org.around.ieuri.tourism.infrastructure.adapter.out.persistence.mapper

import org.around.ieuri.tourism.application.port.out.persistence.MapShareLinkRecord
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.MapShareLinkEntity

/**
 * 공유 링크 엔티티 → 애플리케이션 레코드(POL-033).
 *
 * `revokedAt`은 넘기지 않는다 — 조회 경로가 유효한 링크만 다루므로 항상 null이고, 넘기면
 * 호출자가 "폐기됐는지"를 다시 판단할 수 있다는 오해를 준다.
 */
fun MapShareLinkEntity.toRecord(): MapShareLinkRecord = MapShareLinkRecord(
    memberId = memberId,
    token = token,
    createdAt = requireNonNullCreatedAt(),
)

/** `@PrePersist`가 채우므로 조회된 행에서는 null일 수 없다. 타입이 그것을 증명하지 못할 뿐이다. */
private fun MapShareLinkEntity.requireNonNullCreatedAt() = createdAt ?: error("저장된 공유 링크에는 createdAt이 있어야 한다.")
