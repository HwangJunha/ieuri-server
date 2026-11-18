package org.around.ieuri.common.security

/**
 * 요청이 토큰을 어떻게 제시했는지의 판정 결과(SPEC-0032 R1).
 * - [NONE]과 [REJECTED]를 구별해, 만료된 세션이 비로그인으로 조용히 강등되지 않게 한다.
 */
enum class TokenPresentation {
    /** 토큰을 보내지 않았다. 로그인하지 않은 상태다. */
    NONE,

    /** 토큰이 유효해 신원이 확인됐다. */
    VALID,

    /**
     * 토큰을 보냈으나 서명·만료·타입이 어긋나 거절했다.
     * 신원 없이도 동작하는 연산은 이 상태를 `UNAUTHENTICATED_TOKEN_REJECTED`로 거절해야 한다.
     */
    REJECTED,
}

/** 요청 속성 키. 인증 필터가 쓰고 GraphQL 컨텍스트가 읽는다. */
const val TOKEN_PRESENTATION_ATTRIBUTE: String = "ieuri.tokenPresentation"
