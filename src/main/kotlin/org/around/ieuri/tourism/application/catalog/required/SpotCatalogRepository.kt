package org.around.ieuri.tourism.application.catalog.required

import org.around.ieuri.tourism.domain.catalog.SpotCatalog
import org.around.ieuri.tourism.domain.shared.DomainLocale

/** 카탈로그 저장소(SPEC-0019·SPEC-0029). 배정·분할·노출 갱신 배치가 쓴다. */
interface SpotCatalogRepository {

    /** 카탈로그를 저장하고 부여된 id를 돌려준다. 번역도 함께 쓴다. */
    fun save(catalog: SpotCatalog): Long

    /** spot 행을 카탈로그에 배정한다. 이미 배정된 행은 덮어쓰지 않는다 — 덮으면 좋아요가 다른 장소로 옮겨 간다. */
    fun assign(spotId: Long, catalogId: Long)

    /** spot 행 여럿을 다른 카탈로그로 옮긴다. 이미 배정된 행을 덮어쓴다(분할 전용). */
    fun reassign(spotIds: List<Long>, catalogId: Long)

    /**
     * 번역 한 건을 쓴다. 같은 `(catalogId, language)`가 있으면 제목·주소는 덮지 않는다(R-4).
     * - 좌표·분류는 카탈로그에서 복사하고, 감춰져 있었다면 되살린다.
     */
    fun upsertTranslation(catalogId: Long, language: DomainLocale, title: String, address: String?)

    /** 옮겨 간 언어의 번역 행을 지운다. 감춤이 아니라 삭제다. 빈 목록은 무동작이다. */
    fun deleteTranslations(catalogId: Long, languages: List<DomainLocale>)

    /** `spot_festival`에서 축제 기간을 확정한다. 축제 원본이 사라졌으면 기간도 지운다. */
    fun refreshFestivalPeriod(catalogId: Long)

    /**
     * 원본 생존 여부를 번역 행의 `visible`에 반영한다(행은 지우지 않고 감춘다).
     * - 변경분이 아니라 반영 후 노출 중인 것 전부를 돌려준다.
     */
    fun refreshTranslationVisibility(): VisibilityRefreshResult

    /**
     * 아직 시군구에 귀속되지 않은 카탈로그를 좌표로 붙이고 붙인 수를 돌려준다(SPEC-0030 R11). 이미 귀속된 행은 건드리지 않는다.
     */
    fun assignUnassignedDistricts(): Int

    /** 귀속되지 않은 채 남은 카탈로그 수. 바다·경계 밖 좌표는 영원히 남으므로 0이 목표가 아니다. */
    fun countDistrictUnassigned(): Int
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
