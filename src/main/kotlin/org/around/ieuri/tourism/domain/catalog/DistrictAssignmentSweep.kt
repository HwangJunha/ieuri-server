package org.around.ieuri.tourism.domain.catalog

import org.around.ieuri.tourism.domain.catalog.spec.DistrictUnassigned

/**
 * [target]을 만족하는 카탈로그를 좌표를 품은 시군구에 귀속시키는 일괄 명령(Specification + Command, SPEC-0030 R11).
 * - "어느 카탈로그를"은 [target]이 정한다. "어느 시군구에"(경계가 좌표를 품는다)는 공간 판정이라 도메인이 계산하지 못하고 DB가 한다.
 * - 경계에 걸친 좌표는 맞닿은 시군구 중 하나다. 바다·경계 밖 좌표는 남아 다음 실행에 다시 시도된다.
 */
data class DistrictAssignmentSweep(val target: DistrictUnassigned = DistrictUnassigned)
