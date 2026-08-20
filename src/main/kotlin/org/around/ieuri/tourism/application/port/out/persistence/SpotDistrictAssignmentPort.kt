package org.around.ieuri.tourism.application.port.out.persistence

/**
 * 카탈로그를 시군구에 귀속시키는 포트(SPEC-0030 R11 · V75).
 *
 * 귀속은 전 카탈로그를 한 번에 훑는 집합 연산이라 개별 장소를 넘기는 형태가 아니다 —
 * 4만여 행을 애플리케이션으로 끌어오지 않고 DB 안에서 끝낸다
 * ([DistrictRepresentativeUpdatePort]와 같은 모양).
 *
 * **트리거로 두지 않은 이유가 이 포트의 존재 이유다.** 담고 있는 것이 "장소는 자기 좌표가
 * 속한 시군구에 귀속된다"는 도메인 규칙이라, DB에 넣으면 DB를 바꿀 때 규칙이 함께 사라진다.
 */
interface SpotDistrictAssignmentPort {

    /**
     * 아직 귀속되지 않은 카탈로그를 시군구에 붙인다.
     *
     * **이미 귀속된 행은 건드리지 않는다.** 좌표가 변하지 않으므로 다시 계산할 이유가 없고,
     * 전량을 매번 갱신하면 4만 행을 헛되이 쓴다.
     *
     * @return 이번 실행으로 귀속된 카탈로그 수.
     */
    fun assignUnassignedDistricts(): Int

    /**
     * 귀속되지 않은 채 남은 카탈로그 수.
     *
     * **0이 목표가 아니다.** 바다·경계 밖 좌표는 어느 시군구에도 속하지 않아 영원히 남으며
     * (실측 273건), 그 장소가 없는 것이 아니라 시군구에 매칭되지 않을 뿐이다. 이 수가 갑자기
     * 커지면 경계 데이터나 좌표 적재를 의심한다.
     */
    fun countUnassigned(): Int
}
