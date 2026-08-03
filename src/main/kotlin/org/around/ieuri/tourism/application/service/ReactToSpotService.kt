package org.around.ieuri.tourism.application.service

import org.around.ieuri.tourism.application.command.ReactToSpotCommand
import org.around.ieuri.tourism.application.port.`in`.command.ReactToSpotUseCase
import org.around.ieuri.tourism.application.port.out.persistence.SpotReactionPort
import org.around.ieuri.tourism.domain.aggregate.SpotReaction
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 좋아요/싫어요 기록 유스케이스 구현(쓰기). 요청 언어(locale)를 서버가 결합해 (memberId, contentId, language)로
 * 저장한다(AC-12). 재평가는 같은 키에 대한 upsert 덮어쓰기이며 이력을 남기지 않는다(AC-13).
 *
 * 인증(UNAUTHENTICATED)은 리졸버가 SecurityContext로 확립해 memberId를 넘긴다. resolve 판정(AC-14b,
 * SPOT_NOT_FOUND)은 상세 조회(detailCommon2)가 이번 범위 밖이라 upstream 재확인을 하지 않는다 — spotId 형식
 * 검증은 커맨드(init)가 담당하므로 여기서 중복 검증하지 않는다. 향후 place-resolution 레이어가 생기면 이 지점에서
 * 미해결 spotId를 SpotNotFoundException으로 거부한다(예외 타입은 그 확장 지점을 위해 유지).
 */
@Service
class ReactToSpotService(
    private val spotReactionPort: SpotReactionPort,
) : ReactToSpotUseCase {

    @Transactional
    override fun react(command: ReactToSpotCommand, memberId: Long) {
        val existing = spotReactionPort.find(memberId, command.spotId)
        if (existing != null) {
            existing.reevaluate(command.reaction)
            spotReactionPort.save(existing)
            return
        }
        val reaction = SpotReaction.record(
            memberId = memberId,
            spotId = command.spotId,
            reaction = command.reaction,
        )
        spotReactionPort.save(reaction)
    }
}
