package org.around.ieuri.common.security

import graphql.schema.DataFetchingEnvironment

/**
 * 새 구독을 열 자격이 있는지 판정한다(SPEC-0032 R7, POL-048, 공유 커널).
 *
 * **연결이 살아 있다는 것이 그 자원을 볼 자격의 증명은 아니다.** WebSocket 신원은
 * `connection_init`에 고정되어 세션 속성에 담기는데 그 속성에는 만료 개념이 없어,
 * 로그아웃·정지·탈퇴 이후에도 연결 수명 내내 그 회원으로 스트리밍됐다.
 *
 * community가 auth의 토큰 저장소를 직접 알 수 없으므로 공유 커널에 인터페이스를 두고
 * auth가 구현한다 — [CurrentMemberProvider]와 같은 구조다.
 *
 * **기존 구독 스트림은 대상이 아니다.** 이미 열린 스트림을 끊으면 타이핑 표시가 주기적으로
 * 사라진다 — 판정은 새 `subscribe`에만 적용한다.
 */
interface SubscriptionAuthorizer {

    /**
     * 자격이 없으면 [SubscriptionUnauthenticatedException]을 던진다.
     *
     * HTTP 요청에는 연결 시각이 없어 **아무 것도 하지 않는다** — 그쪽은 요청마다 JWT 필터가
     * 신원을 새로 판정하므로 이 검사가 필요 없다.
     */
    fun requireLiveIdentity(dfe: DataFetchingEnvironment, memberId: Long)
}
