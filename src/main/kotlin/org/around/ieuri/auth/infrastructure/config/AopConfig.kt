package org.around.ieuri.auth.infrastructure.config

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.EnableAspectJAutoProxy

/** AspectJ 자동프록시 활성화. @RequiresPermission 애스펙트가 동작하려면 필요하다(Boot 4 자동구성에 기대지 않는다). */
@Configuration
@EnableAspectJAutoProxy
class AopConfig
