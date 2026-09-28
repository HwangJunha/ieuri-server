package org.around.ieuri.tourism.application.port.out.client

/*
 * 공유 지도가 다른 도메인에 묻는 두 가지(SPEC-0030 R13·R16).
 *
 * **tourism이 소유하는 포트다.** 상대 도메인의 `port/in`을 서비스가 직접 주입하면 애플리케이션
 * 계층이 다른 도메인을 알게 되어 추출이 막힌다 — 번역은 `out/client` 어댑터가 맡는다
 * (`SpotLookupAdapter`의 선례와 같은 자리).
 *
 * 그래서 여기 있는 타입은 전부 tourism의 것이다. 상대 도메인의 read DTO가 이 경계를 넘지 않는다.
 */

/** 지도 주인을 표시하기 위한 최소 정보. **이메일 등 다른 개인정보는 넘어오지 않는다**(R13). */
interface MapOwnerLookupPort {

    /** 없는 회원(탈퇴 등)이면 null이다 — 그 경우 링크는 열리지 않는다. */
    fun findNickname(memberId: Long): String?

    /**
     * 여러 회원의 닉네임을 **한 번에**. 관계 목록이 한 페이지에 20명을 그리므로 건별로
     * 물으면 왕복이 20번이 된다.
     *
     * 없는 회원은 결과에 키가 없다 — 호출자가 그 항목을 건너뛴다.
     */
    fun findNicknames(memberIds: Collection<Long>): Map<Long, String>
}

/** 차단 판정(R16). **양방향이라 누가 걸었는지는 묻지 않는다**(POL-041). */
interface MapViewerBlockPort {

    fun isBlockedBetween(memberId: Long, counterpartId: Long): Boolean
}
