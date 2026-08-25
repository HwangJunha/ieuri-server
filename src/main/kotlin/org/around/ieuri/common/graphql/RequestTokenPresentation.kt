package org.around.ieuri.common.graphql

import com.netflix.graphql.dgs.context.DgsContext
import com.netflix.graphql.dgs.internal.DgsWebMvcRequestData
import graphql.schema.DataFetchingEnvironment
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
 * **여기서는 값만 꺼낸다.** 그 값으로 무엇을 할지(거절할지 비로그인으로 볼지)는 보안 계층의
 * 판단이라 `CurrentMemberProvider`가 맡는다 — 이 패키지는 `acceptLanguage`·`appKey`처럼
 * 요청에서 값을 읽는 자리이고, 인증 판정이 섞이면 다음 사람이 여기에 인증 로직을 더 얹는다.
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
