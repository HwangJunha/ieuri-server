package org.around.ieuri.tourism.application.port.`in`.command

import org.around.ieuri.tourism.domain.vo.DomainLocale

/**
 * 카탈로그 배정·번역 유스케이스(SPEC-0019). 배치가 호출한다.
 */
interface SpotCatalogUseCase {

    /**
     * 미배정 spot(`catalog_id`가 null인 행)을 카탈로그에 배정한다.
     *
     * **전량 재판정하지 않는다**(설계 문서 §3.3). 배정된 행은 다시 보지 않으므로 미배정
     * 조건 하나가 곧 증분 처리다 — 적재가 새 행을 넣으면 그 행만 자연히 대상이 된다.
     *
     * KO 행이 새로 생겨 기존 `DERIVED` 카탈로그와 같은 장소가 되더라도 **병합하지 않는다** —
     * 두 카탈로그가 공존하며 각자의 좋아요를 유지한다. 병합은 좋아요를 딸려 이동시키므로
     * 잘못되면 사용자 데이터가 엉킨다.
     */
    fun assignCatalogs(): CatalogAssignmentResult

    /**
     * 번역이 비어 있는 (카탈로그, 언어) 쌍을 채운다(R-4).
     *
     * **재번역 경로가 없다**(R-7). 기존 `content_id`의 제목이 상류에서 바뀌어도 번역은 그대로
     * 둔다. 이는 의도된 동작이며 결함이 아니다.
     */
    fun translateMissing(languages: Set<DomainLocale>, limit: Int): TranslationResult
}

/**
 * 배정 결과. 미해결 건수는 CSV 보고의 입력이자 규칙이 무너졌는지 보는 신호다 —
 * 실측 509건(0.81%)에서 크게 벗어나면 정규화나 상류 데이터가 바뀐 것이다.
 */
data class CatalogAssignmentResult(
    val assigned: Int,
    val catalogsCreated: Int,
    val unresolved: Int,
)

/** 번역 결과. [skipped]는 이미 제목이 있어 건너뛴 쌍이다. */
data class TranslationResult(
    val translated: Int,
    val skipped: Int,
)
