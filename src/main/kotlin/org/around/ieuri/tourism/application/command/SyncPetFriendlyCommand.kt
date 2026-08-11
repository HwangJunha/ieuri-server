package org.around.ieuri.tourism.application.command

/**
 * 반려동물 동반 가능 관광지의 적재 입력(SPEC-0015 · V48).
 *
 * **업스트림 필드명이 여기까지 오지 않는다** — `acmpyTypeCd` · `acmpyPsblCpam` 같은 표기는
 * 어댑터의 응답 DTO에서 끝난다(POL-033).
 *
 * [contentId]를 뺀 나머지가 전부 nullable인 것은 업스트림이 실제로 비워 보내기 때문이다.
 * **없는 값은 null로 넣는다 — 빈 문자열로 정규화하지 않는다**(POL-043).
 *
 * @param contentId TourAPI content_id. 국문 spot의 자연키이며 이 테이블의 PK다.
 * @param accompanyType 동반 가능 범위('전구역 동반가능' 등).
 * @param accompanyScope 동반 가능 반려동물 범위('전 견종 동반 가능' 등).
 * @param requiredMatters 준비물·주의사항.
 * @param etcInfo 기타 안내. 줄바꿈이 섞인 긴 문자열이다.
 */
data class SyncPetFriendlyCommand(
    val contentId: String,
    val accompanyType: String?,
    val accompanyScope: String?,
    val requiredMatters: String?,
    val etcInfo: String?,
)
