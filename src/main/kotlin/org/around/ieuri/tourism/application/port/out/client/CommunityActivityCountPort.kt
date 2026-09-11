package org.around.ieuri.tourism.application.port.out.client

/**
 * 장소에 붙은 커뮤니티 활동 수(SPEC-0035 R9). 커뮤니티 도메인의 인바운드 포트를 통해 읽는다 —
 * tourism → community 읽기 전용이며 tourism의 application은 community라는 이름을 모른다.
 *
 * 결과에 키가 없는 카탈로그는 "활동 요인 없음"이다. 어댑터는 요청한 id 전부에 값을 채운다 —
 * 글이 없는 장소는 0이지 모르는 장소가 아니다.
 */
interface CommunityActivityCountPort {
    fun countByCatalogIds(catalogIds: Collection<Long>): Map<Long, ActivityCounts>
}

/** 숨김·삭제를 뺀 수. */
data class ActivityCounts(
    val posts: Long,
    val questions: Long,
    val answers: Long,
)
