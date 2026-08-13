package org.around.ieuri.tourism.application.port.`in`.command

/**
 * 좋아요 dedupe 유스케이스(SPEC-0019 §4.2). 참조 축 이전 중 한 번만 쓰인다.
 *
 * 마이그레이션이 아니라 애플리케이션에 두는 이유는 남길 행을 고르는 규칙이 도메인 판단이고
 * (어느 반응이 현재 의사인가) 단위 테스트로 검증해야 하기 때문이다.
 */
interface ReactionDedupeUseCase {

    /**
     * 충돌하는 `(member_id, catalog_id)` 묶음에서 남길 행 하나만 남긴다.
     *
     * 한 회차 분량만 처리하므로 [ReactionDedupeResult.inspected]가 0이 될 때까지 호출한다.
     */
    fun dedupe(): ReactionDedupeResult
}

/** dedupe 결과. [inspected]가 0이면 충돌이 소진된 것이다. */
data class ReactionDedupeResult(
    val inspected: Int,
    val deleted: Int,
)
