package org.around.ieuri.tourism.infrastructure.locale

import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.springframework.context.annotation.Scope
import org.springframework.context.annotation.ScopedProxyMode
import org.springframework.stereotype.Component
import org.springframework.web.context.WebApplicationContext

/**
 * 요청 스코프 언어 컨텍스트. 서블릿 필터가 Accept-Language를 파싱해 채우고, 리졸버가 읽어 유스케이스에 넘긴다.
 * 언어를 GraphQL 인자/캐시 키에서 제외하려는 의도(spec §4.2). 폴백은 EN(AC-11).
 *
 * ScopedProxyMode.TARGET_CLASS: 싱글턴 리졸버에 요청 스코프 빈을 주입하기 위한 프록시.
 */
@Component
@Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
class LocaleContext {
    /** 필터가 설정하기 전 기본값은 폴백(EN). */
    var locale: DomainLocale = DomainLocale.FALLBACK
}
