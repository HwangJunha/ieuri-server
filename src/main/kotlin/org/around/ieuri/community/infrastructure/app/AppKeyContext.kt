package org.around.ieuri.community.infrastructure.app

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.around.ieuri.community.domain.vo.AppKey
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Scope
import org.springframework.context.annotation.ScopedProxyMode
import org.springframework.stereotype.Component
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.filter.OncePerRequestFilter

/**
 * 요청 스코프 앱 식별 컨텍스트. 상단 칩 구성이 앱마다 달라 어느 앱이 부르는지 알아야 한다.
 *
 * GraphQL 인자가 아니라 헤더에서 온다 — 클라이언트가 고르는 값이 아니라 요청의 속성이고,
 * 인자로 두면 쿼리마다 넘겨야 한다(Accept-Language와 같은 이유).
 */
@Component
@Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
class AppKeyContext {
    /** 필터가 설정하기 전 기본값. */
    var appKey: AppKey = AppKey.DEFAULT
}

/** `X-App-Key` 헤더를 읽어 [AppKeyContext]를 채운다. 헤더가 없으면 기본 앱으로 둔다. */
@Component
class AppKeyResolvingFilter(
    // 요청 스코프 빈은 싱글턴 필터에 직접 주입할 수 없어 ObjectProvider로 요청 시점에 조회한다.
    private val appKeyContextProvider: ObjectProvider<AppKeyContext>,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        appKeyContextProvider.getObject().appKey =
            AppKey.fromHeaderOrDefault(request.getHeader(HEADER))
        filterChain.doFilter(request, response)
    }

    private companion object {
        const val HEADER = "X-App-Key"
    }
}
