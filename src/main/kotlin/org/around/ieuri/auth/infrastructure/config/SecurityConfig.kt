package org.around.ieuri.auth.infrastructure.config

import org.around.ieuri.auth.infrastructure.security.JwtAuthenticationFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

/**
 * 보안 설정. JWT accessToken 필터로 인증 주체를 확립한다(요청의 Bearer 토큰 → memberId).
 * 엔드포인트 인가는 GraphQL 단일 엔드포인트 특성상 리졸버에서 처리하므로 여기선 permitAll로 둔다:
 *   - 공개(회원가입/로그인/refresh)는 토큰 없이 허용
 *   - 인증 필요(탈퇴)는 리졸버가 memberId 유무로 거부, 관리자(정지/복구)는 AdminGuard가 거부
 * 세션은 STATELESS(JWT). CSRF는 GraphQL POST를 위해 비활성화.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    fun securityFilterChain(http: HttpSecurity, jwtAuthenticationFilter: JwtAuthenticationFilter): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
        return http.build()
    }
}
