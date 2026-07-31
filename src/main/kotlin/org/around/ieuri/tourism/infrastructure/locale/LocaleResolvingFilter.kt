package org.around.ieuri.tourism.infrastructure.locale

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Accept-Language 헤더를 파싱해 요청 스코프 [LocaleContext]에 언어를 채운다(RFC 9110 언어 협상).
 * 헤더의 최상위 우선 언어를 지원 언어(EN/KO/JA/ZH)로 정규화하며, 부재·파싱불가 시 EN 폴백(AC-11).
 *
 * 세밀한 q-값 협상 대신 첫 항목만 본다(클라이언트가 세션당 단일 언어를 설정하는 사용 패턴에 충분).
 */
@Component
class LocaleResolvingFilter(
    // 요청 스코프 빈은 필터(싱글턴)에 직접 주입할 수 없어 ObjectProvider로 요청 시점에 조회한다.
    private val localeContextProvider: ObjectProvider<LocaleContext>,
) : OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val header = request.getHeader("Accept-Language")
        val locale = parse(header)
        localeContextProvider.getObject().locale = locale
        filterChain.doFilter(request, response)
    }

    /** "ko-KR,ko;q=0.9,en;q=0.8" → 첫 언어의 primary subtag를 도메인 언어로. */
    private fun parse(header: String?): DomainLocale {
        val firstTag = header
            ?.split(',')
            ?.firstOrNull()
            ?.substringBefore(';')
            ?.trim()
        return DomainLocale.fromLanguageTag(firstTag)
    }
}
