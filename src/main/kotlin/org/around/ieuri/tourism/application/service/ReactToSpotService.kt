package org.around.ieuri.tourism.application.service

import org.around.ieuri.tourism.application.command.ReactToSpotCommand
import org.around.ieuri.tourism.application.port.`in`.command.ReactToSpotUseCase
import org.around.ieuri.tourism.application.exception.SpotNotFoundException
import org.around.ieuri.tourism.application.exception.SpotNotFoundInLocaleException
import org.around.ieuri.tourism.application.port.out.persistence.SpotReactionPort
import org.around.ieuri.tourism.application.port.out.query.SpotQueryPort
import org.around.ieuri.tourism.domain.aggregate.SpotReaction
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 좋아요/싫어요 기록 유스케이스 구현(쓰기). **`(memberId, catalogId, language)`로 저장한다**
 * (SPEC-0029 R-13) — 활성 언어별로 따로 모인다. 재평가는 같은 키에 대한 upsert 덮어쓰기이며
 * 이력을 남기지 않는다.
 *
 * **클라이언트가 카탈로그 id를 보내고 언어는 서버가 Accept-Language에서 정한다** — 목록·상세가
 * 이미 카탈로그 축으로 id를 내려주므로 서버가 다시 해석할 것이 없다. 없는 카탈로그는
 * `SPOT_NOT_FOUND`이고, **활성 언어에 원본이 없는 카탈로그는 `SPOT_NOT_FOUND_IN_LOCALE`이다**
 * (AC-13.4) — 그 언어에서 볼 수 없는 장소에 좋아요를 남길 수는 없다.
 *
 * 인증(UNAUTHENTICATED)은 리졸버가 SecurityContext로 확립해 memberId를 넘긴다. 형식 검증은
 * 커맨드(init)가 담당하므로 여기서 중복 검증하지 않는다.
 */
@Service
class ReactToSpotService(
    private val spotReactionPort: SpotReactionPort,
    private val spotQueryPort: SpotQueryPort,
) : ReactToSpotUseCase {

    @Transactional
    override fun react(command: ReactToSpotCommand, memberId: Long, language: DomainLocale) {
        val catalogId = command.catalogId
        // 존재를 먼저 본다 — FK 위반으로 터뜨리면 클라이언트가 받는 것이 계약에 없는 오류가 된다.
        // **활성 언어에서 해소되는지까지 본다**(R-13) — 카탈로그가 있어도 그 언어에 원본이
        // 없으면 목록·상세 어디에도 나오지 않는 장소이므로 반응 대상이 아니다.
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
