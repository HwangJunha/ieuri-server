package org.around.ieuri.tourism.infrastructure.adapter.out.persistence

import org.around.ieuri.tourism.application.port.out.persistence.MapShareLinkPort
import org.around.ieuri.tourism.application.port.out.persistence.MapShareLinkRecord
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.mapper.toRecord
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.repository.MapShareLinkJpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/** 공유 링크 조회·폐기 어댑터(SPEC-0030 R13·R14 · §9.1). 발급은 [MapShareLinkIssueJdbcAdapter]가 한다. */
@Component
class MapShareLinkStoreAdapter(
    private val repository: MapShareLinkJpaRepository,
) : MapShareLinkPort {

    @Transactional(readOnly = true)
    override fun findActiveByToken(token: String): MapShareLinkRecord? = repository.findByTokenAndRevokedAtIsNull(token)?.toRecord()

    /** 본인 것이 아니거나 이미 폐기됐으면 아무 일도 없다. 더티 체킹으로 폐기한다. */
    @Transactional
    override fun revoke(memberId: Long, token: String): Boolean {
        val link = repository.findByMemberIdAndTokenAndRevokedAtIsNull(memberId, token) ?: return false

        link.revokedAt = Instant.now()
        return true
    }

    @Transactional
    override fun deleteAll(memberId: Long): Int = repository.deleteByMemberId(memberId)
}
