package org.around.ieuri.auth.infrastructure.config

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.EnableAspectJAutoProxy

/**
 * AspectJ 자동프록시 활성화. @RequiresPermission 인가 애스펙트(RequiresPermissionAspect)가 동작하려면
 * 빈이 프록시로 감싸져야 한다. Spring Boot 4는 자동구성을 모듈로 분리했으므로, AOP 자동프록시가
 * 켜져 있으리라 가정하지 않고 여기서 명시적으로 활성화한다(전이 의존 spring-aop/aspectjweaver 사용).
 */
@Configuration
@EnableAspectJAutoProxy
class AopConfig
