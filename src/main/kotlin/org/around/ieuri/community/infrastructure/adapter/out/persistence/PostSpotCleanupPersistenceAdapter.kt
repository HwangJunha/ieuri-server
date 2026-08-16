package org.around.ieuri.community.infrastructure.adapter.out.persistence

import org.around.ieuri.community.application.port.out.persistence.PostSpotCleanupPort
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.PostSpotJpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 사라진 카탈로그를 가리키던 글-장소 연결 삭제 어댑터(SPEC-0023 R11).
 *
 * 파생 삭제 쿼리는 영속성 컨텍스트를 우회한다. 이 트랜잭션에서 같은 엔티티를 다시 읽는 코드가
 * 없으므로 안전하다(`MemberDataErasePersistenceAdapter`와 같은 판단).
 */
@Component
class PostSpotCleanupPersistenceAdapter(
    private val postSpotRepository: PostSpotJpaRepository,
) : PostSpotCleanupPort {

    @Transactional
    override fun deleteByCatalogIds(catalogIds: List<Long>): Long =
        postSpotRepository.deleteByCatalogIdIn(catalogIds)
}
