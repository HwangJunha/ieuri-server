package org.around.ieuri.tourism.infrastructure.adapter.out.client

import org.around.ieuri.tourism.application.port.out.client.SpotTitleTranslationPort
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.slf4j.LoggerFactory

/**
 * 번역 엔진이 없을 때 쓰는 기본 구현(SPEC-0019).
 *
 * **초기 34,622건은 planner 시드가 적재하므로 이 어댑터 없이도 목표 데이터는 채워진다**(§5.4).
 * 이후 신규분 번역에 어떤 엔진을 쓸지는 아직 정해지지 않았다 — 이 프로젝트에 번역 클라이언트의
 * 선례가 없고 Spring 표준 idiom도 없어, 임의로 고르는 대신 결정을 기다린다
 * (CLAUDE.md "인프라: 관례를 따르고 직접 만들지 않는다").
 *
 * 빈 결과를 돌려주므로 **그 쌍은 다음 실행이 다시 대상으로 삼는다** — 원문을 그대로 넣거나
 * 빈 문자열을 쓰면 "번역됨"으로 기록되어 실제 엔진이 붙었을 때 영영 채워지지 않는다.
 * 그것이 여기서 아무것도 쓰지 않는 이유다.
 *
 * 빈 등록은 [org.around.ieuri.tourism.infrastructure.config.TourismConfig]가 하며,
 * 실제 어댑터가 등록되면 이 구현은 물러난다(`@ConditionalOnMissingBean`).
 */
class NoOpSpotTitleTranslationAdapter : SpotTitleTranslationPort {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun translate(koreanTitle: String, targets: Set<DomainLocale>): Map<DomainLocale, String> {
        log.debug("translation engine not configured: skipping {} target(s)", targets.size)
        return emptyMap()
    }
}
