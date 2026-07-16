package org.around.ieuri.auth.infrastructure.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain

/**
 * 개발용 보안 설정. GraphQL 엔드포인트/introspection을 열어 둔다.
 * (토큰/세션 기반 인증은 다음 스텝에서 도입) CSRF는 GraphQL POST를 위해 비활성화.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .authorizeHttpRequests { it.anyRequest().permitAll() }
        return http.build()
    }
}
