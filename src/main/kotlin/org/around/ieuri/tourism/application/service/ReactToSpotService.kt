package org.around.ieuri.tourism.application.service

import org.around.ieuri.tourism.application.command.ReactToSpotCommand
import org.around.ieuri.tourism.application.port.`in`.command.ReactToSpotUseCase
import org.around.ieuri.tourism.application.exception.SpotNotFoundException
import org.around.ieuri.tourism.application.port.out.persistence.SpotReactionPort
import org.around.ieuri.tourism.domain.aggregate.SpotReaction
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 좋아요/싫어요 기록 유스케이스 구현(쓰기). **`(memberId, catalogId)`로 저장한다**(SPEC-0019 R-10)
 * — 같은 장소를 어느 언어에서 눌렀든 한 기록으로 모인다. 재평가는 같은 키에 대한 upsert
 * 덮어쓰기이며 이력을 남기지 않는다.
 *
 * **클라이언트는 계속 `spot.id`를 보낸다**(계약 불변) — 여기서 카탈로그로 해석한다.
 * 카탈로그가 없는 spot은 `SPOT_NOT_FOUND`다: 아직 배정되지 않았거나 사라진 행이며, 어느 쪽이든
 * 좋아요를 붙일 장소가 없다.
 *
 * 인증(UNAUTHENTICATED)은 리졸버가 SecurityContext로 확립해 memberId를 넘긴다. spotId 형식
 * 검증은 커맨드(init)가 담당하므로 여기서 중복 검증하지 않는다.
 */
@Service
class ReactToSpotService(
    private val spotReactionPort: SpotReactionPort,
) : ReactToSpotUseCase {

    @Transactional
    override fun react(command: ReactToSpotCommand, memberId: Long) {
        val catalogId = spotReactionPort.findCatalogIdBySpotId(command.spotId)
            ?: throw SpotNotFoundException()

        val existing = spotReactionPort.find(memberId, catalogId)
        if (existing != null) {
            existing.reevaluate(command.reaction)
            spotReactionPort.save(existing)
            return
        }
        val reaction = SpotReaction.record(
            memberId = memberId,
            catalogId = catalogId,
            // 어느 언어 화면에서 눌렀는가의 기록. 식별의 일부가 아니다.
            spotId = command.spotId,
            reaction = command.reaction,
        )
        spotReactionPort.save(reaction)
    }
}
