package org.around.ieuri.auth.infrastructure.adapter.out.persistence

import org.around.ieuri.auth.application.port.out.persistence.AuditLogPort
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.MemberAuditLogEntity
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.repository.MemberAuditLogJpaRepository
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/** 감사 이력 기록 포트 구현. payload Map을 JSON으로 직렬화해 jsonb 컬럼에 저장한다. */
@Component
class AuditLogPersistenceAdapter(
    private val repository: MemberAuditLogJpaRepository,
    private val objectMapper: ObjectMapper,
) : AuditLogPort {

    override fun record(memberId: Long, eventType: String, payload: Map<String, Any?>) {
        repository.save(
            MemberAuditLogEntity(
                memberId = memberId,
                eventType = eventType,
                payload = objectMapper.writeValueAsString(payload),
            ),
        )
    }
}
