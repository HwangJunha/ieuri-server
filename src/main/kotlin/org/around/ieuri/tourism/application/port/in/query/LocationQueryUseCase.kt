package org.around.ieuri.tourism.application.port.`in`.query

import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.tourism.application.query.LocationView
import org.around.ieuri.tourism.application.query.LocationViewQuery
import org.around.ieuri.tourism.application.query.SpotView
import org.around.ieuri.tourism.domain.vo.DomainLocale

/**
 * 좌표 맥락 `Location`과 그 거리순 보기(SPEC-0035 R1~R3). 추천 보기는 [RecommendedSpotsUseCase]다 —
 * 조회 경로가 전혀 달라(점수·재배열·캐시) 한 서비스에 두면 검증 한 줄 때문에 이백 줄이 딸려 온다.
 */
interface LocationQueryUseCase {
    /**
     * 좌표를 검증해 맥락을 만든다. **DB를 보지 않는다**(AC-1.1). 범위 밖은 INVALID_COORDINATES.
     * [memberId]가 있으면 위치 조회 사실을 감사 이벤트로 남긴다(SPEC-0032 R19) — 좌표는 남기지 않는다.
     */
    fun location(latitude: Double, longitude: Double, memberId: Long?): LocationView

    /**
     * 거리 오름차순(동거리는 id 오름차순) 커서 페이지. `Query.spots`와 같은 후보·같은 검증이며
     * 페이지만 (거리, id) 키셋이다. [memberId]가 있으면 이미 반응한 장소를 제외한다.
     * 손상된 커서는 INVALID_CURSOR.
     */
    fun spots(query: LocationViewQuery, locale: DomainLocale, memberId: Long?): CursorPage<SpotView>
}
