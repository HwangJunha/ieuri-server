package org.around.ieuri.community.application.port.`in`.command

import org.around.ieuri.community.domain.vo.ContentLanguage

/**
 * 커뮤니티가 들고 있는 **장소 참조의 정리·동기화**(SPEC-0023 R11, SPEC-0029 R-15).
 *
 * **tourism이 이 포트를 직접 호출하지 않는다** — tourism의 이벤트를 구독한 인바운드 어댑터가
 * 부른다. `MemberDataEraseUseCase`와 같은 구조다.
 *
 * 두 연산이 한 포트에 있는 이유는 다루는 대상이 같기 때문이다 — 카탈로그가 사라졌을 때와
 * 카탈로그가 보이는 언어가 바뀌었을 때, 둘 다 커뮤니티가 가진 장소 관련 사본을 맞춘다.
 */
interface PostSpotCleanupUseCase {
    /**
     * 그 카탈로그를 가리키던 연결을 끊는다. **글은 지우지 않는다** — 연결 테이블만 지운다.
     * 지울 것이 없어도 오류가 아니다.
     */
    fun cleanupDeletedCatalogs(catalogIds: List<Long>)

    /**
     * 카탈로그가 해소되는 언어의 사본을 **통째로 맞춘다**(R-15). 목록이 활성 언어로 거를 때
     * 보는 표다.
     *
     * **[resolved]는 델타가 아니라 정본이다** — 지금 해소되는 (카탈로그, 언어) 전부이며,
     * 여기 없는 조합은 지운다. 변경분만 받으면 사본을 채울 수 없다: 전량 재적재 직후에는
     * 모든 행이 신규라 뒤집힌 것이 없어 변경분이 비고, 그러면 이 표가 빈 채로 남아 목록이
     * spot 붙은 글·질문을 **전부** 조용히 걸러 낸다.
     *
     * 같은 값이 두 번 와도 결과가 같다(멱등).
     */
    fun syncCatalogLanguages(resolved: List<CatalogLanguage>)
}

/**
 * 한 카탈로그가 한 언어에서 해소된다는 사실.
 *
 * **커뮤니티가 소유한 타입이다** — 리스너가 tourism의 이벤트 타입을 이 경계 안으로 들여보내지
 * 않고 여기로 옮겨 담는다. 언어도 문자열 코드가 아니라 [ContentLanguage]다 — 변환은 어댑터가
 * 마치고 들어온다.
 */
data class CatalogLanguage(
    val catalogId: Long,
    val language: ContentLanguage,
)
