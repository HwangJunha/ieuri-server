package org.around.ieuri.auth.adapter.security.authorization
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Before
import org.springframework.stereotype.Component

/**
 * @RequiresPermission 메서드 실행 전에 Authorizer로 권한을 강제한다.
 * - DGS 데이터페처는 @DgsComponent 빈을 거쳐 호출되므로 AOP 프록시로 가로채진다.
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
