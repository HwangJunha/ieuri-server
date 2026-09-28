package org.around.ieuri.auth.infrastructure.adapter.out.persistence

import org.around.ieuri.auth.application.port.out.persistence.TermsRepositoryPort
import org.around.ieuri.auth.application.port.out.persistence.TermsView
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.repository.TermsJpaRepository
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * 활성 약관 조회 포트 구현. 유효(effective_at <= now)한 약관 중 코드별 최신 버전을 반환한다.
 */
@Component
class TermsPersistenceAdapter(
    private val termsRepository: TermsJpaRepository,
) : TermsRepositoryPort {

    override fun findActiveTerms(now: Instant): List<TermsView> = termsRepository.findByEffectiveAtLessThanEqual(now)
        .groupBy { it.code }
        .mapNotNull { (_, versions) -> versions.maxByOrNull { it.version } }
        .map { TermsView(id = requireNotNull(it.id), code = it.code, version = it.version, required = it.required) }
}
