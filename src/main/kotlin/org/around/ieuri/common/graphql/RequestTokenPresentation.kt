package org.around.ieuri.common.graphql

import com.netflix.graphql.dgs.context.DgsContext
import com.netflix.graphql.dgs.internal.DgsWebMvcRequestData
import graphql.schema.DataFetchingEnvironment
import org.around.ieuri.common.security.TOKEN_PRESENTATION_ATTRIBUTE
import org.around.ieuri.common.security.TokenPresentation
import org.springframework.web.context.request.RequestAttributes

/**
 * 인증 필터가 남긴 토큰 판정을 실행 컨텍스트에서 읽는다(SPEC-0032 R1). 판정의 의미는 CurrentMemberProvider가 정한다.
 * - 구독(WebSocket)에는 서블릿 요청이 없어 [TokenPresentation.NONE]이다.
 */
fun DataFetchingEnvironment.tokenPresentation(): TokenPresentation {
    val webRequest = (DgsContext.getRequestData(this) as? DgsWebMvcRequestData)?.webRequest
        ?: return TokenPresentation.NONE
    return webRequest.getAttribute(TOKEN_PRESENTATION_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST)
        as? TokenPresentation
        ?: TokenPresentation.NONE
}
