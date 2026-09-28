package org.around.ieuri.tourism.application.port.out.persistence

import org.around.ieuri.tourism.domain.aggregate.SpotCatalog
import org.around.ieuri.tourism.domain.vo.DomainLocale

/**
 * 카탈로그 쓰기 포트(SPEC-0019). 배정 배치가 쓴다.
 * - 생 SQL upsert는 [SpotCatalogUpsertPort]가 맡는다 — 서비스가 두 포트를 각각 주입받는다.
 */
interface SpotCatalogStorePort {

    /** 카탈로그를 저장하고 부여된 id를 돌려준다. 번역과 축제 기간도 함께 쓴다. */
    fun save(catalog: SpotCatalog): Long

    /** spot 행을 카탈로그에 배정한다. 이미 배정된 행은 덮어쓰지 않는다. */
    fun assign(spotId: Long, catalogId: Long)

    /**
     * 원본 생존 여부를 번역 행의 `visible`에 반영한다(행은 지우지 않고 감춘다).
     * - 변경분이 아니라 반영 후 노출 중인 것 전부를 돌려준다.
     */
    fun refreshTranslationVisibility(): VisibilityRefreshResult
}

/** (카탈로그, 언어) 쌍. 다른 도메인이 자기 사본을 맞추는 근거다. */
data class CatalogLanguage(
    val catalogId: Long,
    val language: DomainLocale,
)

/**
 * 노출 갱신의 결과. [visible]은 갱신 후 노출 중인 (카탈로그, 언어) 전부 — 구독자가 받는 정본이다.
 * - [hiddenCount]·[shownCount]는 이번 회차에 뒤집힌 행 수로, 로그·관측용이다.
 */
data class VisibilityRefreshResult(
    val hiddenCount: Int,
    val shownCount: Int,
    val visible: List<CatalogLanguage>,
)
