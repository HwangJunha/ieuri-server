package org.around.ieuri.auth.infrastructure.adapter.out.persistence

import org.around.ieuri.auth.application.port.out.persistence.AuditLogRetentionPort
import org.around.ieuri.auth.application.port.out.persistence.LocationAccessLogPort
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.MemberLocationAccessLogEntity
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.repository.MemberAuditLogJpaRepository
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.repository.MemberLocationAccessLogJpaRepository
import org.springframework.data.domain.Limit
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.time.Instant

/**
 * 위치 조회 감사 기록 포트 구현(SPEC-0032 R19·R20).
 *
 * payload Map을 JSON 문자열로 직렬화해 jsonb 컬럼에 저장한다 — `AuditLogPersistenceAdapter`와
 * 같은 형태다. **좌표를 거르는 것은 이 어댑터의 책임이 아니다**(호출부가 애초에 담지 않는다).
 */
@Component
class LocationAccessLogPersistenceAdapter(
    private val repository: MemberLocationAccessLogJpaRepository,
    private val objectMapper: ObjectMapper,
) : LocationAccessLogPort {

    override fun record(memberId: Long, payload: Map<String, Any?>) {
        repository.save(
            MemberLocationAccessLogEntity(
                memberId = memberId,
                payload = objectMapper.writeValueAsString(payload),
            ),
        )
    }
}

/** 회원 감사 로그의 보관 정리(SPEC-0032 R22, POL-014). 규약은 위와 같다. */
@Component
class AuditLogRetentionPersistenceAdapter(
    private val repository: MemberAuditLogJpaRepository,
) : AuditLogRetentionPort {

    @Transactional
    override fun deleteCreatedBefore(cutoff: Instant, limit: Int): Int {
        val targets = repository.findByCreatedAtBefore(cutoff, Limit.of(limit))
        if (targets.isEmpty()) return 0
        return repository.deleteByIdIn(targets.mapNotNull { it.id }).toInt()
    }
}
