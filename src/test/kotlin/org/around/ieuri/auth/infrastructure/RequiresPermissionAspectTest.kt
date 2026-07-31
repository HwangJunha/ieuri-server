package org.around.ieuri.auth.infrastructure

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.auth.domain.vo.Permission
import org.around.ieuri.auth.infrastructure.security.Authorizer
import org.around.ieuri.common.security.ForbiddenException
import org.around.ieuri.auth.infrastructure.security.RequiresPermission
import org.around.ieuri.auth.infrastructure.security.RequiresPermissionAspect
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * @RequiresPermission 애스펙트가 애노테이션이 붙은 메서드 실행 "전에" Authorizer.require를 호출하고,
 * 거부되면 실제 실행을 막는지 검증한다. Spring AOP 프록시(AspectJProxyFactory)로 대상 빈을 직접 감싸므로
 * ApplicationContext/DB가 필요 없다(순수 단위 테스트).
 *
 * 운영에서는 kotlin-spring all-open이 @DgsComponent(리졸버)를 open으로 만들어 동일하게 프록시된다.
 */
class RequiresPermissionAspectTest {

    private val authorizer = mockk<Authorizer>(relaxed = true)

    /** 프록시 대상. CGLIB가 가로채도록 open. guarded는 보호되고, unguarded는 애노테이션이 없다. */
    open class Target {
        @RequiresPermission(Permission.MEMBER_READ)
        open fun guarded(): String = "executed"

        open fun unguarded(): String = "executed"
    }

    private fun proxied(): Target {
        val factory = AspectJProxyFactory(Target())
        factory.addAspect(RequiresPermissionAspect(authorizer))
        return factory.getProxy()
    }

    @Test
    fun `애노테이션이 붙은 메서드는 실행 전 해당 권한을 요구한다`() {
        val result = proxied().guarded()

        assertEquals("executed", result)
        verify(exactly = 1) { authorizer.require(Permission.MEMBER_READ) }
    }

    @Test
    fun `애노테이션이 없는 메서드는 권한을 요구하지 않는다`() {
        proxied().unguarded()

        verify(exactly = 0) { authorizer.require(any()) }
    }

    @Test
    fun `권한이 없으면(Authorizer 거부) 메서드 실행이 막힌다`() {
        every { authorizer.require(Permission.MEMBER_READ) } throws ForbiddenException("권한이 필요합니다: ${Permission.MEMBER_READ}")

        assertFailsWith<ForbiddenException> { proxied().guarded() }
    }
}
