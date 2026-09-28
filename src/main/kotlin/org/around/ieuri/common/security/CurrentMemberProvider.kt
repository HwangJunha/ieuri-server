package org.around.ieuri.common.security

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

/**
 * 현재 인증된 회원(memberId)을 SecurityContext에서 조회한다(공유 커널). JWT 필터가 설정한 principal(Long)을 읽는다.
 * 여러 도메인의 리졸버가 재사용하므로 특정 도메인이 아닌 common에 둔다.
 */
@Component
class CurrentMemberProvider {

    fun currentMemberId(): Long? = SecurityContextHolder.getContext().authentication?.principal as? Long

    fun requireMemberId(): Long = currentMemberId() ?: throw UnauthorizedException("인증이 필요합니다.")

    /**
     * 신원이 **선택인** 연산의 회원 id(SPEC-0032 R1).
     *
     * 비로그인은 null로 그대로 흘리되, **토큰을 보냈는데 거절된 요청은 실패시킨다.**
     * 그러지 않으면 만료된 세션이 조용히 비로그인으로 강등되어, 방문 칸이나 이미 누른
     * 반응이 사라진 응답이 나간다 — 오류가 없으니 클라이언트는 갱신조차 하지 못한다.
     *
     * 신원을 **요구하는** 연산은 이 함수가 필요 없다 — [requireMemberId]가 어느 쪽이든 던진다.
     *
     * 판정 대상인 [presentation]은 인바운드 어댑터가 실행 컨텍스트에서 꺼내 넘긴다
     * (`DataFetchingEnvironment.tokenPresentation()`) — 그래야 이 계층이 DGS 타입을 알지 않는다.
     */
    fun optionalMemberId(presentation: TokenPresentation): Long? {
        if (presentation == TokenPresentation.REJECTED) {
            throw StaleTokenException("토큰이 만료되었거나 유효하지 않습니다. 갱신 후 다시 시도해 주세요.")
        }
        return currentMemberId()
    }
}
