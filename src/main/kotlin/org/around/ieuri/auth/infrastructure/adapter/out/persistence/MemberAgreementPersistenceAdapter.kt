package org.around.ieuri.auth.infrastructure.adapter.out.persistence

import org.around.ieuri.auth.application.port.out.persistence.AgreementRecord
import org.around.ieuri.auth.application.port.out.persistence.MemberAgreementRepositoryPort
import org.around.ieuri.auth.application.port.out.persistence.TermsKey
import org.around.ieuri.auth.domain.member.Agreement
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.mapper.toNewEntity
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.repository.MemberTermsAgreementJpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 약관 동의 현재 상태 포트 구현(SPEC-0011 R9~R12). `(member_id, terms_id)`당 한 행이며 재동의·철회는 갱신이다.
 * - 이력은 member_audit_log가 갖는다. 이 테이블은 회원 삭제 시 함께 지워진다.
 */
@Component
class MemberAgreementPersistenceAdapter(
    private val agreementRepository: MemberTermsAgreementJpaRepository,
) : MemberAgreementRepositoryPort {

    /** 회원의 (코드, 버전)별 현재 상태. 새 버전이 시행되면 그 버전의 행이 없어 미동의가 된다. */
    @Transactional(readOnly = true)
    override fun findCurrentAgreements(memberId: Long): Map<TermsKey, AgreementRecord> = agreementRepository.findByMemberId(memberId).associate { row ->
        TermsKey(row.termsCode, row.termsVersion) to
            AgreementRecord(agreed = row.agreed, agreedAt = requireNotNull(row.agreedAt))
    }

    /** 동의/철회를 반영한다. 기존 행은 관리 엔티티를 고쳐 dirty-checking에 맡기고, 없으면 새로 넣는다. */
    @Transactional
    override fun upsert(memberId: Long, agreements: List<Agreement>) {
        for (agreement in agreements) {
            val existing = agreementRepository.findByMemberIdAndTermsId(memberId, agreement.termsId)
            if (existing != null) {
                existing.agreed = agreement.agreed
                existing.agreedAt = agreement.agreedAt
                // 약관 버전이 올라가면 같은 terms_id를 쓰지 않으므로 코드·버전은 불변이다.
            } else {
                agreementRepository.save(agreement.toNewEntity(memberId))
            }
        }
    }
}
