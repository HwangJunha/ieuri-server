package org.around.ieuri.tourism.application.catalog.required

import org.around.ieuri.tourism.application.catalog.required.dto.VisibilityRefreshResult
import org.around.ieuri.tourism.domain.catalog.CatalogFestival
import org.around.ieuri.tourism.domain.catalog.CatalogMembership
import org.around.ieuri.tourism.domain.catalog.CatalogTranslation
import org.around.ieuri.tourism.domain.catalog.OriginFestivalPeriod
import org.around.ieuri.tourism.domain.catalog.SpotCatalog
import org.around.ieuri.tourism.domain.catalog.SpotReaction
import org.around.ieuri.tourism.domain.shared.DomainLocale

/**
 * 카탈로그 애그리게이트 저장소. 루트는 카탈로그 행과 그 번역(언어 수만큼)을 함께 읽는다.
 * - 원본 소속(`spot.catalog_id`)은 이 애그리게이트의 일부다 — 루트가 만든 [CatalogMembership]으로 쓴다.
 * - 반응·축제 기간도 루트가 정한 것을 의도별 메서드로 쓴다.
 * - 노출·시군구 일괄 반영은 규칙이 SQL에만 있다 — 수만 행을 한 문장으로 바꿔야 하는데 도메인 명세를 SQL로 옮길 수단이 없다(D-125).
 */
interface SpotCatalogRepository {

    fun findById(catalogId: Long): SpotCatalog?

    /** 새 카탈로그와 그 번역을 넣고 부여된 id를 돌려준다. */
    fun insert(catalog: SpotCatalog): Long

    /** 번역 하나를 쓴다. 사본 컬럼(좌표·분류)은 [catalog]의 표준값에서 온다. */
    fun saveTranslation(catalog: SpotCatalog, translation: CatalogTranslation)

    /** 옮겨 간 언어의 번역 행을 지운다. 감춤이 아니라 삭제다. 빈 목록은 무동작이다. */
    fun deleteTranslations(catalogId: Long, languages: List<DomainLocale>)

    /** 원본 소속을 쓴다. 붙이기([CatalogMembership.Join])는 미배정 원본만, 옮기기([CatalogMembership.Move])는 기존 소속을 덮는다. */
    fun saveMembership(membership: CatalogMembership)

    /** 카탈로그에 붙은 원본들의 축제 기간. */
    fun findOriginFestivalPeriods(catalogId: Long): List<OriginFestivalPeriod>

    /** 카탈로그의 축제 기간을 쓴다. 기간이 null이면 지운다(축제 원본이 사라졌다). */
    fun saveFestival(festival: CatalogFestival)

    /** 반응을 쓴다. 같은 키(회원·카탈로그·언어)면 반응이 바뀔 때만 덮어쓴다. 첫 반응이 동시에 와도 실패하지 않는다. */
    fun saveReaction(reaction: SpotReaction)

    /** 탈퇴 회원의 반응을 지우고 지운 건수를 돌려준다. */
    fun deleteReactionsByMember(memberId: Long): Int

    /**
     * 그 언어의 원본이 있는가를 번역의 `visible`에 한꺼번에 반영한다(행은 지우지 않는다). 상태가 바뀌는 행만 갱신한다.
     * - 변경분이 아니라 반영 후 노출 중인 것 전부를 돌려준다.
     */
    fun refreshTranslationVisibility(): VisibilityRefreshResult

    /** 시군구에 귀속되지 않은 카탈로그를 좌표가 들어가는 시군구에 붙이고 붙인 수를 돌려준다. */
    fun assignUnassignedDistricts(): Int

    /** 귀속되지 않은 채 남은 카탈로그 수. */
    fun countDistrictUnassigned(): Int
}
