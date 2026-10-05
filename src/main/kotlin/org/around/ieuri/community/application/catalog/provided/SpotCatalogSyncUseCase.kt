package org.around.ieuri.community.application.catalog.provided

import org.around.ieuri.community.domain.catalog.CatalogLanguage

/** 커뮤니티가 들고 있는 장소 참조의 정리·동기화. tourism 이벤트 리스너가 커밋 뒤에 부른다. */
interface SpotCatalogSyncUseCase {
    /** 그 카탈로그를 가리키던 글-장소 연결만 끊고 글은 지우지 않는다. 지울 것이 없어도 오류가 아니다. */
    fun cleanupDeletedCatalogs(catalogIds: List<Long>)

    /** 카탈로그-언어 사본을 정본 전체로 맞춘다(멱등). 여기 없는 조합은 지운다 — 빈 목록이면 전부 지운다. */
    fun syncCatalogLanguages(resolved: List<CatalogLanguage>)
}
