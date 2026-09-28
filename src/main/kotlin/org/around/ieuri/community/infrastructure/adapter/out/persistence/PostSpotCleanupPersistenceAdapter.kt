package org.around.ieuri.community.infrastructure.adapter.out.persistence

import org.around.ieuri.community.application.port.out.persistence.PostSpotCleanupPort
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.PostSpotJpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** 사라진 카탈로그를 가리키던 글-장소 연결 삭제(SPEC-0023 R11). */
@Component
class PostSpotCleanupPersistenceAdapter(
    private val postSpotRepository: PostSpotJpaRepository,
) : PostSpotCleanupPort {

    @Transactional
    override fun deleteByCatalogIds(catalogIds: List<Long>): Long = postSpotRepository.deleteByCatalogIdIn(catalogIds)
}
