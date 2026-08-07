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
 * 약관 동의 **현재 상태** 포트 구현(SPEC-0011 R9~R12).
 *
 * 이 테이블은 이력이 아니라 상태다 — `(member_id, terms_id)`당 한 행이며(V38 유일 제약)
 * 재동의·철회는 그 행의 갱신이다. **이력은 member_audit_log가 갖는다** — 회원 삭제 시
 * 이 테이블은 FK 자식이라 함께 지워지지만 감사 로그는 남기 때문이다.
 */
@Component
class MemberAgreementPersistenceAdapter(
    private val agreementRepository: MemberTermsAgreementJpaRepository,
) : MemberAgreementRepositoryPort {

    /**
     * 회원의 약관별 현재 상태. 유일 제약 덕분에 약관 하나에 한 행이라 고를 것이 없다.
     *
     * 코드가 아니라 (코드, 버전)이 키다 — 버전이 올라가면 이전 동의는 이월되지 않는다.
     * 새 버전이 시행되면 그 버전의 행이 없으므로 미동의로 떨어진다.
     */
    @Transactional(readOnly = true)
    override fun findCurrentAgreements(memberId: Long): Map<TermsKey, AgreementRecord> =
        agreementRepository.findByMemberId(memberId).associate { row ->
            TermsKey(row.termsCode, row.termsVersion) to
                AgreementRecord(agreed = row.agreed, agreedAt = requireNotNull(row.agreedAt))
        }

    /**
     * 동의/철회를 반영한다. 기존 행이 있으면 **관리 엔티티를 고쳐 dirty-checking에 맡기고**,
     * 없으면 새로 넣는다 — detached 엔티티를 save()하면 dirty-checking이 무의미해진다.
     *
     * 같은 약관의 이전 판단은 덮어쓴다. 그 이력은 member_audit_log에 이미 기록됐다.
     */
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
