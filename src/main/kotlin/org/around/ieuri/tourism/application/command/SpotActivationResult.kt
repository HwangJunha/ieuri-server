package org.around.ieuri.tourism.application.command

/**
 * 비관광 spot 판정 1회의 결과(SPEC-0018 R10). 배치가 실행 이력과 로그에 남긴다.
 *
 * 쓰기 유스케이스의 반환값이라 읽기 모델(`application/query`)이 아니라 여기에 둔다.
 *
 * [deactivated]·[reactivated]는 **실제로 값이 바뀐 행 수**다 — 같은 결과의 재판정은 세지 않으므로
 * 안정 상태에서는 둘 다 0이다. 어느 쪽이든 갑자기 크게 늘면 키워드 설정이 잘못됐다는 신호다.
 */
data class SpotActivationResult(
    /** 판정한 전체 행 수. 매 실행 전량이라 적재 건수와 같은 규모다. */
    val judged: Int,
    val deactivated: Int,
    val reactivated: Int,
)
