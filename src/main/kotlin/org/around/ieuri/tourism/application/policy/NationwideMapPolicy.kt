package org.around.ieuri.tourism.application.policy

/**
 * 전국 지도 조회 정책값(설정에서 주입, POL-044).
 *
 * @param maxCellSpotPageSize 칸 상세 spot 목록의 first 상한(= 50). 초과는 클램프다(POL-018).
 *   기존 커넥션 관례(`likedSpots`, `ongoingFestivals`)와 맞췄으나 **다른 계약이라 노브가 따로다**.
 * @param shareUrlBase 공유 링크의 앞부분. 토큰을 뒤에 붙여 완성한다.
 *   **설정으로 두는 이유는 이 값이 바뀔 예정이기 때문이다** — 1단계는 커스텀 스킴
 *   (`ieuri://shared`)이라 앱 설치자만 열 수 있고, 웹 페이지가 생기면
 *   (`https://ieuri.app/shared`)로 바꿔 미설치자도 열게 한다. 그때 앱 배포를 기다리지
 *   않으려고 서버가 조립한다.
 */
data class NationwideMapPolicy(
    val maxCellSpotPageSize: Int,
    val shareUrlBase: String,
)
