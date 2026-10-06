package org.around.ieuri.tourism.application.reaction

import org.around.ieuri.common.persistence.ExposedConfig
import org.around.ieuri.tourism.application.reaction.provided.SpotReactionCommandUseCase
import org.around.ieuri.tourism.application.reaction.provided.dto.ReactToSpotCommand
import org.around.ieuri.tourism.application.reaction.provided.exception.SpotNotFoundException
import org.around.ieuri.tourism.application.reaction.provided.exception.SpotNotFoundInLocaleException
import org.around.ieuri.tourism.application.reaction.required.SpotReactionRepository
import org.around.ieuri.tourism.application.shared.required.SpotQueryPort
import org.around.ieuri.tourism.domain.reaction.SpotReaction
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * 좋아요/싫어요 기록. 재평가는 이력 없이 덮어쓴다.
 * - 없는 카탈로그는 SPOT_NOT_FOUND, 활성 언어에 원본이 없는 카탈로그는 SPOT_NOT_FOUND_IN_LOCALE이다.
 */
@Service
@Transactional(transactionManager = ExposedConfig.EXPOSED_TX)
class SpotReactionModifyService(
    private val reactionRepository: SpotReactionRepository,
    private val spotQueryPort: SpotQueryPort,
) : SpotReactionCommandUseCase {

    override fun react(command: ReactToSpotCommand, memberId: Long, language: DomainLocale) {
        val catalogId = command.catalogId
        // 활성 언어에서 해소되는지 먼저 본다 — FK 위반으로 터뜨리지 않는다.
        if (spotQueryPort.findSpotBasics(catalogId, language) == null) {
            throw if (spotQueryPort.existsCatalog(catalogId)) SpotNotFoundInLocaleException() else SpotNotFoundException()
        }

        // 재평가는 같은 키에 덮어쓴다 — 저장소가 키 upsert라 기존 기록을 먼저 읽지 않는다.
        reactionRepository.save(SpotReaction(memberId = memberId, catalogId = catalogId, language = language, reaction = command.reaction))
    }

    @Transactional(transactionManager = ExposedConfig.EXPOSED_TX, propagation = Propagation.REQUIRES_NEW)
    override fun eraseMemberContent(memberId: Long): Int = reactionRepository.deleteByMember(memberId)
}
