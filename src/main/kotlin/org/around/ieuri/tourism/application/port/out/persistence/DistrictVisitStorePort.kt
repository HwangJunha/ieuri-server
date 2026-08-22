package org.around.ieuri.tourism.application.port.out.persistence

/**
 * 시군구 방문 기록의 쓰기 포트(SPEC-0030 R3~R5).
 *
 * **시군구 코드 외에는 아무것도 저장하지 않는다** — 좌표도, 시각도, 판정 출처도. 시각이 붙는
 * 순간 위치 이력이 되어 POL-045의 취지에 반한다. 포트 시그니처에 시각 인자가 없는 것이
 * 그 제약을 타입으로 강제한다.
 */
interface DistrictVisitStorePort {

    /** 방문 기록을 지운다. 기록되지 않은 코드가 섞여 있어도 오류가 아니다(R4). */
    fun remove(memberId: Long, districtCodes: Collection<String>)

    /**
     * 회원의 방문 기록을 전부 지운다(탈퇴, §9.1).
     *
     * @return 지운 건수. 로그에 남기는 용도이며 0이어도 오류가 아니다.
     */
    fun removeAll(memberId: Long): Int
}

/**
 * 방문 기록 등록 포트(SPEC-0030 R3).
 *
 * **삭제와 포트를 나눈 이유는 구현 수단이 다르기 때문이다.** 등록의 멱등성은
 * `INSERT ... ON CONFLICT DO NOTHING`에서 나오는데 JPA에는 이식 가능한 upsert가 없어 생 SQL
 * 어댑터가 필요하다. 삭제는 그런 사정이 없어 JPA 파생 쿼리로 끝난다.
 *
 * 한 포트로 묶으면 어느 쪽이 생 SQL인지가 어댑터 안에 숨고, 어댑터가 어댑터를 부르게 된다.
 * 애플리케이션이 둘을 각각 주입해 순서를 직접 정한다.
 */
interface DistrictVisitUpsertPort {

    /**
     * 방문으로 기록한다. **이미 기록된 코드는 조용히 무시된다**(멱등, R3).
     *
     * 존재하지 않는 시군구 코드는 저장되지 않으며, 걸러내는 일은 호출자가 한다 —
     * 여기서 조용히 버리면 클라이언트가 DISTRICT_NOT_FOUND를 받을 근거가 사라진다.
     *
     * @return 이번 호출로 실제 추가된 코드. 이미 있던 코드는 빠진다.
     */
    fun record(memberId: Long, districtCodes: Collection<String>): Set<String>
}
