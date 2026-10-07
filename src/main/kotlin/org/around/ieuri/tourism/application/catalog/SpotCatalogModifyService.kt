package org.around.ieuri.tourism.application.catalog

import org.around.ieuri.common.persistence.ExposedConfig
import org.around.ieuri.tourism.application.catalog.provided.SpotCatalogCommandUseCase
import org.around.ieuri.tourism.application.catalog.provided.dto.ReactToSpotCommand
import org.around.ieuri.tourism.application.catalog.provided.exception.SpotNotFoundException
import org.around.ieuri.tourism.application.catalog.required.SpotCatalogRepository
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/** 카탈로그의 회원 쓰기. 반응은 카탈로그가 만든다 — 그 언어에서 볼 수 있는 장소인지 루트가 판정한다. */
@Service
@Transactional(transactionManager = ExposedConfig.EXPOSED_TX)
class SpotCatalogModifyService(
    private val catalogRepository: SpotCatalogRepository,
) : SpotCatalogCommandUseCase {

    override fun react(command: ReactToSpotCommand, memberId: Long, language: DomainLocale) {
        val catalog = catalogRepository.findById(command.catalogId) ?: throw SpotNotFoundException()

        val reaction = catalog.react(memberId, language, command.reaction)

        catalogRepository.saveReaction(reaction)
    }

    @Transactional(transactionManager = ExposedConfig.EXPOSED_TX, propagation = Propagation.REQUIRES_NEW)
    override fun eraseMemberContent(memberId: Long): Int = catalogRepository.deleteReactionsByMember(memberId)
}
