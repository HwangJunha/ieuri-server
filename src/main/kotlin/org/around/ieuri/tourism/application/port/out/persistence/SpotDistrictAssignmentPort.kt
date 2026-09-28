package org.around.ieuri.tourism.application.port.out.persistence

/** 카탈로그를 좌표가 속한 시군구에 귀속시키는 포트(SPEC-0030). DB 안에서 집합 연산으로 끝낸다. */
interface SpotDistrictAssignmentPort {

    /**
     * 아직 귀속되지 않은 카탈로그를 시군구에 붙인다. 이미 귀속된 행은 건드리지 않는다.
     * @return 이번 실행으로 귀속된 카탈로그 수.
     */
    fun assignUnassignedDistricts(): Int

    /** 귀속되지 않은 채 남은 카탈로그 수. 바다·경계 밖 좌표는 영원히 남으므로 0이 목표가 아니다. */
    fun countUnassigned(): Int
}
