package org.around.ieuri.tourism.domain.event

/**
 * 카탈로그가 어떤 언어에서 해소되는지가 갱신되었다(SPEC-0029 R-15·R-23).
 *
 * 다른 도메인이 장소를 참조할 때 **활성 언어에서 해소되는지**로 노출을 가르는데, 그 판정에
 * 필요한 값은 tourism의 `spot_catalog_translation`에 있다. 참조하는 쪽이 매번 조인해 오면
 * 도메인 경계를 뚫게 되고, 조인 없이 걸러내려면 후보 id 목록을 통째로 넘겨야 해서 그 목록의
 * 상한이 곧 목록의 한계가 된다. 그래서 **바뀐 시점에 알려 각자 자기 사본을 맞추게 한다.**
 *
 * **[resolved]는 델타가 아니라 정본이다 — 지금 노출 중인 (카탈로그, 언어) 전부다.**
 * 변경분만 실으면 사본을 채울 수 없다: 전량 초기화 후 재적재하면 모든 행이 신규 INSERT라
 * 노출 상태가 뒤집힌 적이 없고, 그러면 이벤트가 영원히 비어 구독자의 표가 빈 채로 남는다.
 * 그 상태에서 목록 필터는 spot이 붙은 글·질문을 **전부** 조용히 걸러 낸다 — 오류도 로그도
 * 없이. 구독자는 이 값으로 자기 표를 **통째로 맞춘다**(델타 적용이 아니다).
 *
 * 갱신 지점은 배정 배치의 노출 갱신 한 곳뿐이라, 구독자는 이 이벤트만 따라가면 어긋나지 않는다.
 *
 * 한 번에 수만 건이 실릴 수 있다. 배치 경로에서만 발행되므로 요청 지연에 영향이 없다.
 *
 * **`application/event`가 아니라 여기 있는 이유는 구독자가 다른 도메인이기 때문이다** —
 * 도메인 간 구독은 `domain/event`의 타입으로만 허용된다(LayeredArchitectureTest).
 * [SpotCatalogDeleted]와 같은 자리다.
 */
data class SpotCatalogLanguagesChanged(
    val resolved: List<CatalogLanguageRef>,
)

/**
 * 이벤트가 싣는 (카탈로그, 언어) 한 쌍.
 *
 * 언어를 **문자열 코드로 싣는다** — 구독자가 tourism의 `DomainLocale`을 import하면 그 도메인만
 * 따로 떼어낼 수 없게 된다. 이벤트는 도메인 간 계약이라 그 제약이 특히 강하게 걸린다.
 */
data class CatalogLanguageRef(
    val catalogId: Long,
    val languageCode: String,
)
