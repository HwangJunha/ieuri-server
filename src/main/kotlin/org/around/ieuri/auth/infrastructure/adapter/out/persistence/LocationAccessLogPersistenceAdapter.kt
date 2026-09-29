package org.around.ieuri.auth.infrastructure.adapter.out.persistence

import org.around.ieuri.auth.application.port.out.persistence.AccessLogPort
import org.around.ieuri.auth.application.port.out.persistence.AccessLogRetentionPort
import org.around.ieuri.auth.application.port.out.persistence.AuditLogRetentionPort
import org.around.ieuri.auth.application.port.out.persistence.LocationAccessLogPort
import org.around.ieuri.auth.application.port.out.persistence.LocationAccessLogRetentionPort
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.MemberAccessLogEntity
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.MemberLocationAccessLogEntity
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.repository.MemberAccessLogJpaRepository
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.repository.MemberAuditLogJpaRepository
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.repository.MemberLocationAccessLogJpaRepository
import org.springframework.data.domain.Limit
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.time.Instant

/** 위치 조회 감사 기록 포트 구현(SPEC-0032 R19·R20). payload를 jsonb로 저장하며, 좌표 제외는 호출부 책임이다. */
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

/** 접속기록 포트 구현(안전성 확보조치 기준 제8조). 기준의 네 항목을 컬럼으로 남긴다. */
@Component
class AccessLogPersistenceAdapter(
    private val repository: MemberAccessLogJpaRepository,
) : AccessLogPort {

    override fun record(memberId: Long, operation: String, ipAddress: String?) {
        repository.save(
            MemberAccessLogEntity(
                memberId = memberId,
                operation = operation,
                ipAddress = ipAddress,
            ),
        )
    }
}

/** 접속기록의 보관 정리(기준 제8조 제2항의 1년). 규약은 아래와 같다. */
@Component
class AccessLogRetentionPersistenceAdapter(
    private val repository: MemberAccessLogJpaRepository,
) : AccessLogRetentionPort {

    @Transactional
    override fun deleteCreatedBefore(cutoff: Instant, limit: Int): Int {
        val targets = repository.findByCreatedAtBefore(cutoff, Limit.of(limit))
        if (targets.isEmpty()) return 0
        return repository.deleteByIdIn(targets.mapNotNull { it.id }).toInt()
    }
}

/** 위치 조회 감사 기록의 보관 정리(위치정보법 제16조 제2항의 6개월). */
@Component
class LocationAccessLogRetentionPersistenceAdapter(
    private val repository: MemberLocationAccessLogJpaRepository,
) : LocationAccessLogRetentionPort {

    @Transactional
    override fun deleteCreatedBefore(cutoff: Instant, limit: Int): Int {
        val targets = repository.findByCreatedAtBefore(cutoff, Limit.of(limit))
        if (targets.isEmpty()) return 0
        return repository.deleteByIdIn(targets.mapNotNull { it.id }).toInt()
    }
}
