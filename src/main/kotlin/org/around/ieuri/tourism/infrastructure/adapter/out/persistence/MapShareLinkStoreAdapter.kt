package org.around.ieuri.tourism.infrastructure.adapter.out.persistence

import org.around.ieuri.tourism.application.port.out.persistence.MapShareLinkPort
import org.around.ieuri.tourism.application.port.out.persistence.MapShareLinkRecord
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.mapper.toRecord
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.repository.MapShareLinkJpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 공유 링크 조회·폐기 어댑터(SPEC-0030 R13·R14 · §9.1).
 *
 * 발급은 부분 UNIQUE 인덱스에 걸리는 `ON CONFLICT`가 필요해 [MapShareLinkIssueJdbcAdapter]에
 * 있다 — 포트도 그렇게 갈려 있다. 나머지는 파생 쿼리로 끝난다.
 */
@Component
class MapShareLinkStoreAdapter(
    private val repository: MapShareLinkJpaRepository,
) : MapShareLinkPort {

    @Transactional(readOnly = true)
    override fun findActiveByToken(token: String): MapShareLinkRecord? =
        repository.findByTokenAndRevokedAtIsNull(token)?.toRecord()

    /**
     * 본인 것이 아니거나 이미 폐기됐으면 아무 일도 없다.
     *
     * 조회 후 필드를 바꿔 더티 체킹에 맡긴다 — 벌크 UPDATE로 쓰면 영속성 컨텍스트를 우회해
     * 같은 트랜잭션의 다른 읽기와 어긋날 수 있다.
     */
    @Transactional
    override fun revoke(memberId: Long, token: String): Boolean {
        val link = repository.findByMemberIdAndTokenAndRevokedAtIsNull(memberId, token) ?: return false

        link.revokedAt = Instant.now()
        return true
    }

    @Transactional
    override fun deleteAll(memberId: Long): Int = repository.deleteByMemberId(memberId)
}
