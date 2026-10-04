package org.around.ieuri.community.application.port.out.persistence

import org.around.ieuri.community.domain.vo.ContentLanguage

/** 사라진 장소를 가리키는 글-장소 연결 정리(SPEC-0023 R11). 컨텍스트 간 FK가 없어 애플리케이션이 맡는다. */
interface PostSpotCleanupPort {
    /** 지운 연결 행 수. 한 장소를 여러 글이 참조하므로 단건이 아니다. */
    fun deleteByCatalogIds(catalogIds: List<Long>): Long
}

/** 카탈로그가 해소되는 언어의 커뮤니티 사본(SPEC-0029). 원본은 tourism에 있다. */
interface SpotCatalogLanguagePort {
    /**
     * 표 전체를 [entries]로 맞추고 반영 후 총 행 수를 돌려준다.
     * - 여기 없는 조합은 지운다 — 빈 목록이면 전부 지운다.
     */
    fun replaceAll(entries: List<CatalogLanguageEntry>): Int
}

/** 한 카탈로그가 한 언어에서 해소된다는 사실. 이벤트가 싣는 쌍과 같은 모양이다. */
data class CatalogLanguageEntry(
    val catalogId: Long,
    val language: ContentLanguage,
)
