package org.around.ieuri.common.graphql

import com.netflix.graphql.dgs.context.DgsContext
import com.netflix.graphql.dgs.internal.DgsWebMvcRequestData
import graphql.schema.DataFetchingEnvironment
import org.around.ieuri.common.security.StaleTokenException
import org.around.ieuri.common.security.TOKEN_PRESENTATION_ATTRIBUTE
import org.around.ieuri.common.security.TokenPresentation
import org.springframework.web.context.request.RequestAttributes

/**
 * 인증 필터가 남긴 토큰 판정을 **실행 컨텍스트에서** 읽는다(SPEC-0032 R1, 공유 커널).
 *
 * `RequestContextHolder`를 쓰지 않는 이유는 [acceptLanguage]와 같다 — 가상 스레드가 켜져 있어
 * 데이터페처가 다른 스레드에서 돌면 ThreadLocal 기반 요청 스코프가 사라진다. DGS가 서블릿
 * 스레드에서 캡처해 둔 `WebRequest`를 통해 읽으므로 스레드가 바뀌어도 그대로 있다.
 *
 * **구독에는 서블릿 요청이 없다.** WebSocket은 이 경로를 타지 않으므로 [TokenPresentation.NONE]이
 * 되며, 구독의 신원 판정은 `GraphqlWebSocketAuthInterceptor`가 따로 맡는다.
 */
fun DataFetchingEnvironment.tokenPresentation(): TokenPresentation {
    val webRequest = (DgsContext.getRequestData(this) as? DgsWebMvcRequestData)?.webRequest
        ?: return TokenPresentation.NONE
    return webRequest.getAttribute(TOKEN_PRESENTATION_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST)
        as? TokenPresentation
        ?: TokenPresentation.NONE
}

/**
 * 신원 없이도 동작하는 연산이 **거절된 토큰을 비로그인으로 강등하지 않도록** 막는다(R1).
 *
 * 토큰을 보내지 않은 요청은 그대로 통과시키고(비로그인은 정상 상태다), 토큰을 보냈으나
 * 거절된 요청만 실패시킨다. 그래야 클라이언트의 갱신 링크가 발동한다 — 조용히 비로그인
 * 응답을 주면 클라이언트는 만료를 인지할 방법이 없다.
 *
 * 신원을 **요구하는** 연산은 이 함수가 필요 없다 — `requireMemberId()`가 어느 쪽이든 던진다.
 */
fun DataFetchingEnvironment.rejectStaleToken() {
    if (tokenPresentation() == TokenPresentation.REJECTED) {
        throw StaleTokenException("토큰이 만료되었거나 유효하지 않습니다. 갱신 후 다시 시도해 주세요.")
    }
}
