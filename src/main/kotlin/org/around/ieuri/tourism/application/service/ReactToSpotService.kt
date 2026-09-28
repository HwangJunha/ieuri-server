package org.around.ieuri.tourism.application.service

import org.around.ieuri.tourism.application.command.ReactToSpotCommand
import org.around.ieuri.tourism.application.exception.SpotNotFoundException
import org.around.ieuri.tourism.application.exception.SpotNotFoundInLocaleException
import org.around.ieuri.tourism.application.port.`in`.command.ReactToSpotUseCase
import org.around.ieuri.tourism.application.port.out.persistence.SpotReactionPort
import org.around.ieuri.tourism.application.port.out.query.SpotQueryPort
import org.around.ieuri.tourism.domain.aggregate.SpotReaction
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 좋아요/싫어요 기록 유스케이스 구현. `(memberId, catalogId, language)`로 저장하며(SPEC-0029 R-13), 재평가는 이력 없이 덮어쓴다.
 * - 없는 카탈로그는 SPOT_NOT_FOUND, 활성 언어에 원본이 없는 카탈로그는 SPOT_NOT_FOUND_IN_LOCALE이다.
 */
@Service
class ReactToSpotService(
    private val spotReactionPort: SpotReactionPort,
    private val spotQueryPort: SpotQueryPort,
) : ReactToSpotUseCase {

    @Transactional
    override fun react(command: ReactToSpotCommand, memberId: Long, language: DomainLocale) {
        val catalogId = command.catalogId
        // 활성 언어에서 해소되는지 먼저 본다 — FK 위반으로 터뜨리지 않는다.
        if (spotQueryPort.findSpotBasics(catalogId, language) == null) {
            throw if (spotReactionPort.existsCatalog(catalogId)) {
                SpotNotFoundInLocaleException()
            } else {
                SpotNotFoundException()
            }
        }

        val existing = spotReactionPort.find(memberId, catalogId, language)
        if (existing != null) {
            existing.reevaluate(command.reaction)
            spotReactionPort.save(existing)
            return
        }
        spotReactionPort.save(
            SpotReaction.record(
                memberId = memberId,
                catalogId = catalogId,
                language = language,
                reaction = command.reaction,
            ),
        )
    }
}
