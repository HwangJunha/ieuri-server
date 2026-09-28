package org.around.ieuri.community.application.port.`in`.command

import org.around.ieuri.community.domain.vo.ContentLanguage

/** 커뮤니티가 들고 있는 장소 참조의 정리·동기화(SPEC-0023, SPEC-0029). tourism 이벤트 리스너가 부른다. */
interface PostSpotCleanupUseCase {
    /** 그 카탈로그를 가리키던 연결만 끊고 글은 지우지 않는다. 지울 것이 없어도 오류가 아니다. */
    fun cleanupDeletedCatalogs(catalogIds: List<Long>)

    /**
     * 카탈로그가 해소되는 언어의 사본을 통째로 맞춘다(멱등).
     * - [resolved]는 델타가 아니라 정본이다 — 여기 없는 조합은 지운다.
     * - 이 표가 비면 목록이 장소 붙은 글·질문을 전부 걸러 낸다.
     */
    fun syncCatalogLanguages(resolved: List<CatalogLanguage>)
}

/** 한 카탈로그가 한 언어에서 해소된다는 사실. 리스너가 tourism 이벤트에서 옮겨 담는다. */
data class CatalogLanguage(
    val catalogId: Long,
    val language: ContentLanguage,
)
