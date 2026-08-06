package org.around.ieuri.auth.infrastructure.adapter.out.persistence

import org.around.ieuri.auth.application.port.out.persistence.AgreementRecord
import org.around.ieuri.auth.application.port.out.persistence.MemberAgreementRepositoryPort
import org.around.ieuri.auth.application.port.out.persistence.TermsKey
import org.around.ieuri.auth.domain.vo.Agreement
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.mapper.toNewEntity
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.repository.MemberTermsAgreementJpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 약관 동의 이력 포트 구현(SPEC-0011 R9~R12). **append-only** — 이 어댑터에 update/delete가
 * 없는 것이 그 계약이다. 철회도 `agreed = false` 행 추가로 표현된다.
 */
@Component
class MemberAgreementPersistenceAdapter(
    private val agreementRepository: MemberTermsAgreementJpaRepository,
) : MemberAgreementRepositoryPort {

    /**
     * 약관 버전별 **가장 최근** 판단만 남긴다. 오름차순으로 읽어 덮어쓰므로 마지막 값이 남는다.
     *
     * 코드가 아니라 (코드, 버전)이 키다 — 버전이 올라가면 이전 동의는 이월되지 않는다.
     */
    @Transactional(readOnly = true)
    override fun findLatestAgreements(memberId: Long): Map<TermsKey, AgreementRecord> {
        val rows = agreementRepository.findByMemberIdOrderByAgreedAtAscIdAsc(memberId)
        val latest = mutableMapOf<TermsKey, AgreementRecord>()
        for (row in rows) {
            latest[TermsKey(row.termsCode, row.termsVersion)] =
                AgreementRecord(agreed = row.agreed, agreedAt = requireNotNull(row.agreedAt))
        }
        return latest
    }

    @Transactional
    override fun append(memberId: Long, agreements: List<Agreement>) {
        if (agreements.isEmpty()) return
        agreementRepository.saveAll(agreements.map { it.toNewEntity(memberId) })
    }
}
