package org.around.ieuri.community.application.port.`in`.command

/**
 * 사라진 장소를 가리키는 글-장소 연결 정리(SPEC-0023 R11).
 *
 * **tourism이 이 포트를 직접 호출하지 않는다** — `SpotCatalogDeleted` 이벤트를 구독한 인바운드
 * 어댑터가 부른다. `MemberDataEraseUseCase`와 같은 구조다.
 */
interface PostSpotCleanupUseCase {
    /**
     * 그 카탈로그를 가리키던 연결을 끊는다. **글은 지우지 않는다** — 연결 테이블만 지운다.
     * 지울 것이 없어도 오류가 아니다.
     */
    fun cleanupDeletedCatalogs(catalogIds: List<Long>)
}
