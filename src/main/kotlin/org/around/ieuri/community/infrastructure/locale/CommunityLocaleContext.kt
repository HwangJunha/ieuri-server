package org.around.ieuri.community.infrastructure.locale

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.around.ieuri.community.domain.vo.ContentLanguage
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Scope
import org.springframework.context.annotation.ScopedProxyMode
import org.springframework.stereotype.Component
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.filter.OncePerRequestFilter

/**
 * 요청 스코프 언어 컨텍스트. 상단 칩 구성이 언어마다 달라 요청 언어를 알아야 한다.
 *
 * 언어는 GraphQL 인자가 아니라 Accept-Language에서만 온다 — 클라이언트가 고르는 값이 아니라
 * 요청의 속성이기 때문이다.
 */
@Component
@Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
class CommunityLocaleContext {
    /** 필터가 설정하기 전 기본값은 폴백(EN). */
    var language: ContentLanguage = ContentLanguage.FALLBACK
}

/**
 * Accept-Language를 파싱해 [CommunityLocaleContext]를 채운다.
 * 세밀한 q-값 협상 대신 최상위 우선 언어 하나만 본다.
 */
@Component
class CommunityLocaleResolvingFilter(
    // 요청 스코프 빈은 싱글턴 필터에 직접 주입할 수 없어 ObjectProvider로 요청 시점에 조회한다.
    private val localeContextProvider: ObjectProvider<CommunityLocaleContext>,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val firstTag = request.getHeader("Accept-Language")
            ?.split(',')
            ?.firstOrNull()
            ?.substringBefore(';')
            ?.trim()
        localeContextProvider.getObject().language = ContentLanguage.fromLanguageTag(firstTag)
        filterChain.doFilter(request, response)
    }
}
