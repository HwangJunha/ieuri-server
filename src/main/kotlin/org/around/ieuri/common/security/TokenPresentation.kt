package org.around.ieuri.common.security

/**
 * 요청이 토큰을 어떻게 제시했는지의 판정 결과(SPEC-0032 R1).
 *
 * **[NONE]과 [REJECTED]를 나누는 것이 이 타입의 전부다.** 이전에는 인증 필터가 무효 토큰을
 * 만나면 SecurityContext를 비우고 통과시켜, 그 시점에 "토큰이 없었다"와 "토큰이 있었지만
 * 만료됐다"가 같은 상태가 됐다 — 뒤에 오는 어떤 코드도 둘을 구별할 수 없었다.
 *
 * 그래서 만료된 세션을 가진 사용자가 **비로그인으로 조용히 강등**됐다. 클라이언트는 오류를
 * 받지 못하니 토큰 갱신을 발동하지 않고, 화면은 로그인하지 않은 것처럼 보인다.
 */
enum class TokenPresentation {
    /** 토큰을 보내지 않았다. 로그인하지 않은 상태다. */
    NONE,

    /** 토큰이 유효해 신원이 확인됐다. */
    VALID,

    /**
     * 토큰을 보냈으나 서명·만료·타입이 어긋나 거절했다.
     *
     * 신원이 필요한 연산은 어차피 실패하므로 이 값이 중요한 곳은 **신원이 없어도 동작하는
     * 연산**이다. 그런 연산은 이 상태를 비로그인으로 다루지 말고
     * `UNAUTHENTICATED_TOKEN_REJECTED`로 거절해, 클라이언트가 갱신을 발동하게 한다.
     */
    REJECTED,
}

/** 요청 속성 키. 인증 필터가 쓰고 GraphQL 컨텍스트가 읽는다. */
const val TOKEN_PRESENTATION_ATTRIBUTE: String = "ieuri.tokenPresentation"
