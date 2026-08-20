package org.around.ieuri.tourism.domain.event

/**
 * 장소 카탈로그 삭제(SPEC-0023 R11). 다른 컨텍스트가 자신의 참조를 정리하도록 알린다.
 *
 * **오늘 이 이벤트를 발행하는 코드는 없다.** 카탈로그 생명주기는 삭제가 아니라
 * `refreshTranslationVisibility()`의 숨김으로 운영되며, 그것이 좋아요와 글 연결을 잃지 않기
 * 위한 의도적 설계다(SpotCatalogStorePort 주석). 삭제 경로가 생기는 시점에 이 이벤트를
 * 발행하면 구독자는 이미 준비돼 있다 — 그때 정리 로직을 처음부터 설계하지 않아도 된다.
 *
 * 한 번에 여러 카탈로그가 사라질 수 있으므로 목록으로 싣는다. 단건 가정은 두지 않는다.
 *
 * **`application/event`가 아니라 여기 있는 이유는 구독자가 다른 도메인이기 때문이다.**
 * 도메인 간 구독은 `domain/event`의 타입으로만 허용된다(LayeredArchitectureTest) —
 * `MemberWithdrawn`이 auth의 `domain/event`에 있는 것과 같다. tourism 내부에서만 소비되는
 * `SpotDetailFetched` 같은 이벤트는 계속 `application/event`에 남는다.
 */
data class SpotCatalogDeleted(
    val catalogIds: List<Long>,
)
