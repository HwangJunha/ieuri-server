package org.around.ieuri.auth.infrastructure.security

import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Before
import org.springframework.stereotype.Component

/**
 * @RequiresPermission가 붙은 메서드 실행 전에 Authorizer로 권한을 강제한다.
 * 인가 로직을 리졸버 본문에서 분리해, 호출부는 애노테이션만 선언하면 되게 한다.
 *
 * 주의: DGS 데이터페처는 Spring 빈(@DgsComponent)을 통해 호출되므로 AOP 프록시로 가로채진다.
 * 이 전제는 RequiresPermissionAspectIntegrationTest가 DGS 실행 경로로 실증한다.
 */
@Aspect
@Component
class RequiresPermissionAspect(
    private val authorizer: Authorizer,
) {

    @Before("@annotation(requiresPermission)")
    fun enforce(requiresPermission: RequiresPermission) {
        authorizer.require(requiresPermission.permission)
    }
}
