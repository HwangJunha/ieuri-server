package org.around.ieuri.tourism.application.policy

/**
 * 전국 지도 조회 정책값(설정에서 주입, POL-044).
 *
 * @param maxCellSpotPageSize 칸 상세 spot 목록의 first 상한(= 50). 초과는 클램프다(POL-018).
 *   기존 커넥션 관례(`likedSpots`, `ongoingFestivals`)와 맞췄으나 **다른 계약이라 노브가 따로다**.
 * @param shareUrlBase 공유 링크의 앞부분. 토큰을 뒤에 붙여 완성한다
 *   (`https://www.ieuri.co.kr/tour/korea-map/<token>`).
 *   **커스텀 스킴을 쓰지 않는다** — 카카오톡 등 메신저가 `http`/`https`만 링크로 인식해
 *   `ieuri://`는 눌리지 않는다(실측). 앱은 이 경로를 App Link/Universal Link로 가로채고,
 *   웹 페이지가 생기면 같은 경로가 미설치자를 받는다. 조립을 서버가 하므로 도메인이나
 *   경로가 바뀌어도 앱 배포가 필요 없다.
 * @param maxRelationPageSize 관계 목록의 first 상한(= 50). 초과는 클램프다(POL-018).
 */
data class NationwideMapPolicy(
    val maxCellSpotPageSize: Int,
    val shareUrlBase: String,
    val maxRelationPageSize: Int,
)
